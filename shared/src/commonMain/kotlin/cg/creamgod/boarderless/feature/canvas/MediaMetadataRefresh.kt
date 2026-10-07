package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.delay

/** Metadata changes need not advance the canvas operation sequence. No transfer or mutation. */
internal fun needsMediaMetadataFollowUp(
    workspaceId: WorkspaceId,
    assets: List<WorkspaceAsset>,
    nodes: Collection<MediaNode>,
): Boolean {
    val referenced = nodes.map { it.assetId }.toSet()
    return assets.any { asset ->
        asset.workspaceId == workspaceId && (
            asset.status == AssetStatus.Pending ||
                (
                    asset.status == AssetStatus.Ready && asset.id in referenced &&
                        (asset.thumbnailAssetId == null || asset.thumbnailAssetId == asset.id)
                )
        )
    }
}

/** A bounded window, including the initial read. False publication means a newer owner won. */
internal suspend fun refreshMediaMetadata(
    read: suspend () -> List<WorkspaceAsset>,
    publish: (List<WorkspaceAsset>) -> Boolean,
    needsFollowUp: (List<WorkspaceAsset>) -> Boolean,
) {
    repeat(10) { attempt ->
        val snapshot = awaitActiveMediaRead(read)
        if (!publish(snapshot) || !needsFollowUp(snapshot) || attempt == 9) return
        delay(3_000)
    }
}
