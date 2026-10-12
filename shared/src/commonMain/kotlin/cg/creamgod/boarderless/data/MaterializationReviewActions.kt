package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun Workspace.materializationReferenceKinds(sourceId: String): Set<MediaKind> =
    objects.values.filterIsInstance<MediaNode>().flatMap { node ->
        listOfNotNull(node.mediaKind.takeIf { node.assetId == sourceId },
            MediaKind.Image.takeIf { node.thumbnailAssetId == sourceId })
    }.toSet()

internal fun Workspace.requireMaterializationReference(source: MaterializationSourceDescriptor) {
    val kinds = materializationReferenceKinds(source.id)
    require(kinds.isNotEmpty() && kinds.all { it == mediaKindForAssetMediaType(source.mediaType) })
}

/** Read metadata only. Caller still needs separate approval to save and then download. */
internal suspend fun inspectMaterializationSource(local: CanvasWorkspaceRepository, reviewed: LocalWorkspaceSyncSnapshot,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, sourceId: String,
    canContinue: () -> Boolean): WorkspaceAsset {
    fun active() { currentScopeCheck(canContinue) }
    currentCoroutineContext().ensureActive(); active()
    val (target, expectedRemote) = reviewed.materializationReview()
    require(target.materializationReferenceKinds(sourceId).isNotEmpty())
    require(server.syncServerScope == reviewed.checkpoint?.binding?.serverScope)
    require(local.localSyncStore!!.readSyncSnapshot(reviewed.local) == reviewed)
    val fresh = server.refresh(selected)
    currentCoroutineContext().ensureActive(); active(); require(fresh == expectedRemote)
    val asset = server.getAsset(fresh, sourceId)
    currentCoroutineContext().ensureActive(); active()
    require(asset.id == sourceId && asset.workspaceId == fresh.workspace.id)
    target.requireMaterializationReference(MaterializationSourceDescriptor.fromAsset(asset))
    require(server.refresh(fresh) == fresh)
    currentCoroutineContext().ensureActive(); active()
    require(local.localSyncStore!!.readSyncSnapshot(reviewed.local) == reviewed)
    return asset
}

internal suspend fun saveReviewedMaterialization(local: CanvasWorkspaceRepository, reviewed: LocalWorkspaceSyncSnapshot,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, mapping: LocalAssetMaterialization,
    canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    currentCoroutineContext().ensureActive(); currentScopeCheck(canContinue)
    val store = requireNotNull(local.localSyncStore)
    val current = store.readSyncSnapshot(reviewed.local)
    val same = current.materializations.singleOrNull { it.source.id == mapping.source.id }
    require(same == null || same == mapping)
    // Exact unknown local ACK can have saved this reservation already; no other edits are adopted.
    require(current.copy(materializations = reviewed.materializations) == reviewed)
    require(current.materializations == reviewed.materializations ||
        (mapping !in reviewed.materializations && current.materializations == reviewed.materializations + mapping))
    val asset = inspectMaterializationSource(local, current, server, selected, mapping.source.id, canContinue)
    require(MaterializationSourceDescriptor.fromAsset(asset) == mapping.source)
    currentCoroutineContext().ensureActive(); currentScopeCheck(canContinue)
    return store.stageSyncMaterialization(current.local, requireNotNull(reviewed.checkpoint), mapping)
}

private fun currentScopeCheck(canContinue: () -> Boolean) { check(canContinue()) { "Materialization review scope changed" } }
