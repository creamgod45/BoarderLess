package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace

internal data class PendingWorkspaceSubmission(
    val token: Long,
    val operation: WorkspaceOperation,
    val workspaceAfter: Workspace,
)

internal data class WorkspaceSubmissionQueue(
    private val entries: List<PendingWorkspaceSubmission> = emptyList(),
    private val nextToken: Long = 1,
) {
    val first: PendingWorkspaceSubmission? get() = entries.firstOrNull()
    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()

    fun enqueue(
        operation: WorkspaceOperation,
        workspaceAfter: Workspace,
    ): WorkspaceSubmissionQueue =
        copy(
            entries = entries + PendingWorkspaceSubmission(nextToken, operation, workspaceAfter),
            nextToken = nextToken + 1,
        )

    fun accept(token: Long): WorkspaceSubmissionQueue = if (entries.firstOrNull()?.token == token) copy(entries = entries.drop(1)) else this

    fun clear(): WorkspaceSubmissionQueue = copy(entries = emptyList())
}
