package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Ephemeral exact review result, not a durable Ready flag or permission to publish later. */
class VerifiedMaterializedTarget internal constructor(val reviewed: LocalWorkspaceSyncSnapshot, val localTarget: Workspace)

internal fun reviewedLocalApplyTarget(reviewed: LocalWorkspaceSyncSnapshot): Workspace {
    if (reviewed.rebaseProposal != null) require(reviewed.rebaseIsCurrent)
    else require(reviewed.proposalIsCurrent && reviewed.applied == null)
    val proposal = requireNotNull(reviewed.proposal)
    val target = reviewed.rebaseProposal?.target ?: proposal.target
    return if (reviewed.materializations.isEmpty()) target
        else MaterializedAssetProjection(reviewed.local, proposal.binding, reviewed.materializations).toLocal(target)
}

internal suspend fun verifyMaterializedRebaseTargetOrRecovery(local: CanvasWorkspaceRepository,
    reviewed: LocalWorkspaceSyncSnapshot, operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, gateway: AssetDownloadGateway,
    canContinue: () -> Boolean): VerifiedMaterializedTarget {
    currentCoroutineContext().ensureActive(); check(canContinue())
    val rebase = requireNotNull(reviewed.rebaseProposal)
    require(operation.before == rebase.localBefore && !operation.restoreVersions)
    val current = requireNotNull(local.localSyncStore).readSyncSnapshot(reviewed.local)
    currentCoroutineContext().ensureActive(); check(canContinue())
    current.rebases.singleOrNull { it.reviewed.id == rebase.id }?.let {
        require(it == AppliedLocalSyncRebase(rebase, operation, reviewed.materializations) && current.local.workspace == it.workspace)
        require(current.materializations == reviewed.materializations && current.checkpoint == it.checkpoint)
        require(current.proposal == it.continuation(current.local) && current.rebaseProposal == null && current.outgoing == null)
        require(server.syncServerScope == rebase.context.original.binding.serverScope && gateway.downloadServerScope == server.syncServerScope)
        rebase.context.original.binding.requireRemote(selected)
        return VerifiedMaterializedTarget(reviewed, operation.after)
    }
    val verified = verifyMaterializedTarget(local, reviewed, server, selected, gateway, canContinue)
    require(verified.localTarget == operation.after)
    return verified
}

/** On an unknown ACK only the exact already-published result can be confirmed again.
 * Otherwise fresh verification is mandatory; no remote-original download occurs here. */
internal suspend fun verifyMaterializedApplyOrRecovery(local: CanvasWorkspaceRepository,
    reviewed: LocalWorkspaceSyncSnapshot, operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, gateway: AssetDownloadGateway,
    canContinue: () -> Boolean): VerifiedMaterializedTarget {
    currentCoroutineContext().ensureActive(); check(canContinue())
    require(operation.before == reviewed.local.workspace && operation.after == reviewedLocalApplyTarget(reviewed) && !operation.restoreVersions)
    val current = requireNotNull(local.localSyncStore).readSyncSnapshot(reviewed.local)
    currentCoroutineContext().ensureActive(); check(canContinue())
    if (current.applied != null) {
        val applied = AppliedLocalSyncProposal(requireNotNull(reviewed.proposal).id, operation,
            syncWorkspaceDigest(operation.appliedWorkspace()), materializations = reviewed.materializations)
        val expected = reviewed.copy(local = reviewed.local.copy(workspace = operation.appliedWorkspace(),
            workspaceVersion = operation.appliedWorkspace().version), applied = applied)
        require(current == expected) { "Another publication or review must be preserved" }
        require(server.syncServerScope == reviewed.checkpoint?.binding?.serverScope && gateway.downloadServerScope == server.syncServerScope)
        reviewed.checkpoint!!.binding.requireRemote(selected)
        return VerifiedMaterializedTarget(reviewed, operation.after)
    }
    val result = verifyMaterializedTarget(local, reviewed, server, selected, gateway, canContinue)
    require(result.localTarget == operation.after)
    return result
}

/** Explicit pre-apply verification only. No original transfer, catalog writes or canvas writes.
 * Publication must still atomically fence this exact snapshot and retain canonical history. */
internal suspend fun verifyMaterializedTarget(local: CanvasWorkspaceRepository, reviewed: LocalWorkspaceSyncSnapshot,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, remoteGateway: AssetDownloadGateway,
    canContinue: () -> Boolean): VerifiedMaterializedTarget {
    val context = currentCoroutineContext()
    fun active() { context.ensureActive(); check(canContinue()) }
    active(); require(local.isLocalOnly && reviewed.local.canEditContent)
    require(!reviewed.requiresAssetIdentityReview) { "Uploaded identity requires a new canonical review" }
    val checkpoint = requireNotNull(reviewed.checkpoint)
    if (reviewed.rebaseProposal != null) require(reviewed.rebaseIsCurrent)
    else require(reviewed.proposalIsCurrent && reviewed.applied == null && reviewed.outgoing == null)
    val (target, remote) = reviewed.materializationReview()
    checkpoint.binding.requireRemote(selected)
    require(server.syncServerScope == checkpoint.binding.serverScope && remoteGateway.downloadServerScope == checkpoint.binding.serverScope)
    val store = requireNotNull(local.localSyncStore)
    require(store.readSyncSnapshot(reviewed.local) == reviewed)
    require(server.refresh(selected) == remote); active()
    val projection = MaterializedAssetProjection(reviewed.local, checkpoint.binding, reviewed.materializations)
    val sources = reviewed.materializations.associateBy { it.source.id }
    val existing = reviewed.local.workspace.objects.values.filterIsInstance<MediaNode>().flatMap {
        listOfNotNull(it.assetId, it.thumbnailAssetId)
    }.toSet()
    val references = target.objects.values.filterIsInstance<MediaNode>().flatMap {
        listOfNotNull(it.assetId, it.thumbnailAssetId)
    }.toSet()
    val needed = references.mapNotNull { id -> sources[id].also { require(it != null || id in existing) { "Original not materialized" } } }
    val verifiedLocalIds = needed.mapTo(mutableSetOf()) { it.localAssetId }
    val localGateway = local.localAssetGateway as? AssetDownloadGateway ?: error("Local originals unavailable")
    for (mapping in needed) {
        active()
        val ticket = remoteGateway.authorize(remote, mapping.source.id)
        active(); require(MaterializationSourceDescriptor.fromAsset(ticket.asset) == mapping.source)
        verifyMaterializedLocalOriginal(localGateway, reviewed.local, mapping, canContinue)
    }
    val localTarget = projection.toLocal(target)
    // Metadata for existing references still has to match node kind and local ownership.
    for (node in localTarget.objects.values.filterIsInstance<MediaNode>()) {
        for ((id, kind) in listOfNotNull(node.assetId to node.mediaKind, node.thumbnailAssetId?.let { it to MediaKind.Image })) {
            if (id in verifiedLocalIds) continue // Already exact descriptor + original verified above.
            val asset = local.getAsset(reviewed.local, id)
            active(); require(asset.status == AssetStatus.Ready && asset.workspaceId == reviewed.local.workspace.id && asset.ownerId == reviewed.local.userId)
            require(mediaKindForAssetMediaType(asset.mediaType) == kind)
        }
    }
    for (mapping in needed) {
        active(); require(MaterializationSourceDescriptor.fromAsset(remoteGateway.authorize(remote, mapping.source.id).asset) == mapping.source)
    }
    active(); require(server.refresh(remote) == remote); active()
    require(store.readSyncSnapshot(reviewed.local) == reviewed); active()
    return VerifiedMaterializedTarget(reviewed, localTarget)
}
