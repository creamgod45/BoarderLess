package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.MaxWorkspaceAssetBytes
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.mediaKindForAssetMediaType
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.domain.model.MediaKind

/** All-or-nothing reuse gate before paste allocates IDs or mutates history.
 * A syntactically valid asset ID is not proof of destination scope or availability.
 * Server operation ACL/ready checks remain required; no client-side blob transfer is implied.
 */
internal fun selectionMediaAvailable(
    payload: ClipboardPayload,
    workspaceId: WorkspaceId?,
    assets: Map<String, WorkspaceAsset>,
): Boolean {
    if (payload.media.isEmpty()) return true
    if (workspaceId == null) return false
    fun readyAsset(id: String): WorkspaceAsset? = assets[id]?.takeIf {
        it.id == id && it.workspaceId == workspaceId && it.status == AssetStatus.Ready &&
            it.byteSize <= MaxWorkspaceAssetBytes
    }
    return payload.media.all { media ->
        val asset = readyAsset(media.assetId)
        val thumbnailAvailable = media.thumbnailAssetId?.let { id ->
            val thumbnail = readyAsset(id)
            // A poster is an independently authorized reference, not a video original.
            thumbnail != null && mediaKindForAssetMediaType(thumbnail.mediaType) in
                setOf(MediaKind.Image, MediaKind.Gif)
        } ?: true
        asset != null && mediaKindForAssetMediaType(asset.mediaType)?.token == media.mediaKind &&
            thumbnailAvailable
    }
}
