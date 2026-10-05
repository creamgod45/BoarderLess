package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** Validated legacy content, captured ONLY while all legacy writers are quiescent.
 * Does not claim that independent Settings reads form an atomic snapshot.
 */
@Serializable
internal data class WorkspaceDraftLegacySnapshot(
    val scope: PendingSubmissionScope,
    val journal: WorkspaceDraftJournal?,
    val pending: PendingWorkspaceSubmission?,
)

internal object WorkspaceDraftLegacyMigration {
    private val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }

    fun prepare(source: WorkspaceDraftLegacySnapshot): WorkspaceDraftScopeBundle {
        // Freeze caller-owned lists; bounds/depth checked before replay/normalization.
        val content = json.encodeToString(source)
        require(content.encodeToByteArray().size in 1..6 * 1024 * 1024)
        requireBoundedDraftJsonDepth(content)
        val frozen = json.decodeFromString<WorkspaceDraftLegacySnapshot>(content)
        WorkspaceDraftScopeBundleCodec.scopeHash(frozen.scope)
        var journal = frozen.journal
        val pending = frozen.pending
        require(journal != null || pending != null)
        journal?.let { require(it.scope == frozen.scope); validateWorkspaceDraftJournal(it) }
        pending?.let { require(it.scope == frozen.scope); validatePendingWorkspaceSubmission(it) }
        if (journal != null && pending != null) {
            // Legacy save published wire before markSubmitted. Complete ONLY that matching pair;
            // never infer an ack, drop a wire or rebuild its transaction/operation/clientSeq.
            require(journal.lastAcknowledgedTransactionId != pending.request.transactionId)
            require(journal.operations.firstOrNull()?.operationId == pending.localOperationId)
            require(journal.baseVersion == pending.request.baseVersion)
            require(journal.headTransactionId == null || journal.headTransactionId == pending.request.transactionId)
            journal = journal.copy(headTransactionId = pending.request.transactionId)
        }
        val fingerprint = SHA256().digest(content.encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val bundle = WorkspaceDraftScopeBundle(scope = frozen.scope, journal = journal, pending = pending,
            legacyImportDigest = fingerprint)
        return WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(bundle), frozen.scope)
    }
}
