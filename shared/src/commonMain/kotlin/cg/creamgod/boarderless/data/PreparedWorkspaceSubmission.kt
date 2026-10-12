package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.serialization.Serializable

/** Identity of an already saved wire request, never consent to send or proof of commit.
 * Digest covers canonical DTO JSON, not raw transport bytes. No endpoint/header/credential. */
@Serializable data class PreparedWorkspaceSubmission(
    val serverScope: String,
    val userId: String,
    val clientId: String,
    val workspaceId: WorkspaceId,
    val localOperationId: String,
    val transactionId: String,
    val baseVersion: Long,
    val operationIds: List<String>,
    val clientSequences: List<Long>,
    val requestDigest: String,
) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")) && requestDigest.matches(Regex("[0-9a-f]{64}")))
        require(listOf(userId, clientId, localOperationId, transactionId).all { it.isNotBlank() && it.length <= 256 })
        require(baseVersion in 0..9_007_199_254_740_991L)
        require(operationIds.size in 1..200 && operationIds.distinct().size == operationIds.size)
        require(operationIds.all { it.isNotBlank() && it.length <= 256 } && clientSequences.size == operationIds.size)
        require(clientSequences.all { it in 1..9_007_199_254_740_991L })
        require(clientSequences.zipWithNext().all { (first, second) -> second == first + 1 })
    }
}

/** Fresh receipt plus exact committed-log validation. Read-only; not a saved settlement. */
data class PreparedSubmissionVerification internal constructor(
    val prepared: PreparedWorkspaceSubmission,
    val status: PendingReceiptStatus,
    val current: WorkspaceSession,
    val committedWorkspaceVersion: Long?,
    val committedServerSeq: Long?,
    val committedWorkspace: Workspace? = null,
)
