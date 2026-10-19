package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.serialization.Serializable

/** Original submitted intent/proof and exact reviewed choices survive replacing the active
 * proposal. This record is never an authorization to upload the continuation. */
@Serializable data class AppliedLocalSyncRebase(
    val reviewed: LocalSyncRebaseProposal,
    val operation: LocalWorkspaceContentOperation,
    val materializations: List<LocalAssetMaterialization> = emptyList(),
) {
    init {
        require(operation.before == reviewed.localBefore && !operation.restoreVersions)
        if (materializations.isEmpty()) require(operation.after == reviewed.target)
        else {
            val first = materializations.first()
            val local = WorkspaceSession(first.userId, first.clientId, WorkspaceMemberRole.Owner, operation.before.version, 0, operation.before)
            require(MaterializedAssetProjection(local, reviewed.context.original.binding, materializations).toLocal(reviewed.target) == operation.after)
        }
        require(operation.operationId.length <= 256)
        require(operation.appliedWorkspace().id == reviewed.localBefore.id)
    }
    val workspace: Workspace get() = operation.appliedWorkspace()
    val checkpoint: LocalWorkspaceSyncCheckpoint get() = LocalWorkspaceSyncCheckpoint(reviewed.context.original.binding,
        reviewed.context.committed.copy(id = workspace.id), reviewed.context.committedVersion, reviewed.context.committedSeq)
    internal fun continuation(local: WorkspaceSession): LocalWorkspaceSyncProposal {
        require(local.workspace == workspace)
        val plan = LocalWorkspaceSyncMergePlan(LocalWorkspaceSyncSnapshot(local, checkpoint, materializations = materializations), reviewed.context.remoteSession())
        return plan.proposal(operation.operationId, plan.rows.associate { it.id to LocalSyncMergeChoice.Local })
            .copy(localDigest = syncWorkspaceDigest(operation.before))
    }
    val normalizedOperation: LocalWorkspaceContentOperation get() = operation.copy(after = workspace)
}
