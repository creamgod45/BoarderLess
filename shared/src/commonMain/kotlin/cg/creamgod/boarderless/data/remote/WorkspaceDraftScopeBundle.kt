package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** Local atomic storage schema, NOT a signature or permission to resend.
 * Acknowledgement marker remains in journal; formal provenance/receipt is still BAI-012.
 * Wire-only bundles support existing legacy submissions without inventing a journal.
 * v2 retains complete stopped evidence without changing draft state or authorizing Apply.
 * v3 additionally retains independent historical archives across current draft transitions.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class WorkspaceDraftScopeBundle(
    val version: Int = 1,
    val scope: PendingSubmissionScope,
    val journal: WorkspaceDraftJournal? = null,
    val pending: PendingWorkspaceSubmission? = null,
    val acknowledged: LocalDraftAcknowledgement? = null,
    val legacyImportDigest: String? = null,
    val stoppedPending: PendingWorkspaceSubmission? = null,
    // v1 bytes retain their original field set; a saved outcome deliberately upgrades to v2.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val stoppedEvidence: StoppedSubmissionEvidence? = null,
    // v3 stores independent historical archives, not the head of the current journal.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val retainedStopped: List<StoppedSubmissionArchive> = emptyList(),
)

@Serializable
internal data class StoppedSubmissionArchive(
    val submitted: PendingWorkspaceSubmission,
    val evidence: StoppedSubmissionEvidence? = null,
)

/** Evidence of the exact locally validated outcome, not server-signed provenance. */
@Serializable
internal data class LocalDraftAcknowledgement(
    val submitted: PendingWorkspaceSubmission,
    val version: Long,
    val serverSeq: Long,
)

internal object WorkspaceDraftScopeBundleCodec {
    private val json =
        Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
    private const val MaximumBytes = 6 * 1024 * 1024

    fun scopeHash(scope: PendingSubmissionScope): String {
        validateScope(scope)
        return SHA256()
            .digest(json.encodeToString(scope).encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    fun encode(bundle: WorkspaceDraftScopeBundle): ByteArray {
        // Check framing/depth before replaying recursive domain operations.
        val content = json.encodeToString(bundle)
        requireBoundedDraftJsonDepth(content)
        val bytes = content.encodeToByteArray()
        require(bytes.size in 1..MaximumBytes)
        validate(bundle, bundle.scope)
        return bytes
    }

    fun decode(
        bytes: ByteArray,
        scope: PendingSubmissionScope,
    ): WorkspaceDraftScopeBundle {
        require(bytes.size in 1..MaximumBytes)
        val content = bytes.decodeToString(throwOnInvalidSequence = true)
        requireBoundedDraftJsonDepth(content)
        return json.decodeFromString<WorkspaceDraftScopeBundle>(content).also { validate(it, scope) }
    }

    private fun validateScope(scope: PendingSubmissionScope) {
        require(listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId).all(String::isNotBlank))
    }

    private fun validate(
        bundle: WorkspaceDraftScopeBundle,
        scope: PendingSubmissionScope,
    ) {
        validateScope(scope)
        require(bundle.version in 1..3 && bundle.scope == scope)
        require(
            (bundle.version == 1 && bundle.stoppedEvidence == null) ||
                (bundle.version == 2 && bundle.stoppedEvidence != null) || bundle.version == 3,
        )
        require((bundle.version == 3) == bundle.retainedStopped.isNotEmpty())
        require(bundle.legacyImportDigest == null || bundle.legacyImportDigest.matches(Regex("[0-9a-f]{64}")))
        require(bundle.journal != null || bundle.pending != null || bundle.stoppedPending != null || bundle.retainedStopped.isNotEmpty())
        require(bundle.retainedStopped.size <= 256)
        val archiveIds = bundle.retainedStopped.map { it.submitted.request.transactionId }
        require(archiveIds.distinct().size == archiveIds.size)
        require(
            listOfNotNull(
                bundle.pending?.request?.transactionId,
                bundle.stoppedPending?.request?.transactionId,
                bundle.acknowledged
                    ?.submitted
                    ?.request
                    ?.transactionId,
            ).none { it in archiveIds },
        )
        bundle.retainedStopped.forEach {
            require(it.submitted.scope == scope)
            validatePendingWorkspaceSubmission(it.submitted)
            it.evidence?.validateAgainst(it.submitted)
        }
        val journal = bundle.journal
        val pending = bundle.pending
        journal?.let {
            require(it.scope == scope)
            validateWorkspaceDraftJournal(it)
        }
        pending?.let {
            require(it.scope == scope)
            validatePendingWorkspaceSubmission(it)
        }
        bundle.stoppedPending?.let {
            require(pending == null && it.scope == scope)
            validatePendingWorkspaceSubmission(it)
            require(
                journal == null || (
                    journal.quarantined && journal.headTransactionId == null &&
                        journal.operations.firstOrNull()?.operationId == it.localOperationId &&
                        journal.baseVersion == it.request.baseVersion
                ),
            )
        }
        bundle.stoppedEvidence?.validateAgainst(requireNotNull(bundle.stoppedPending))
        bundle.acknowledged?.let {
            require(it.submitted.scope == scope)
            validatePendingWorkspaceSubmission(it.submitted)
            require(journal != null && journal.lastAcknowledgedTransactionId == it.submitted.request.transactionId)
            require(it.version == journal.baseVersion && it.serverSeq == journal.baseServerSeq)
            require(it.version > it.submitted.request.baseVersion && it.serverSeq in 1..9_007_199_254_740_991L)
        }
        if (journal != null) {
            if (pending == null) {
                require(journal.headTransactionId == null)
            } else {
                require(journal.headTransactionId == pending.request.transactionId)
                require(journal.operations.firstOrNull()?.operationId == pending.localOperationId)
                require(journal.baseVersion == pending.request.baseVersion)
                // An already acknowledged wire is not an unresolved head. Migration must reconcile explicitly.
                require(journal.lastAcknowledgedTransactionId != pending.request.transactionId)
            }
        }
    }
}
