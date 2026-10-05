package cg.creamgod.boarderless.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only inspection, NOT replay into the active history or submission of old operations. */
internal suspend fun inspectWorkspaceDraftBackup(
    content: String,
    opened: WorkspaceSession,
    canPublish: () -> Boolean,
    refresh: suspend (WorkspaceSession) -> WorkspaceSession,
): WorkspaceDraftReview {
    currentCoroutineContext().ensureActive()
    check(canPublish())
    val historical = withContext(Dispatchers.Default) { WorkspaceDraftBackupReader().read(content, opened.workspace.id) }
    currentCoroutineContext().ensureActive()
    check(canPublish())
    val fresh = refresh(opened) // Even viewer inspection requires a current read authorization.
    currentCoroutineContext().ensureActive()
    check(canPublish())
    check(fresh.userId == opened.userId && fresh.clientId == opened.clientId && fresh.workspace.id == opened.workspace.id)
    check(fresh.workspaceVersion >= opened.workspaceVersion && fresh.lastServerSeq >= opened.lastServerSeq)
    return historical.copy(current = fresh.workspace, currentVersion = fresh.workspaceVersion,
        currentServerSeq = fresh.lastServerSeq)
}
