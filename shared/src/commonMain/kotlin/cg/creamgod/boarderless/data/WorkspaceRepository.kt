package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId

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
    suspend fun openOrCreateWorkspace(): WorkspaceSession

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

    suspend fun submit(
        session: WorkspaceSession,
        operation: WorkspaceOperation,
    ): SubmitOutcome

    fun close()
}

internal fun WorkspaceSession.hasRemoteChangesComparedTo(current: WorkspaceSession): Boolean =
    workspace.id == current.workspace.id &&
        workspaceVersion >= current.workspaceVersion &&
        (
            workspaceVersion > current.workspaceVersion ||
                lastServerSeq > current.lastServerSeq ||
                role != current.role ||
                workspace.title != current.workspace.title
            )
