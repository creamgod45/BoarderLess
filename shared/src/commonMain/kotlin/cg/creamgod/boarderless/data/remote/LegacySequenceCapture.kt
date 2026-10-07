package cg.creamgod.boarderless.data.remote

/** Read-only source capture, not proof of writer exclusion or a permission to initialize/reset. */
internal data class LegacySequenceCapture(
    val counter: Long,
    val floor: Long,
    val pending: List<PendingWorkspaceSubmission>,
    val stopped: List<PendingWorkspaceSubmission>,
    val journals: List<WorkspaceDraftJournal>,
    val fenceAttempts: List<PendingFenceAttemptRecord>,
    val deletionEvidence: List<CommittedDeletionEvidence>,
    // Their request digest contains no clientSeq: explicit recovery is required before activation.
    val unboundFenceAttempts: List<PendingFenceAttemptRecord>,
)
