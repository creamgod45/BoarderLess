package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspacePresenceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import cg.creamgod.boarderless.data.WorkspaceRemoteNotification
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspacePresencePublisher
import cg.creamgod.boarderless.data.WorkspacePresenceUpdate
import cg.creamgod.boarderless.domain.model.Vec2
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

internal enum class BackendRealtimeStatus { Idle, Preparing, Joining, CatchingUp, Live, Disconnected, ResyncRequired, Revoked, Failed }
@Serializable internal data class BackendRealtimeMetadata(val revision: Long, val title: String, val role: String) {
    init {
        require(revision in 0..RealtimeMaxSequence && role in setOf("owner", "editor", "commenter", "viewer"))
        require(title.length in 1..200 && title.isNotBlank())
        title.encodeToByteArray(throwOnInvalidSequence = true)
    }
}

/** Single-use connection owner. All content/metadata sinks must be scope-bound awaited
 * CAS+durability confirmations. Observing these flows does not open another socket.
 * REST writes may run separately, but CAS competition must fail this owner's checkpoint
 * write rather than overwrite a newer projection. Reconnect requires a new owner. */
internal class BackendRealtimeOwner(baseUrl: String, private val workspaceId: String,
    private val userId: String, private val clientId: String, private val subscriptionId: String,
    private val initial: WorkspaceStateDto, private val initialMetadata: BackendRealtimeMetadata,
    private val canvasCapabilities: BackendCanvasReadCapabilities,
    private val canContinue: () -> Boolean,
    private val persistProjection: suspend (WorkspaceStateDto) -> Unit,
    private val persistMetadata: suspend (BackendRealtimeMetadata) -> Unit,
    private val readMetadata: suspend () -> BackendRealtimeMetadata,
    private val readSnapshot: suspend () -> WorkspaceStateDto,
) {
    private val gateway = BackendRealtimeGateway(baseUrl)
    private val socket = BackendRealtimeSocket("${baseUrl.trimEnd('/')}/api/v1")
    private val epoch = randomUuid()
    private var started = false
    private var closed = false
    private var projection: BackendRealtimeProjection? = null
    private var presenceReceiver: BackendRealtimePresence? = null
    private var writer: BackendRealtimeWriter? = null
    private var realtimeCapabilities: BackendRealtimeCapabilities? = null
    private var publisherToken: String? = null
    private val presenceWrites = Mutex()
    private val presenceClock = TimeSource.Monotonic.markNow()
    private var wirePresenceSeq = 0L
    private var lastCursor: Vec2? = null
    private var lastSelection = emptySet<String>()
    private var lastCursorAt: Long? = null
    private var lastSelectionAt: Long? = null
    private val presencePausedUntil = MutableStateFlow(0L)
    private val submissionWrites = Mutex()
    private data class AwaitingAck(val request: SubmitOperationsRequest, val result: CompletableDeferred<RealtimeSubmissionResult>)
    private var awaitingAck: AwaitingAck? = null
    private var metadata = initialMetadata
    private val statusMutable = MutableStateFlow(BackendRealtimeStatus.Idle)
    val status: StateFlow<BackendRealtimeStatus> = statusMutable.asStateFlow()
    private val diagnosticMutable = MutableStateFlow<BackendRealtimeDiagnostic?>(null)
    val diagnostic: StateFlow<BackendRealtimeDiagnostic?> = diagnosticMutable.asStateFlow()
    private val presenceMutable = MutableStateFlow<WorkspacePresenceSnapshot?>(null)
    val presence: StateFlow<WorkspacePresenceSnapshot?> = presenceMutable.asStateFlow()
    private val projectionMutable = MutableStateFlow(initial)
    val confirmedProjection: StateFlow<WorkspaceStateDto> = projectionMutable.asStateFlow()
    private val metadataMutable = MutableStateFlow(initialMetadata)
    val confirmedMetadata: StateFlow<BackendRealtimeMetadata> = metadataMutable.asStateFlow()
    /** Wake authoritative refresh; never settle a write or replace optimistic content. */
    fun notifications(): Flow<WorkspaceRemoteNotification> = merge(
        confirmedProjection.drop(1).map { state -> WorkspaceRemoteNotification.Committed(WorkspaceId(workspaceId),
            state.throughServerSeq, state.throughServerSeq, state.workspaceVersion) },
        confirmedMetadata.drop(1).map { WorkspaceRemoteNotification.MetadataChanged(WorkspaceId(workspaceId)) },
        status.filter { it == BackendRealtimeStatus.Live }.map { WorkspaceRemoteNotification.Joined(WorkspaceId(workspaceId)) },
    )

    suspend fun run() {
        require(!started && !closed); started = true
        try {
            active(); statusMutable.value = BackendRealtimeStatus.Preparing
            val prepared = gateway.prepare(workspaceId, userId, clientId, epoch, initial, metadata.revision, ::allowed)
            active()
            realtimeCapabilities = prepared.capabilities
            val machine = BackendRealtimeProjection(workspaceId, userId, epoch, initial, canvasCapabilities,
                prepared.capabilities.maxMessageBytes, metadata.revision)
            projection = machine
            val receiver = BackendRealtimePresence(workspaceId, userId, clientId, subscriptionId, epoch,
                prepared.capabilities.presenceTtlMs, prepared.capabilities.maxSelectionIds) {
                allowed() && machine.phase == RealtimeProjectionPhase.Live
            }
            presenceReceiver = receiver
            statusMutable.value = BackendRealtimeStatus.Joining
            socket.connectPrepared(prepared, ::allowed, onWriter = { writer = it }) { bytes ->
                active()
                val frame = machine.decode(bytes)
                when (frame.text("type")) {
                    "join_accepted" -> {
                        // Join carries revision/role but not title. Read the complete metadata
                        // when the local durable record is behind, before catch-up ACK.
                        if (frame.sequence("metadataRevision") > metadata.revision || frame.text("role") != metadata.role)
                            refreshMetadata(machine, frame.sequence("metadataRevision"))
                        val response = machine.joined(frame)
                        active(); statusMutable.value = BackendRealtimeStatus.CatchingUp
                        response
                    }
                    "snapshot_required" -> {
                        machine.snapshotRequired(frame)
                        active(); val snapshot = readSnapshot(); active()
                        refreshMetadata(machine, frame.sequence("metadataRevision"))
                        machine.installSnapshot(snapshot, ::saveProjection).also { active(); projectionMutable.value = machine.state }
                    }
                    "catch_up_transaction", "committed_transaction" ->
                        machine.applyTransaction(frame, ::saveProjection).also { active(); projectionMutable.value = machine.state }
                    "live_ready" -> {
                        if (frame.sequence("metadataRevision") > metadata.revision)
                            refreshMetadata(machine, frame.sequence("metadataRevision"))
                        machine.liveReady(frame); active(); statusMutable.value = BackendRealtimeStatus.Live; null
                    }
                    "presence_snapshot", "presence_delta" -> {
                        require(prepared.capabilities.presence)
                        val received = receiver.accept(frame); active()
                        received?.let { presenceMutable.value = it }; null
                    }
                    "heartbeat_ack" -> {
                        // Physical socket validates ACK identity/order before invoking consume.
                        if (machine.phase == RealtimeProjectionPhase.Live) {
                            val renewed = receiver.renew(); active(); renewed?.let { presenceMutable.value = it }
                        }
                        null
                    }
                    "workspace_metadata" -> {
                        require(frame.keys == EnvelopeKeys + setOf("metadataRevision", "title", "selfRole"))
                        val updated = BackendRealtimeMetadata(frame.sequence("metadataRevision"), frame.text("title"), frame.text("selfRole"))
                        saveMetadata(machine, updated); null
                    }
                    "operation_ack" -> {
                        val awaiting = requireNotNull(awaitingAck) { "Unexpected operation ACK; original request remains unresolved" }
                        val result = decodeRealtimeAcknowledgement(frame, awaiting.request, userId)
                        when (result) {
                            is RealtimeSubmissionResult.Rejected -> diagnosticMutable.value = BackendRealtimeDiagnostic(statusMutable.value, result.code)
                            is RealtimeSubmissionResult.Conflict -> diagnosticMutable.value = BackendRealtimeDiagnostic(statusMutable.value, "submission_conflict")
                            is RealtimeSubmissionResult.Accepted -> Unit
                        }
                        require(!awaiting.result.isCompleted) { "Repeated ACK requires original receipt verification" }
                        awaiting.result.complete(result); null
                    }
                    "protocol_error" -> {
                        val failure = readRealtimeProtocolFailure(frame)
                        if (failure.code == "rate_limited" && machine.phase == RealtimeProjectionPhase.Live) {
                            val retry = requireNotNull(failure.retryAfterMs)
                            require(retry > 0)
                            val now = presenceClock.elapsedNow().inWholeMilliseconds
                            require(now in 0..Long.MAX_VALUE - retry)
                            presencePausedUntil.value = maxOf(presencePausedUntil.value, now + retry)
                            diagnosticMutable.value = BackendRealtimeDiagnostic(statusMutable.value, failure.code, retryAfterMs = retry)
                            null // Never replay the rejected cursor; only future intents can write.
                        } else throw failure
                    }
                    "access_revoked" -> {
                        require(frame.keys == EnvelopeKeys + "reason" && frame.text("reason") in setOf("membership_revoked", "workspace_deleted"))
                        statusMutable.value = BackendRealtimeStatus.Revoked; throw RealtimeStop()
                    }
                    "resync_required" -> {
                        require(frame.keys == EnvelopeKeys + setOf("reason", "headServerSeq", "workspaceVersion"))
                        require(frame.text("reason") in setOf("too_many_pending_transactions", "transaction_too_large", "slow_consumer", "sequence_gap"))
                        frame.sequence("headServerSeq"); frame.sequence("workspaceVersion")
                        statusMutable.value = BackendRealtimeStatus.ResyncRequired; throw RealtimeStop()
                    }
                    // This owner does not submit WS operations yet. Never settle an unknown
                    // operation ACK or silently ignore a protocol error as successful liveness.
                    else -> error("Unsupported realtime event")
                }
            }
            active(); statusMutable.value = BackendRealtimeStatus.Disconnected
        } catch (cancelled: CancellationException) {
            if (statusMutable.value !in setOf(BackendRealtimeStatus.Failed, BackendRealtimeStatus.Revoked, BackendRealtimeStatus.ResyncRequired))
                statusMutable.value = BackendRealtimeStatus.Disconnected
            throw cancelled
        } catch (_: RealtimeStop) {
            // Preserve explicit Revoked/Resync status; caller chooses the next action.
        } catch (failure: Exception) {
            if (!closed) {
                diagnosticMutable.value = when (failure) {
                    is BackendRealtimeHttpException -> BackendRealtimeDiagnostic(statusMutable.value, "http_rejected", failure.status)
                    is RealtimeProtocolFailure -> BackendRealtimeDiagnostic(statusMutable.value, failure.code, retryAfterMs = failure.retryAfterMs)
                    else -> BackendRealtimeDiagnostic(statusMutable.value, "transport_or_validation_failed")
                }
                statusMutable.value = BackendRealtimeStatus.Failed
            }
            throw failure
        } finally { release() }
    }
    private suspend fun saveProjection(next: WorkspaceStateDto) {
        active(); persistProjection(next); active()
    }
    private suspend fun refreshMetadata(machine: BackendRealtimeProjection, minimum: Long) {
        active(); val fresh = readMetadata(); active()
        require(fresh.revision >= minimum); saveMetadata(machine, fresh)
    }
    private suspend fun saveMetadata(machine: BackendRealtimeProjection, next: BackendRealtimeMetadata) {
        require(next.revision >= metadata.revision && (next.revision != metadata.revision || next == metadata))
        machine.confirmMetadata(next.revision) { active(); persistMetadata(next); active() }
        active(); metadata = next; metadataMutable.value = next
    }
    private fun allowed() = !closed && canContinue()
    private suspend fun active() { currentCoroutineContext().ensureActive(); check(allowed()) }
    fun close() {
        if (statusMutable.value !in setOf(BackendRealtimeStatus.Failed, BackendRealtimeStatus.Revoked, BackendRealtimeStatus.ResyncRequired))
            statusMutable.value = BackendRealtimeStatus.Disconnected
        release()
    }
    private fun release() {
        closed = true; publisherToken = null; writer = null
        awaitingAck?.result?.completeExceptionally(RealtimeSubmissionUnconfirmed())
        presenceMutable.value = null; presenceReceiver?.close(); projection?.close()
        socket.close(); gateway.close()
    }
    fun belongsTo(session: WorkspaceSession): Boolean = session.userId == userId && session.clientId == clientId &&
        session.workspace.id.value == workspaceId

    /** Repository must stage/confirm the exact pending request before invoking this. No
     * automatic REST fallback after dispatch, retry or pending/history mutation here. */
    suspend fun submit(request: SubmitOperationsRequest, stillOriginal: suspend () -> Boolean): RealtimeSubmissionResult = submissionWrites.withLock {
        active(); require(statusMutable.value == BackendRealtimeStatus.Live && metadata.role in setOf("owner", "editor"))
        require(request.protocolVersion == 1 && request.clientId == clientId && request.baseVersion in 0..RealtimeMaxSequence)
        requireRealtimeUuid(request.transactionId)
        val limits = requireNotNull(realtimeCapabilities)
        require(request.operations.size in 1..limits.maxOperationsPerTransaction && request.operations.map { it.operationId }.distinct().size == request.operations.size)
        request.operations.forEach { requireRealtimeUuid(it.operationId); require(it.clientSeq in 0..RealtimeMaxSequence) }
        require(awaitingAck == null && stillOriginal())
        active(); require(statusMutable.value == BackendRealtimeStatus.Live && metadata.role in setOf("owner", "editor"))
        val frozen = Json { encodeDefaults = true; explicitNulls = false }
            .encodeToJsonElement(SubmitOperationsRequest.serializer(), request) as JsonObject
        val message = JsonObject(frozen + mapOf("type" to JsonPrimitive("submit_operations"), "workspaceId" to JsonPrimitive(workspaceId),
            "connectionEpoch" to JsonPrimitive(epoch)))
        val waiting = AwaitingAck(request, CompletableDeferred()); awaitingAck = waiting
        try {
            requireNotNull(writer).send(message) {
                if (!allowed() || statusMutable.value != BackendRealtimeStatus.Live) false
                else {
                    val original = stillOriginal()
                    allowed() && statusMutable.value == BackendRealtimeStatus.Live &&
                        metadata.role in setOf("owner", "editor") && original
                }
            }
            active()
            try { withTimeout(30_000) { waiting.result.await() } }
            catch (_: TimeoutCancellationException) {
                diagnosticMutable.value = BackendRealtimeDiagnostic(statusMutable.value, "submission_unconfirmed")
                throw RealtimeSubmissionUnconfirmed()
            }
        } finally {
            if (awaitingAck === waiting) awaitingAck = null
            waiting.result.cancel()
        }
    }
    fun stopPublication() {
        if (closed) return
        diagnosticMutable.value = BackendRealtimeDiagnostic(statusMutable.value, "presence_publication_stopped")
        statusMutable.value = BackendRealtimeStatus.Failed; release()
    }
    fun openPresencePublisher(session: WorkspaceSession, subscription: String, room: String,
        canPublish: () -> Boolean, mayShare: () -> Boolean): WorkspacePresencePublisher? {
        val limits = realtimeCapabilities ?: return null
        fun live() = allowed() && canPublish() && statusMutable.value == BackendRealtimeStatus.Live &&
            session.userId == userId && session.clientId == clientId && session.workspace.id.value == workspaceId &&
            session.role.token == metadata.role && subscription == subscriptionId && presenceMutable.value?.roomEpoch == room &&
            writer != null && limits.presence
        if (!live()) return null
        val token = randomUuid(); publisherToken = token
        return object : WorkspacePresencePublisher {
            private var stopped = false
            private var localSequence = 0L
            private var first = true
            override suspend fun publish(update: WorkspacePresenceUpdate) {
                currentCoroutineContext().ensureActive()
                presenceWrites.withLock {
                    fun current() = !stopped && publisherToken == token && live()
                    check(current())
                    require(update.presenceSeq in 1..RealtimeMaxSequence && update.presenceSeq > localSequence)
                    update.cursor?.let { require(it.x.isFinite() && it.y.isFinite() && abs(it.x) <= 10_000_000f && abs(it.y) <= 10_000_000f) }
                    require(update.selectedIds.size <= 128); update.selectedIds.forEach { requireRealtimeUuid(it.value) }
                    val known = requireNotNull(projection).state.objects.map { it.objectId }.toSet()
                    val selected = update.selectedIds.map { it.value }.filter { it in known }.sorted().take(limits.maxSelectionIds).toSet()
                    val containsPrivatePresence = update.cursor != null || selected.isNotEmpty()
                    check(!containsPrivatePresence || mayShare())
                    val cursorChanged = first || update.cursor != lastCursor
                    val selectionChanged = first || selected != lastSelection
                    val cursorInterval = (1000L + limits.cursorUpdatesPerSecond - 1) / limits.cursorUpdatesPerSecond
                    val selectionInterval = (1000L + limits.selectionUpdatesPerSecond - 1) / limits.selectionUpdatesPerSecond
                    while (true) {
                        currentCoroutineContext().ensureActive(); check(current())
                        val now = presenceClock.elapsedNow().inWholeMilliseconds
                        val cooldown = presencePausedUntil.value - now
                        // A rate-limited clear must not keep an old cursor alive through
                        // continued heartbeats after consent is withdrawn: disconnect instead.
                        if (!containsPrivatePresence && !mayShare() && cooldown > 0) {
                            stopPublication(); error("Presence clear blocked by cooldown")
                        }
                        val wait = maxOf(0L, cooldown,
                            if (cursorChanged) lastCursorAt?.let { it + cursorInterval - now } ?: 0L else 0L,
                            if (selectionChanged) lastSelectionAt?.let { it + selectionInterval - now } ?: 0L else 0L)
                        if (wait == 0L) break
                        delay(wait)
                    }
                    currentCoroutineContext().ensureActive(); check(current())
                    require(wirePresenceSeq < RealtimeMaxSequence); wirePresenceSeq++
                    val message = buildJsonObject {
                        put("type", "presence_update"); put("protocolVersion", 1); put("workspaceId", workspaceId); put("connectionEpoch", epoch)
                        put("presenceSeq", wirePresenceSeq)
                        put("cursor", update.cursor?.let { buildJsonObject { put("x", it.x); put("y", it.y) } } ?: JsonNull)
                        put("selectedObjectIds", JsonArray(selected.map(::JsonPrimitive)))
                    }
                    requireNotNull(writer).send(message) { current() && (!containsPrivatePresence || mayShare()) }
                    currentCoroutineContext().ensureActive(); check(current())
                    val completed = presenceClock.elapsedNow().inWholeMilliseconds
                    if (cursorChanged) lastCursorAt = completed
                    if (selectionChanged) lastSelectionAt = completed
                    lastCursor = update.cursor; lastSelection = selected; localSequence = update.presenceSeq; first = false
                }
            }
            override fun close() { stopped = true; if (publisherToken == token) publisherToken = null }
        }
    }
    private class RealtimeStop : Exception()
}
private val EnvelopeKeys = setOf("type", "protocolVersion", "workspaceId", "connectionEpoch")
private fun JsonObject.text(key: String) = requireNotNull((get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content)
private fun JsonObject.sequence(key: String) = requireNotNull((get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
    .also { require(it in 0..RealtimeMaxSequence) }
