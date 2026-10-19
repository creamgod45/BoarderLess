package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
internal data class PendingFenceAttemptRecord(
    val schema: Int = 1,
    val scope: PendingSubmissionScope,
    val transactionId: String,
    val wireDigest: String,
    val state: PendingFenceAttemptStore.State,
)

/** Verified local recovery barrier, not server authority or Settings fsync/multi-process safety.
 * Retained even after pending settlement; never erase uncertainty on an unknown lookup.
 */
internal class PendingFenceAttemptStore(
    private val settings: Settings,
    private val native: RecoverySafetyPersistence? = null,
) {
    @Serializable internal enum class State { Unconfirmed, Fenced, Committed }

    private val json = Json { encodeDefaults = true }

    private fun digest(value: String) =
        SHA256().digest(value.encodeToByteArray()).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }

    private fun key(
        scope: PendingSubmissionScope,
        transactionId: String,
    ) = "fence1." +
        digest(json.encodeToString(scope) + ":" + transactionId)

    private fun key(pending: PendingWorkspaceSubmission) = key(pending.scope, pending.request.transactionId)

    private fun entry(
        pending: PendingWorkspaceSubmission,
        state: State,
    ) = PendingFenceAttemptRecord(
        scope = pending.scope,
        transactionId = pending.request.transactionId,
        wireDigest = digest(json.encodeToString(pending.request)),
        state = state,
    )

    fun state(pending: PendingWorkspaceSubmission): State? {
        native?.let { return it.fenceState(pending) }
        val raw = settings.getStringOrNull(key(pending)) ?: return null
        check(raw.length in 1..4096) { "Invalid fence recovery record" }
        val found = json.decodeFromString<PendingFenceAttemptRecord>(raw)
        check(found.schema == 1 && found == entry(pending, found.state)) { "Fence recovery scope or wire mismatch" }
        return found.state
    }

    /** Keep orphan/foreign markers too; absence of their wire never means safe to resend. */
    fun inventoryForMigration(): List<PendingFenceAttemptRecord> =
        captureLegacyRecoveryRecords(settings, "fence1.", 256) { storageKey, raw ->
            json.decodeFromString<PendingFenceAttemptRecord>(raw).also {
                check(it.schema == 1 && it.transactionId.isNotBlank() && it.transactionId.length <= 4096)
                WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
                check(it.wireDigest.matches(Regex("[0-9a-f]{64}")))
                check(storageKey == key(it.scope, it.transactionId))
            }
        }

    fun requireMatches(
        record: PendingFenceAttemptRecord,
        pending: PendingWorkspaceSubmission,
    ) {
        validatePendingWorkspaceSubmission(pending)
        check(record == entry(pending, record.state)) { "Captured fence marker differs from original wire" }
    }

    private fun save(
        pending: PendingWorkspaceSubmission,
        state: State,
    ) {
        val encoded = json.encodeToString(entry(pending, state))
        check(encoded.length <= 4096)
        settings.putString(key(pending), encoded)
        check(settings.getStringOrNull(key(pending)) == encoded) { "Fence recovery was not saved" }
    }

    fun begin(pending: PendingWorkspaceSubmission) {
        native?.let {
            it.beginFence(pending)
            return
        }
        validatePendingWorkspaceSubmission(pending)
        check(state(pending).let { it == null || it == State.Unconfirmed }) { "Original transaction already has a terminal fence result" }
        save(pending, State.Unconfirmed) // Must finish before HTTP dispatch.
    }

    fun observe(
        pending: PendingWorkspaceSubmission,
        status: PendingReceiptStatus,
    ) {
        native?.let {
            it.observeFence(pending, status)
            return
        }
        val previous = state(pending) ?: return // A normal read-only lookup does not create a marker.
        if (status == PendingReceiptStatus.Unknown) return
        val next = if (status == PendingReceiptStatus.Fenced) State.Fenced else State.Committed
        check(previous == State.Unconfirmed || previous == next) { "Conflicting original transaction evidence" }
        save(pending, next)
    }
}
