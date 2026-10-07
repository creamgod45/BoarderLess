package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Transport callbacks can return or throw after the observing session was cancelled. */
internal suspend fun awaitActiveWorkspaceRefresh(read: suspend () -> WorkspaceSession): WorkspaceSession {
    currentCoroutineContext().ensureActive()
    val refreshed = try { read() } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        throw error
    }
    currentCoroutineContext().ensureActive()
    return refreshed
}

/** Transport-neutral signals, not mutations or durable acknowledgements. */
sealed interface WorkspaceRemoteNotification {
    val workspaceId: WorkspaceId
    data class Joined(override val workspaceId: WorkspaceId) : WorkspaceRemoteNotification
    data class Committed(override val workspaceId: WorkspaceId, val fromServerSeq: Long, val toServerSeq: Long,
        val workspaceVersion: Long) : WorkspaceRemoteNotification
    data class MetadataChanged(override val workspaceId: WorkspaceId) : WorkspaceRemoteNotification
}

enum class WorkspaceCatchUpReason { Notification, Reconnected, Poll, TransportEnded }

/**
 * Notifications wake an authoritative refresh; they never advance lastServerSeq or mark saves acked.
 * Keep polling even with a live stream until server membership/metadata broadcasts are contracted.
 */
fun workspaceCatchUpRequests(
    session: WorkspaceSession,
    notifications: Flow<WorkspaceRemoteNotification>?,
    pollIntervalMs: Long = 3000,
): Flow<WorkspaceCatchUpReason> = flow {
    require(pollIntervalMs > 0)
    coroutineScope {
        val wakeups = Channel<WorkspaceCatchUpReason>(Channel.CONFLATED)
        val observer = notifications?.let { source -> launch {
            try {
                source.collect { event ->
                    if (event.workspaceId != session.workspace.id) return@collect
                    when (event) {
                        is WorkspaceRemoteNotification.Joined -> wakeups.send(WorkspaceCatchUpReason.Reconnected)
                        is WorkspaceRemoteNotification.MetadataChanged -> wakeups.send(WorkspaceCatchUpReason.Notification)
                        is WorkspaceRemoteNotification.Committed -> {
                            // Includes atomic multi-operation transactions; sequence is not workspace version.
                            if (event.fromServerSeq > 0 && event.toServerSeq >= event.fromServerSeq &&
                                event.workspaceVersion > session.workspaceVersion && event.toServerSeq > session.lastServerSeq) {
                                wakeups.send(WorkspaceCatchUpReason.Notification)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* REST remains authoritative and continues below. */ }
            finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) wakeups.trySend(WorkspaceCatchUpReason.TransportEnded) }
        } }
        try {
            while (true) {
                val reason = withTimeoutOrNull(pollIntervalMs) { wakeups.receive() } ?: WorkspaceCatchUpReason.Poll
                emit(reason)
            }
        } finally { observer?.cancel(); wakeups.close() }
    }
}
