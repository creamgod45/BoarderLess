package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
internal data class RecoverySafetyBundle(
    val version: Int = 1,
    val scope: PendingSubmissionScope,
    val fences: List<PendingFenceAttemptRecord> = emptyList(),
    val deletions: List<CommittedDeletionEvidence> = emptyList(),
    val legacyImportDigest: String? = null,
)

internal data class StoredRecoverySafety(
    val generation: Long,
    val bundle: RecoverySafetyBundle,
)

/** Desktop POSIX single-file safety records. Explicit initialization/adoption only; no runtime
 * missing-record reset, Settings fallback, retry, GC or default activation. Draft/ledger files
 * are separate: this does NOT claim an atomic ACK settlement across those files.
 */
internal class DesktopRecoverySafetyStore(
    private val files: DesktopAtomicDraftStore,
    private val requireScope: (PendingSubmissionScope) -> Unit = {},
) : RecoverySafetyPersistence {
    private val json = Json { encodeDefaults = true }

    private fun digest(value: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    internal fun key(scope: PendingSubmissionScope) =
        digest("BoarderLess.recovery-safety.v1:" + WorkspaceDraftScopeBundleCodec.scopeHash(scope))

    private fun fence(
        pending: PendingWorkspaceSubmission,
        state: PendingFenceAttemptStore.State,
    ): PendingFenceAttemptRecord {
        validatePendingWorkspaceSubmission(pending)
        return PendingFenceAttemptRecord(
            scope = pending.scope,
            transactionId = pending.request.transactionId,
            wireDigest = digest(json.encodeToString(pending.request)),
            state = state,
        )
    }

    private fun encode(bundle: RecoverySafetyBundle) = RecoverySafetyBundleCodec.encode(bundle)

    private fun decode(
        bytes: ByteArray,
        scope: PendingSubmissionScope,
    ) = RecoverySafetyBundleCodec.decode(bytes, scope)

    fun read(scope: PendingSubmissionScope): StoredRecoverySafety? {
        requireScope(scope)
        val record = files.read(key(scope)) ?: return null
        return StoredRecoverySafety(
            record.generation,
            decode(
                requireNotNull(record.payload) {
                    "Safety tombstone requires recovery, not initialization"
                },
                scope,
            ),
        )
    }

    /** Caller excludes old writers and validates the entire source catalog/floor before activation.
     * Keeps old sources; digest makes repeated adoption a read, never a rewind of advanced data.
     */
    fun adoptLegacy(
        scope: PendingSubmissionScope,
        fences: List<PendingFenceAttemptRecord>,
        deletions: List<CommittedDeletionEvidence>,
    ): StoredRecoverySafety {
        val prepared = RecoverySafetyBundleCodec.prepareLegacy(scope, fences, deletions)
        val current = read(scope)
        if (current != null) {
            require(
                current.bundle.legacyImportDigest == prepared.legacyImportDigest,
            ) { "Safety source changed; explicit recovery required" }
            return current
        }
        val record = files.compareAndSet(key(scope), null, encode(prepared))
        return StoredRecoverySafety(record.generation, decode(requireNotNull(record.payload), scope))
    }

    private fun update(
        current: StoredRecoverySafety,
        bundle: RecoverySafetyBundle,
    ) {
        val record = files.compareAndSet(key(bundle.scope), current.generation, encode(bundle))
        check(decode(requireNotNull(record.payload), bundle.scope) == bundle)
    }

    private fun required(scope: PendingSubmissionScope) = checkNotNull(read(scope)) { "Safety store requires explicit initialization" }

    private fun matching(
        bundle: RecoverySafetyBundle,
        pending: PendingWorkspaceSubmission,
    ): PendingFenceAttemptRecord? =
        bundle.fences.singleOrNull { it.transactionId == pending.request.transactionId }?.also {
            require(it == fence(pending, it.state)) { "Safety fence differs from original wire" }
        }

    override fun fenceState(pending: PendingWorkspaceSubmission): PendingFenceAttemptStore.State? =
        matching(required(pending.scope).bundle, pending)?.state

    override fun beginFence(pending: PendingWorkspaceSubmission) {
        val current = required(pending.scope)
        val old = matching(current.bundle, pending)
        require(old == null || old.state == PendingFenceAttemptStore.State.Unconfirmed)
        if (old != null) return
        update(current, current.bundle.copy(fences = current.bundle.fences + fence(pending, PendingFenceAttemptStore.State.Unconfirmed)))
    }

    override fun observeFence(
        pending: PendingWorkspaceSubmission,
        status: PendingReceiptStatus,
    ) {
        val current = required(pending.scope)
        val old = matching(current.bundle, pending) ?: return
        if (status == PendingReceiptStatus.Unknown) return
        val next =
            if (status ==
                PendingReceiptStatus.Fenced
            ) {
                PendingFenceAttemptStore.State.Fenced
            } else {
                PendingFenceAttemptStore.State.Committed
            }
        require(old.state == PendingFenceAttemptStore.State.Unconfirmed || old.state == next)
        if (old.state == next) return
        update(current, current.bundle.copy(fences = current.bundle.fences.map { if (it == old) it.copy(state = next) else it }))
    }

    override fun deletion(
        scope: PendingSubmissionScope,
        entity: String,
    ) = required(scope).bundle.deletions.singleOrNull {
        it.entity ==
            entity
    }

    override fun recordDeletions(
        scope: PendingSubmissionScope,
        records: List<CommittedDeletionEvidence>,
    ) {
        require(records.isNotEmpty() && records.map { it.entity }.distinct().size == records.size)
        val current = required(scope)
        val merged =
            current.bundle.deletions
                .associateBy { it.entity }
                .toMutableMap()
        records.forEach { next ->
            require(next.scope == scope)
            merged[next.entity]?.let { old ->
                require(next == old || (next.tombstoneVersion > old.tombstoneVersion && next.throughServerSeq > old.throughServerSeq)) {
                    "Deletion evidence cannot rewind or change the same tombstone"
                }
            }
            merged[next.entity] = next
        }
        if (merged.values.toList() == current.bundle.deletions) return
        update(current, current.bundle.copy(deletions = merged.values.toList()))
    }
}

/** Shared by the pure migration planner and native writer: neither can skip typed validation. */
internal object RecoverySafetyBundleCodec {
    private val json = Json { encodeDefaults = true }

    private fun validate(
        bundle: RecoverySafetyBundle,
        scope: PendingSubmissionScope,
    ) {
        WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        require(bundle.version == 1 && bundle.scope == scope)
        require(bundle.legacyImportDigest == null || bundle.legacyImportDigest.matches(Regex("[0-9a-f]{64}")))
        require(bundle.fences.size <= 256 && bundle.deletions.size <= 4096)
        require(
            bundle.fences
                .map { it.transactionId }
                .distinct()
                .size == bundle.fences.size,
        )
        require(
            bundle.deletions
                .map { it.entity }
                .distinct()
                .size == bundle.deletions.size,
        )
        bundle.fences.forEach {
            require(it.schema == 1 && it.scope == scope && it.transactionId.isNotBlank())
            require(it.wireDigest.matches(Regex("[0-9a-f]{64}")))
        }
        bundle.deletions.forEach {
            require(
                it.scope == scope && (it.entity.startsWith("object:") || it.entity.startsWith("relation:")) &&
                    it.entity.substringAfter(':').isNotBlank(),
            )
            require(
                it.transactionId.isNotBlank() && it.tombstoneVersion in 2..9_007_199_254_740_990L &&
                    it.throughServerSeq in 1..9_007_199_254_740_991L && it.snapshotDigest.matches(Regex("[0-9a-f]{64}")),
            )
        }
    }

    fun encode(bundle: RecoverySafetyBundle): ByteArray {
        val raw = json.encodeToString(bundle)
        requireBoundedDraftJsonDepth(raw)
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..6 * 1024 * 1024)
        validate(bundle, bundle.scope)
        return bytes
    }

    fun decode(
        bytes: ByteArray,
        scope: PendingSubmissionScope,
    ): RecoverySafetyBundle {
        require(bytes.size in 1..6 * 1024 * 1024)
        val raw = bytes.decodeToString(throwOnInvalidSequence = true)
        requireBoundedDraftJsonDepth(raw)
        return json.decodeFromString<RecoverySafetyBundle>(raw).also { validate(it, scope) }
    }

    fun prepareLegacy(
        scope: PendingSubmissionScope,
        fences: List<PendingFenceAttemptRecord>,
        deletions: List<CommittedDeletionEvidence>,
    ): RecoverySafetyBundle {
        val source =
            RecoverySafetyBundle(scope = scope, fences = fences.sortedBy { it.transactionId }, deletions = deletions.sortedBy { it.entity })
        val bytes = encode(source)
        val fingerprint =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
        return decode(encode(decode(bytes, scope).copy(legacyImportDigest = fingerprint)), scope)
    }
}
