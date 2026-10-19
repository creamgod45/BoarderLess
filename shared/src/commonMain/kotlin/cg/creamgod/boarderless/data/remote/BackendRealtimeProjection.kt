package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal enum class RealtimeProjectionPhase { Joining, CatchingUp, Live, Closed }

@Serializable internal data class RealtimeTransactionFrame(
    val type: String, val protocolVersion: Int, val workspaceId: String, val connectionEpoch: String,
    val transactionId: String, val fromServerSeq: Long, val toServerSeq: Long,
    val workspaceVersion: Long, val operations: List<CommittedWorkspaceOperationDto>,
)

/** One physical connection, sequentially consumed. An ACK advertises only a confirmed
 * durable projection, never the received head. Any validation/persistence failure closes
 * this machine; the transport must then close, discard presence and obtain a new ticket. */
internal class BackendRealtimeProjection(
    private val workspaceId: String,
    private val userId: String,
    private val connectionEpoch: String,
    initial: WorkspaceStateDto,
    private val capabilities: BackendCanvasReadCapabilities,
    private val maximumFrameBytes: Int,
    initialMetadataRevision: Long,
) {
    var phase = RealtimeProjectionPhase.Joining
        private set
    var state = initial
        private set
    var metadataRevision = initialMetadataRevision
        private set
    private var barrier: Long? = null
    private var connectionId: String? = null
    private var advertisedMetadataRevision: Long? = null
    private var advertisedWorkspaceVersion: Long? = null
    private var snapshotRequested = false
    private val strict = Json { encodeDefaults = true }
    init {
        requireRealtimeUuid(workspaceId); requireRealtimeUuid(userId); requireRealtimeUuid(connectionEpoch)
        require(initial.workspaceId == workspaceId && initialMetadataRevision in 0..RealtimeMaxSequence)
        require(initial.throughServerSeq in 0..RealtimeMaxSequence && initial.workspaceVersion in 0..RealtimeMaxSequence)
        require(maximumFrameBytes in 1024..4 * 1024 * 1024)
        initial.toDomainWorkspace("Realtime checkpoint validation", capabilities)
    }
    fun close() { phase = RealtimeProjectionPhase.Closed }

    /** Returns a parsed scoped frame; does not expose unauthenticated contents to UI. */
    fun decode(bytes: ByteArray): JsonObject = guarded {
        require(phase != RealtimeProjectionPhase.Closed && bytes.size in 1..maximumFrameBytes)
        val frame = parseStrictJsonObject(bytes.decodeToString(throwOnInvalidSequence = true))
        envelope(frame)
        frame
    }

    /** Persist metadata from a fresh authoritative read before acknowledging a newer
     * advertised revision. Join does not contain the title, so it is not a metadata snapshot. */
    suspend fun confirmMetadata(revision: Long, persist: suspend () -> Unit) {
        try {
            require(phase != RealtimeProjectionPhase.Closed && revision in metadataRevision..RealtimeMaxSequence)
            currentCoroutineContext().ensureActive(); persist(); currentCoroutineContext().ensureActive()
            metadataRevision = revision
        } catch (failure: Throwable) { close(); throw failure }
    }

    fun joined(frame: JsonObject): JsonObject? = guarded {
        require(phase == RealtimeProjectionPhase.Joining)
        envelope(frame)
        frame.exact("type", "protocolVersion", "workspaceId", "connectionEpoch", "connectionId", "userId", "role",
            "barrierServerSeq", "workspaceVersion", "metadataRevision")
        require(frame.string("type") == "join_accepted" && frame.string("userId") == userId)
        require(frame.string("role") in setOf("owner", "editor", "commenter", "viewer"))
        val id = frame.string("connectionId"); requireRealtimeUuid(id)
        connectionId = id; barrier = frame.number("barrierServerSeq")
        advertisedMetadataRevision = frame.number("metadataRevision")
        advertisedWorkspaceVersion = frame.number("workspaceVersion")
        phase = RealtimeProjectionPhase.CatchingUp
        if (state.throughServerSeq == barrier) {
            require(state.workspaceVersion == frame.number("workspaceVersion"))
            appliedMessage()
        } else null // Ahead checkpoints require an explicit snapshot_required response.
    }

    fun snapshotRequired(frame: JsonObject) = guarded {
        require(phase == RealtimeProjectionPhase.CatchingUp && !snapshotRequested)
        envelope(frame)
        frame.exact("type", "protocolVersion", "workspaceId", "connectionEpoch", "barrierServerSeq", "workspaceVersion", "metadataRevision", "reason")
        require(frame.string("type") == "snapshot_required" && frame.number("barrierServerSeq") == barrier &&
            frame.number("workspaceVersion") == advertisedWorkspaceVersion && frame.number("metadataRevision") == advertisedMetadataRevision)
        require(frame.string("reason") in setOf("checkpoint_not_at_transaction_boundary", "checkpoint_ahead_of_server", "catch_up_too_large"))
        snapshotRequested = true
    }

    suspend fun applyTransaction(frame: JsonObject, persist: suspend (WorkspaceStateDto) -> Unit): JsonObject? {
        try {
            require(phase in setOf(RealtimeProjectionPhase.CatchingUp, RealtimeProjectionPhase.Live) && !snapshotRequested)
            val decoded = strict.decodeFromJsonElement(RealtimeTransactionFrame.serializer(), frame)
            require(decoded.workspaceId == workspaceId && decoded.connectionEpoch == connectionEpoch && decoded.protocolVersion == 1)
            require(decoded.type == if (phase == RealtimeProjectionPhase.Live) "committed_transaction" else "catch_up_transaction")
            requireRealtimeUuid(decoded.transactionId)
            require(decoded.operations.size in 1..200 && decoded.operations.all { it.transactionId == decoded.transactionId })
            // No partial overlap or content-blind duplicate skipping. Out-of-order/gap
            // requires authoritative resync; the existing reducer validates all records.
            val next = reduceCommittedProjection(state, workspaceId, decoded.operations, decoded.fromServerSeq,
                decoded.toServerSeq, decoded.workspaceVersion, capabilities)
            currentCoroutineContext().ensureActive(); persist(next); currentCoroutineContext().ensureActive()
            state = next
            return if (phase == RealtimeProjectionPhase.CatchingUp && state.throughServerSeq >= requireNotNull(barrier)) appliedMessage() else null
        } catch (failure: Throwable) { close(); throw failure }
    }

    suspend fun installSnapshot(snapshot: WorkspaceStateDto, persist: suspend (WorkspaceStateDto) -> Unit): JsonObject {
        try {
            require(phase == RealtimeProjectionPhase.CatchingUp && snapshotRequested && snapshot.workspaceId == workspaceId)
            require(snapshot.throughServerSeq in requireNotNull(barrier)..RealtimeMaxSequence &&
                snapshot.workspaceVersion in requireNotNull(advertisedWorkspaceVersion)..RealtimeMaxSequence)
            snapshot.toDomainWorkspace("Realtime snapshot validation", capabilities)
            currentCoroutineContext().ensureActive(); persist(snapshot); currentCoroutineContext().ensureActive()
            state = snapshot
            snapshotRequested = false
            return appliedMessage()
        } catch (failure: Throwable) { close(); throw failure }
    }

    fun liveReady(frame: JsonObject) = guarded {
        require(phase == RealtimeProjectionPhase.CatchingUp && !snapshotRequested)
        envelope(frame)
        frame.exact("type", "protocolVersion", "workspaceId", "connectionEpoch", "throughServerSeq", "workspaceVersion",
            "metadataRevision", "connectionId")
        require(frame.string("type") == "live_ready" && frame.string("connectionId") == connectionId)
        require(frame.number("throughServerSeq") == state.throughServerSeq && frame.number("workspaceVersion") == state.workspaceVersion)
        require(frame.number("metadataRevision") == metadataRevision && state.throughServerSeq >= requireNotNull(barrier))
        phase = RealtimeProjectionPhase.Live
    }
    private fun envelope(frame: JsonObject) {
        require(frame.number("protocolVersion") == 1L && frame.string("workspaceId") == workspaceId && frame.string("connectionEpoch") == connectionEpoch)
    }

    fun appliedMessage(): JsonObject = guarded {
        require(phase == RealtimeProjectionPhase.CatchingUp && state.throughServerSeq >= requireNotNull(barrier))
        require(metadataRevision >= requireNotNull(advertisedMetadataRevision)) { "Persist fresh workspace metadata before catch-up ACK" }
        buildJsonObject {
            put("type", "catch_up_applied"); put("protocolVersion", 1); put("workspaceId", workspaceId); put("connectionEpoch", connectionEpoch)
            put("appliedServerSeq", state.throughServerSeq); put("workspaceVersion", state.workspaceVersion); put("metadataRevision", metadataRevision)
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() } catch (failure: Throwable) { close(); throw failure }
}

internal const val RealtimeMaxSequence = 9_007_199_254_740_991L
internal fun requireRealtimeUuid(value: String) = require(value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
private fun JsonObject.string(key: String): String = requireNotNull((get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content)
private fun JsonObject.number(key: String): Long = requireNotNull((get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull).also { require(it in 0..RealtimeMaxSequence) }
private fun JsonObject.exact(vararg keys: String) = require(this.keys == keys.toSet())
