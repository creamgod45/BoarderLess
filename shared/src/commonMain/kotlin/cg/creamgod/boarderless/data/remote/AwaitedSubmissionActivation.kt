package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable private data class AwaitedSubmissionActivationRecord(val version: Int = 1, val target: PendingSubmissionScope,
    val publicationGeneration: Long, val sourceDigest: String, val planDigest: String)
internal class ActivatedAwaitedSubmission internal constructor(val runtime: AwaitedSubmissionRuntime,
    private val awaitCalls: suspend () -> Unit, private val release: () -> Unit) {
    fun close() = release()
    /** Logical runtime calls only. Host must also drain platform physical I/O before
     * releasing its real writer lease; cancelled browser calls may still own an IDB ticket. */
    suspend fun closeAndAwaitCalls() { release(); awaitCalls() }
}

/** Initial cutover and explicit reopening of previously activated registered scopes.
 * Real writer exclusion and original actor/client identity checks have NO defaults and
 * remain required throughout the returned lease, not just while the wizard is visible.
 * Does not change Settings, client identity, selected repository or legacy records. */
internal class AwaitedSubmissionActivationManager(private val controls: AwaitedDurableLocalRecordStore,
    private val publisher: AwaitedLegacyMigrationPublisher, private val drafts: AwaitedAtomicWorkspaceDraftPersistence,
    private val sequences: AwaitedAtomicClientSequenceAllocator, private val safety: AwaitedAtomicRecoverySafetyPersistence,
    private val requireAllOtherWritersExcluded: () -> Unit, private val requireOriginalIdentity: (PendingSubmissionScope) -> Unit) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private class LeaseIdentity
    private data class LeaseState(val closed: Boolean = false, val calls: Int = 0, val releasing: Boolean = false)
    private val issued = MutableStateFlow<LeaseIdentity?>(null)
    private fun key(target: PendingSubmissionScope) = SHA256().digest(
        "boarderless.awaited-submission-activation.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(target)}".encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun guard(target: PendingSubmissionScope) { requireAllOtherWritersExcluded(); requireOriginalIdentity(target) }
    suspend fun confirm(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): ActivatedAwaitedSubmission =
        confirmOrReopen(target, review, recapture, reopening = false)

    suspend fun reopen(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): ActivatedAwaitedSubmission =
        confirmOrReopen(target, review, recapture, reopening = true)

    private suspend fun confirmOrReopen(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan, reopening: Boolean): ActivatedAwaitedSubmission = mutex.withLock {
        require(issued.value == null) { "Close the existing runtime lease before confirming another" }
        currentCoroutineContext().ensureActive(); guard(target)
        publisher.requirePublished(target) // Preparing/missing must not trigger publication here.
        guard(target)
        val plan = review.requireUnchanged(recapture()); guard(target)
        // Reopen cannot adopt coincidentally matching files without an activation receipt.
        if (reopening) requireNotNull(controls.read(key(target))) { "Activation receipt missing; do not initialize" }
        currentCoroutineContext().ensureActive(); guard(target)
        val published = if (reopening) publisher.confirmAdvanced(target, review, recapture)
            else publisher.confirmPublished(target, review, recapture)
        require(published.publication.phase == AwaitedMigrationPhase.Published)
        currentCoroutineContext().ensureActive(); guard(target)
        val next = AwaitedSubmissionActivationRecord(target = target, publicationGeneration = published.generation,
            sourceDigest = review.sourceDigest, planDigest = review.planDigest)
        val recordKey = key(target); val before = controls.read(recordKey)
        currentCoroutineContext().ensureActive(); guard(target)
        if (before != null) {
            require(before.generation in 1 until RealtimeMaxSequence && before.payload.size in 1..64 * 1024)
            val raw = parseStrictJsonObject(before.payload.decodeToString(throwOnInvalidSequence = true))
            require(raw.keys == setOf("version", "target", "publicationGeneration", "sourceDigest", "planDigest") && raw["version"] == JsonPrimitive(1))
            require((raw["publicationGeneration"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
            require(json.decodeFromJsonElement(AwaitedSubmissionActivationRecord.serializer(), raw) == next) {
                "Activation receipt changed; explicit reconciliation required"
            }
            controls.confirmDurable(recordKey, before.generation)
        } else {
            require(!reopening) { "Activation receipt disappeared during reopen" }
            val bytes = json.encodeToString(AwaitedSubmissionActivationRecord.serializer(), next).encodeToByteArray(throwOnInvalidSequence = true)
            require(bytes.size <= 64 * 1024)
            controls.compareAndSet(recordKey, null, bytes); controls.confirmDurable(recordKey, 1)
        }
        currentCoroutineContext().ensureActive(); guard(target)
        val saved = requireNotNull(controls.read(recordKey))
        require(saved.generation == (before?.generation ?: 1L) && saved.payload.size <= 64 * 1024)
        require(json.decodeFromJsonElement(AwaitedSubmissionActivationRecord.serializer(),
            parseStrictJsonObject(saved.payload.decodeToString(throwOnInvalidSequence = true))) == next)
        currentCoroutineContext().ensureActive(); guard(target)
        val scopes = plan.scopes.map { it.scope }.toSet()
        val state = MutableStateFlow(LeaseState())
        val drained = MutableStateFlow(false)
        val leaseIdentity = LeaseIdentity()
        fun drain() {
            while (true) {
                val before = state.value
                if (!before.closed || before.calls != 0 || before.releasing) return
                if (state.compareAndSet(before, before.copy(releasing = true))) {
                    issued.compareAndSet(leaseIdentity, null)
                    drained.value = true
                    return
                }
            }
        }
        fun closeLease() {
            while (true) {
                val before = state.value
                if (before.closed || state.compareAndSet(before, before.copy(closed = true))) break
            }
            drain()
        }
        fun active(scope: PendingSubmissionScope) {
            check(!state.value.closed && issued.value === leaseIdentity); guard(target)
            require(scope in scopes && scope.apiBase == target.apiBase && scope.userId == target.userId && scope.clientId == target.clientId) {
                "Scope needs explicit registration or historical recovery"
            }
        }
        suspend fun <T> call(scope: PendingSubmissionScope, action: suspend () -> T): T {
            currentCoroutineContext().ensureActive()
            while (true) {
                active(scope)
                val before = state.value
                check(!before.closed && before.calls < Int.MAX_VALUE)
                if (state.compareAndSet(before, before.copy(calls = before.calls + 1))) break
            }
            try {
                active(scope)
                val value = action(); currentCoroutineContext().ensureActive(); active(scope); return value
            } finally {
                while (true) {
                    val before = state.value
                    check(before.calls > 0)
                    if (state.compareAndSet(before, before.copy(calls = before.calls - 1))) break
                }
                drain()
            }
        }
        val guardedDrafts = object : AwaitedWorkspaceDraftPersistence {
            override suspend fun read(scope: PendingSubmissionScope) = call(scope) { drafts.read(scope) }
            override suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) = call(scope) { drafts.append(scope, session, before, operation) }
            override suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
                operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
                beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                call(scope) { drafts.appendWithIntent(scope, session, before, operation, intent, beforeHistory) }
            override suspend fun stage(entry: PendingWorkspaceSubmission) = call(entry.scope) { drafts.stage(entry) }
            override suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) = call(entry.scope) { drafts.acknowledge(entry, version, seq) }
            override suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
                history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                call(entry.scope) { drafts.acknowledgeWithHistory(entry, version, seq, history) }
            override suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession) = call(scope) { drafts.restore(scope, id, current) }
            override suspend fun remove(scope: PendingSubmissionScope, id: String) = call(scope) { drafts.remove(scope, id) }
            override suspend fun stop(scope: PendingSubmissionScope, transactionId: String) = call(scope) { drafts.stop(scope, transactionId) }
            override suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, expectedGeneration: Long, submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence) = call(scope) { drafts.recordStoppedEvidence(scope, expectedGeneration, submitted, evidence) }
        }
        val guardedSequences = object : AwaitedClientSequenceAllocator {
            override suspend fun reserve(scope: PendingSubmissionScope, count: Int) = call(scope) { sequences.reserve(scope, count) }
            override suspend fun advance(scope: PendingSubmissionScope, sequence: Long) = call(scope) { sequences.advance(scope, sequence) }
        }
        val guardedSafety = object : AwaitedRecoverySafetyPersistence {
            override suspend fun fenceState(pending: PendingWorkspaceSubmission) = call(pending.scope) { safety.fenceState(pending) }
            override suspend fun beginFence(pending: PendingWorkspaceSubmission) = call(pending.scope) { safety.beginFence(pending) }
            override suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus) = call(pending.scope) { safety.observeFence(pending, status) }
            override suspend fun deletion(scope: PendingSubmissionScope, entity: String) = call(scope) { safety.deletion(scope, entity) }
            override suspend fun recordDeletions(scope: PendingSubmissionScope, records: List<CommittedDeletionEvidence>) = call(scope) { safety.recordDeletions(scope, records) }
        }
        guard(target)
        currentCoroutineContext().ensureActive()
        check(issued.compareAndSet(null, leaseIdentity)) { "Runtime lease changed during activation" }
        try { active(target) } catch (failure: Throwable) {
            closeLease()
            throw failure
        }
        ActivatedAwaitedSubmission(AwaitedSubmissionRuntime(guardedDrafts, guardedSequences, guardedSafety, ::active),
            awaitCalls = {
                check(state.value.closed)
                drained.first { it }
                currentCoroutineContext().ensureActive()
                check(state.value.calls == 0 && issued.value !== leaseIdentity)
            }, release = ::closeLease)
    }
}
