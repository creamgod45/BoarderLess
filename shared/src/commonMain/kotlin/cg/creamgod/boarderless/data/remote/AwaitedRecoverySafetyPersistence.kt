package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

internal interface AwaitedRecoverySafetyPersistence {
    suspend fun fenceState(pending: PendingWorkspaceSubmission): PendingFenceAttemptStore.State?
    suspend fun beginFence(pending: PendingWorkspaceSubmission)
    suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus)
    suspend fun deletion(scope: PendingSubmissionScope, entity: String): CommittedDeletionEvidence?
    suspend fun recordDeletions(scope: PendingSubmissionScope, records: List<CommittedDeletionEvidence>)
}
internal data class AwaitedRecoverySafetyRecord(val generation: Long, val bundle: RecoverySafetyBundle)

/** Scope-wide atomic fence+deletion provenance. Missing records require explicitly
 * reviewed adoption, not an empty reset. No automatic initialization, TTL, eviction,
 * Settings fallback, network authorization or atomicity claim across draft/ledger files. */
internal class AwaitedAtomicRecoverySafetyPersistence(private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PendingSubmissionScope) -> Unit,
    private val maximumRecordBytes: Int = 4 * 1024 * 1024) : AwaitedRecoverySafetyPersistence {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    init { require(maximumRecordBytes in 1024..6 * 1024 * 1024) }
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(scope: PendingSubmissionScope): String {
        requireScope(scope)
        return digest("boarderless.awaited-recovery-safety.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}".encodeToByteArray())
    }
    private suspend fun load(scope: PendingSubmissionScope): AwaitedRecoverySafetyRecord? {
        val stored = files.read(key(scope)) ?: return null
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(stored.generation in 1 until RealtimeMaxSequence && stored.payload.size in 1..maximumRecordBytes)
        return AwaitedRecoverySafetyRecord(stored.generation, RecoverySafetyBundleCodec.decode(stored.payload, scope))
    }
    suspend fun read(scope: PendingSubmissionScope) = mutex.withLock { load(scope) }
    suspend fun readDurable(scope: PendingSubmissionScope): AwaitedRecoverySafetyRecord = mutex.withLock {
        val found = requireNotNull(load(scope)) { "Registered recovery safety missing" }
        files.confirmDurable(key(scope), found.generation)
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(load(scope) == found) { "Recovery safety changed during durable read" }
        found
    }
    private suspend fun required(scope: PendingSubmissionScope) = requireNotNull(load(scope)) { "Recovery safety needs verified initialization" }
    private fun marker(pending: PendingWorkspaceSubmission, state: PendingFenceAttemptStore.State): PendingFenceAttemptRecord {
        validatePendingWorkspaceSubmission(pending)
        return PendingFenceAttemptRecord(scope = pending.scope, transactionId = pending.request.transactionId,
            wireDigest = digest(json.encodeToString(pending.request).encodeToByteArray(throwOnInvalidSequence = true)), state = state)
    }
    private fun matching(bundle: RecoverySafetyBundle, pending: PendingWorkspaceSubmission) =
        bundle.fences.singleOrNull { it.transactionId == pending.request.transactionId }?.also {
            require(it == marker(pending, it.state)) { "Fence marker differs from original request" }
        }
    private suspend fun save(scope: PendingSubmissionScope, before: AwaitedRecoverySafetyRecord?, next: RecoverySafetyBundle) {
        val bytes = RecoverySafetyBundleCodec.encode(next)
        require(next.scope == scope && bytes.size <= maximumRecordBytes)
        currentCoroutineContext().ensureActive(); require(load(scope) == before)
        val recordKey = key(scope)
        if (before != null && before.bundle == next) files.confirmDurable(recordKey, before.generation)
        else {
            files.compareAndSet(recordKey, before?.generation, bytes)
            files.confirmDurable(recordKey, (before?.generation ?: 0L) + 1)
        }
        currentCoroutineContext().ensureActive(); requireScope(scope)
    }
    /** Caller holds legacy-writer exclusion and has reviewed the complete source catalog. */
    suspend fun adoptLegacy(scope: PendingSubmissionScope, fences: List<PendingFenceAttemptRecord>, deletions: List<CommittedDeletionEvidence>) = mutex.withLock {
        val prepared = RecoverySafetyBundleCodec.prepareLegacy(scope, fences, deletions)
        val current = load(scope)
        if (current != null) {
            require(current.bundle.legacyImportDigest == prepared.legacyImportDigest)
            save(scope, current, current.bundle) // Confirm, never rewind advanced evidence.
        } else save(scope, null, prepared)
    }
    override suspend fun fenceState(pending: PendingWorkspaceSubmission) = mutex.withLock {
        matching(required(pending.scope).bundle, pending)?.state
    }
    override suspend fun beginFence(pending: PendingWorkspaceSubmission) = mutex.withLock {
        val current = required(pending.scope); val old = matching(current.bundle, pending)
        require(old == null || old.state == PendingFenceAttemptStore.State.Unconfirmed)
        save(pending.scope, current, if (old != null) current.bundle else current.bundle.copy(
            fences = current.bundle.fences + marker(pending, PendingFenceAttemptStore.State.Unconfirmed)))
    }
    override suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus) = mutex.withLock {
        val current = required(pending.scope); val old = matching(current.bundle, pending) ?: return@withLock
        if (status == PendingReceiptStatus.Unknown) return@withLock // Never erase uncertainty or invent a marker.
        val next = if (status == PendingReceiptStatus.Fenced) PendingFenceAttemptStore.State.Fenced else PendingFenceAttemptStore.State.Committed
        require(old.state == PendingFenceAttemptStore.State.Unconfirmed || old.state == next)
        save(pending.scope, current, current.bundle.copy(fences = current.bundle.fences.map { if (it == old) it.copy(state = next) else it }))
    }
    override suspend fun deletion(scope: PendingSubmissionScope, entity: String) = mutex.withLock {
        required(scope).bundle.deletions.singleOrNull { it.entity == entity }
    }
    override suspend fun recordDeletions(scope: PendingSubmissionScope, records: List<CommittedDeletionEvidence>) = mutex.withLock {
        require(records.isNotEmpty() && records.map { it.entity }.distinct().size == records.size)
        val current = required(scope); val merged = current.bundle.deletions.associateBy { it.entity }.toMutableMap()
        records.forEach { next ->
            require(next.scope == scope)
            merged[next.entity]?.let { old -> require(next == old ||
                (next.tombstoneVersion > old.tombstoneVersion && next.throughServerSeq > old.throughServerSeq)) }
            merged[next.entity] = next
        }
        save(scope, current, current.bundle.copy(deletions = merged.values.toList()))
    }
}
