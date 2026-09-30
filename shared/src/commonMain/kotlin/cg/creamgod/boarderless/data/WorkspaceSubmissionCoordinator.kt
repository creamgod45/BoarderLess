package cg.creamgod.boarderless.data

import kotlinx.coroutines.CancellationException

internal sealed interface WorkspaceSubmissionStep {
    data object Idle : WorkspaceSubmissionStep

    data class Accepted(
        val session: WorkspaceSession,
        val acceptedToken: Long,
    ) : WorkspaceSubmissionStep

    data class Conflict(
        val session: WorkspaceSession,
        val discardedCount: Int,
    ) : WorkspaceSubmissionStep

    data class Recovered(
        val session: WorkspaceSession,
        val discardedCount: Int,
        val submitError: Throwable,
    ) : WorkspaceSubmissionStep

    data class Failed(
        val session: WorkspaceSession,
        val discardedCount: Int,
        val submitError: Throwable,
        val refreshError: Throwable,
    ) : WorkspaceSubmissionStep
}

internal suspend fun submitNextWorkspaceChange(
    repository: WorkspaceRepository,
    session: WorkspaceSession,
    queue: WorkspaceSubmissionQueue,
): WorkspaceSubmissionStep {
    val pending = queue.first ?: return WorkspaceSubmissionStep.Idle
    return try {
        when (val outcome = repository.submit(session, pending.operation)) {
            is SubmitOutcome.Accepted -> WorkspaceSubmissionStep.Accepted(
                session = session.copy(
                    workspaceVersion = outcome.workspaceVersion,
                    lastServerSeq = outcome.lastServerSeq,
                    workspace = pending.workspaceAfter,
                ),
                acceptedToken = pending.token,
            )

            is SubmitOutcome.Conflict -> WorkspaceSubmissionStep.Conflict(
                session = outcome.current,
                discardedCount = queue.size,
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (submitError: Throwable) {
        try {
            WorkspaceSubmissionStep.Recovered(
                session = repository.refresh(session),
                discardedCount = queue.size,
                submitError = submitError,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (refreshError: Throwable) {
            WorkspaceSubmissionStep.Failed(
                session = session,
                discardedCount = queue.size,
                submitError = submitError,
                refreshError = refreshError,
            )
        }
    }
}
