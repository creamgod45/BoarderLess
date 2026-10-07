package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import kotlinx.serialization.Serializable

/** Locally retained complete server response, not a signature or future write authorization. */
@Serializable internal data class StoppedSubmissionEvidence(
    val receipt: SubmissionReceiptResponseDto,
    val acknowledgement: AcceptedOperationsDto?,
    val observedWorkspaceVersion: Long,
    val observedServerSeq: Long,
)

internal fun StoppedSubmissionEvidence.validateAgainst(submitted: PendingWorkspaceSubmission): PendingReceiptStatus {
    validatePendingWorkspaceSubmission(submitted)
    val result = receipt.validatePendingReceipt(submitted.scope.workspaceId, submitted.scope.userId, submitted.request)
    require(result.status != PendingReceiptStatus.Unknown)
    require(observedWorkspaceVersion in maxOf(result.headWorkspaceVersion, submitted.request.baseVersion)..9_007_199_254_740_991L)
    require(observedServerSeq in result.headServerSeq..9_007_199_254_740_991L)
    if (result.status == PendingReceiptStatus.Committed) {
        val committed = receipt.receipts.single()
        val ack = requireNotNull(acknowledgement)
        require(
            ack.status == "duplicate" && ack.workspaceVersion == committed.workspaceVersion &&
                ack.fromServerSeq == committed.fromServerSeq && ack.toServerSeq == committed.toServerSeq,
        )
        require(
            validateOriginalCommittedLog(
                committed,
                submitted.request,
                submitted.scope.userId,
                FullCatchUpOperationsDto(ack.operations, ack.toServerSeq, false),
            ) == ack,
        )
    } else {
        require(acknowledgement == null)
    }
    return result.status
}
