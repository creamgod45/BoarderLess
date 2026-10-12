package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation
import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.serialization.Serializable

@Serializable data class AppliedLocalSyncProposal(
    val proposalId: String,
    val operation: LocalWorkspaceContentOperation,
    val appliedDigest: String,
    val settledCheckpoint: LocalWorkspaceSyncCheckpoint? = null,
    val committed: CommittedLocalSyncTransaction? = null,
    val materializations: List<LocalAssetMaterialization> = emptyList(),
)

internal fun AppliedLocalSyncProposal.requireCanonicalTarget(proposal: LocalWorkspaceSyncProposal) {
    require(proposalId == proposal.id && !operation.restoreVersions)
    if (materializations.isEmpty()) require(operation.after == proposal.target)
    else {
        val first = materializations.first()
        val owner = WorkspaceSession(first.userId, first.clientId,
            WorkspaceMemberRole.Owner, operation.before.version, 0, operation.before)
        require(MaterializedAssetProjection(owner, proposal.binding, materializations).toLocal(proposal.target) == operation.after)
    }
}

data class LocalSyncApplyResult(val snapshot: LocalWorkspaceSyncSnapshot, val operation: LocalWorkspaceContentOperation)

/** Replaced local review, not an outgoing request or authorization to upload it. */
@Serializable data class SupersededLocalSyncProposal(
    val proposal: LocalWorkspaceSyncProposal,
    val applied: AppliedLocalSyncProposal,
    val checkpoint: LocalWorkspaceSyncCheckpoint,
) {
    init {
        require(proposal.binding == checkpoint.binding && proposal.target.id == checkpoint.baseline.id)
        applied.requireCanonicalTarget(proposal)
        require(syncWorkspaceDigest(applied.operation.before) == proposal.localDigest)
        require(applied.appliedDigest == syncWorkspaceDigest(applied.operation.appliedWorkspace()))
        require(applied.settledCheckpoint == null || applied.settledCheckpoint == checkpoint)
        require(applied.committed == null || applied.settledCheckpoint != null)
        applied.committed?.outgoing?.requireProposal(proposal)
        if (applied.settledCheckpoint == null) require(proposal.checkpointDigest == syncCheckpointDigest(checkpoint))
    }
}

/** Local media must exist before publication. Missing server originals require verified
 * materialization rather than inventing local references or using a thumbnail. */
internal suspend fun validateLocalSyncAssets(repository: CanvasWorkspaceRepository, snapshot: LocalWorkspaceSyncSnapshot) {
    val proposal = requireNotNull(snapshot.proposal)
    require(snapshot.proposalIsCurrent)
    validateLocalSyncTargetAssets(repository, snapshot.local, proposal.target)
}

internal suspend fun validateLocalSyncTargetAssets(repository: CanvasWorkspaceRepository, session: WorkspaceSession, target: Workspace) {
    val existing = session.workspace.objects.values.filterIsInstance<MediaNode>().map { it.assetId }.toSet()
    target.objects.values.filterIsInstance<MediaNode>().forEach { node ->
        require(node.assetId in existing) { "Server originals require materialization" }
        val asset = repository.getAsset(session, node.assetId)
        require(asset.status == AssetStatus.Ready && asset.ownerId == session.userId && asset.workspaceId == session.workspace.id)
        require(mediaKindForAssetMediaType(asset.mediaType) == node.mediaKind)
    }
}

internal fun LocalWorkspaceContentOperation.appliedWorkspace(): Workspace =
    (applyTo(before) as? OperationResult.Applied)?.workspace ?: error("Invalid reviewed local operation")

internal fun restoredLocalSyncHistory(snapshot: LocalWorkspaceSyncSnapshot): cg.creamgod.boarderless.domain.history.WorkspaceHistory {
    val applied = snapshot.applied?.takeIf { snapshot.appliedIsCurrent }
        ?: (snapshot.stopped.map { it.applied } + snapshot.superseded.map { it.applied })
            .filter { it.appliedDigest == syncWorkspaceDigest(snapshot.local.workspace) }
            .maxByOrNull { it.operation.appliedWorkspace().version }
    return if (applied != null) {
        cg.creamgod.boarderless.domain.history.WorkspaceHistory(snapshot.local.workspace,
            undoStack = listOf(applied.operation.inverse()))
    } else cg.creamgod.boarderless.domain.history.WorkspaceHistory(snapshot.local.workspace)
}
