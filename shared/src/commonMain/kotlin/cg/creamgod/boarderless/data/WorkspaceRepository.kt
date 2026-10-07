package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow

data class WorkspaceSummary(
    val id: WorkspaceId,
    val title: String,
    val role: String,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
)

data class WorkspaceActivity(
    val serverSeq: Long,
    val actorId: String,
    val clientId: String,
    val workspaceVersion: Long,
    val operationType: String,
    val committedAt: String,
)

data class PendingWorkspaceChange(val transactionId: String, val operationCount: Int)

enum class WorkspaceMemberRole(val token: String) {
    Owner("owner"),
    Editor("editor"),
    Commenter("commenter"),
    Viewer("viewer"),
    ;

    companion object {
        fun fromToken(token: String): WorkspaceMemberRole? = entries.firstOrNull { it.token == token }
    }
}

data class WorkspaceMember(
    val userId: String,
    val displayName: String,
    val role: WorkspaceMemberRole,
    val joinedAt: String,
)

data class WorkspaceSession(
    val userId: String,
    val clientId: String,
    val role: WorkspaceMemberRole,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
    val workspace: Workspace,
)

data class PendingWorkspaceDraft(val id: String, val operationCount: Int, val requiresReview: Boolean)
data class RestoredWorkspaceDraft(val session: WorkspaceSession, val operations: List<WorkspaceOperation>)

val WorkspaceSession.canEditContent: Boolean
    get() = role == WorkspaceMemberRole.Owner || role == WorkspaceMemberRole.Editor

sealed interface SubmitOutcome {
    data class Accepted(
        val workspaceVersion: Long,
        val lastServerSeq: Long,
    ) : SubmitOutcome

    data class Conflict(val current: WorkspaceSession) : SubmitOutcome
}

interface WorkspaceRepository {
    /** Opens [preferredWorkspaceId] when it can, otherwise the last opened or a new workspace. */
    suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId? = null): WorkspaceSession

    suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary>

    suspend fun createWorkspace(session: WorkspaceSession, title: String): WorkspaceSession

    suspend fun openWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId): WorkspaceSession

    suspend fun renameWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
        title: String,
    ): WorkspaceSummary

    suspend fun deleteWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId)

    suspend fun listRecentActivity(
        session: WorkspaceSession,
        limit: Int = 100,
    ): List<WorkspaceActivity>

    suspend fun listWorkspaceMembers(session: WorkspaceSession): List<WorkspaceMember>

    suspend fun setWorkspaceMemberRole(
        session: WorkspaceSession,
        userId: String,
        role: WorkspaceMemberRole,
    )

    suspend fun removeWorkspaceMember(session: WorkspaceSession, userId: String)

    suspend fun refresh(session: WorkspaceSession): WorkspaceSession

    /** Optional authenticated, session-scoped notification stream. Null keeps REST fallback. */
    fun observeRemoteChanges(session: WorkspaceSession): Flow<WorkspaceRemoteNotification>? = null

    /** Authenticated ephemeral snapshots only; null means presence is unavailable.
     * Echo this local subscription nonce, not a peer-supplied identity. Reconnect adapters must
     * validate connection epoch and emit renewed full snapshots using server-confirmed liveness.
     * This stream cannot change membership, content, acknowledgements or saved checkpoints.
     */
    fun observePresence(session: WorkspaceSession, subscriptionId: String): Flow<WorkspacePresenceSnapshot>? = null

    /** Optional outgoing handle for an authenticated live room; null means unavailable.
     * No guessed endpoint or automatic sharing. Must be opened only after trusted Live presence.
     * The transport owns physical connection epoch validation, including at the final write.
     */
    fun openPresencePublisher(
        session: WorkspaceSession,
        subscriptionId: String,
        roomEpoch: String,
    ): WorkspacePresencePublisher? = null

    suspend fun pendingChange(session: WorkspaceSession): PendingWorkspaceChange? = null

    /** Save before optimistic publication. Default repositories may not support local journals. */
    fun retainDraft(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {}
    suspend fun pendingDraft(session: WorkspaceSession): PendingWorkspaceDraft? = null
    suspend fun reviewPendingDraft(session: WorkspaceSession, draftId: String): WorkspaceDraftReview =
        throw UnsupportedOperationException("Draft review is unavailable")
    suspend fun restorePendingDraft(session: WorkspaceSession, draftId: String): RestoredWorkspaceDraft =
        throw UnsupportedOperationException("Draft recovery is unavailable")
    suspend fun dismissPendingDraft(session: WorkspaceSession, draftId: String): WorkspaceSession =
        throw UnsupportedOperationException("Draft recovery is unavailable")

    /** Explicit resend, followed by an authoritative refresh, never an optimistic old snapshot. */
    suspend fun retryPendingChange(session: WorkspaceSession, transactionId: String): WorkspaceSession =
        throw UnsupportedOperationException("Pending recovery is unavailable")

    /** Stops local resend only. Does not undo/delete anything already accepted by the server. */
    suspend fun dismissPendingChange(session: WorkspaceSession, transactionId: String): WorkspaceSession =
        throw UnsupportedOperationException("Pending recovery is unavailable")

    suspend fun submit(
        session: WorkspaceSession,
        operation: WorkspaceOperation,
    ): SubmitOutcome

    fun close()
}

internal fun WorkspaceSession.hasRemoteChangesComparedTo(current: WorkspaceSession): Boolean =
    userId == current.userId && clientId == current.clientId && workspace.id == current.workspace.id &&
        workspaceVersion >= current.workspaceVersion &&
        lastServerSeq >= current.lastServerSeq &&
        (
            workspaceVersion > current.workspaceVersion ||
                lastServerSeq > current.lastServerSeq ||
                role != current.role ||
                workspace.title != current.workspace.title
            )

/** A slow refresh cannot overwrite a changed owner or an advanced durable checkpoint. */
internal fun shouldApplyRemoteRefresh(
    requested: WorkspaceSession, current: WorkspaceSession, refreshed: WorkspaceSession,
): Boolean = current.userId == requested.userId && current.clientId == requested.clientId &&
    current.workspace.id == requested.workspace.id && current.workspaceVersion == requested.workspaceVersion &&
    current.lastServerSeq == requested.lastServerSeq && current.role == requested.role &&
    current.workspace.title == requested.workspace.title && refreshed.hasRemoteChangesComparedTo(current)
