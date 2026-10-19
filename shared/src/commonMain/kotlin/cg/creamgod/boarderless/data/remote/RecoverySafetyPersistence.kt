package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus

/** Atomic safety records, not server authority. Configure before repository construction. */
internal interface RecoverySafetyPersistence {
    fun fenceState(pending: PendingWorkspaceSubmission): PendingFenceAttemptStore.State?

    fun beginFence(pending: PendingWorkspaceSubmission)

    fun observeFence(
        pending: PendingWorkspaceSubmission,
        status: PendingReceiptStatus,
    )

    fun deletion(
        scope: PendingSubmissionScope,
        entity: String,
    ): CommittedDeletionEvidence?

    fun recordDeletions(
        scope: PendingSubmissionScope,
        records: List<CommittedDeletionEvidence>,
    )
}
