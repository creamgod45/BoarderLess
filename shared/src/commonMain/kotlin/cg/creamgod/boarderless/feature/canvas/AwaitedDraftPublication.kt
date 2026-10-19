package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DraftPublicationLive(val session: WorkspaceSession?, val history: WorkspaceHistory, val enabled: Boolean)
internal enum class DraftPublicationOutcome { Ready, Rejected, Unconfirmed, ScopeChanged }

/** A result remains private until storage ACK; a late ACK cannot publish into another scope.
 * ScopeChanged after retention leaves the durable receipt intact for explicit reconciliation.
 */
internal suspend fun awaitDraftPublication(
    repository: WorkspaceRepository,
    owner: WorkspaceSession,
    before: WorkspaceHistory,
    result: HistoryResult,
    intent: WorkspaceHistoryIntent = WorkspaceHistoryIntent.Edit,
    readLive: () -> DraftPublicationLive,
): DraftPublicationOutcome {
    val operation = result.appliedOperation ?: return DraftPublicationOutcome.Rejected
    if (!result.succeeded || before.workspace.id != owner.workspace.id) return DraftPublicationOutcome.Rejected
    fun current(): Boolean {
        val live = readLive()
        val session = live.session ?: return false
        return live.enabled && session.canEditContent && owner.canEditContent &&
            session.userId == owner.userId && session.clientId == owner.clientId &&
            session.workspace.id == owner.workspace.id && session.role == owner.role &&
            session.workspaceVersion == owner.workspaceVersion && session.lastServerSeq == owner.lastServerSeq &&
            live.history == before
    }
    if (!current()) return DraftPublicationOutcome.ScopeChanged
    try {
        repository.retainHistoryDraftAwaited(owner, before.workspace, operation, intent, before)
        currentCoroutineContext().ensureActive()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { return DraftPublicationOutcome.Unconfirmed }
    return if (current()) DraftPublicationOutcome.Ready else DraftPublicationOutcome.ScopeChanged
}
