package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*

internal data class MediaAssetLibraryEntry(
    val asset: WorkspaceAsset,
    val title: String,
) {
    val kind: MediaKind? get() = mediaKindForAssetMediaType(asset.mediaType)
    val rejectionReason: AssetRejectionReason? get() = asset.rejectionReason.takeIf { asset.status == AssetStatus.Rejected }
    val insertable: Boolean get() = asset.status == AssetStatus.Ready && kind != null && asset.byteSize <= MaxWorkspaceAssetBytes

    /** Never fetch a video original or oversized image just to display a library card. */
    val previewAssetId: String? get() =
        if (asset.status != AssetStatus.Ready) {
            null
        } else {
            asset.thumbnailAssetId?.takeUnless { it == asset.id }
                ?: asset.id.takeIf { kind != null && kind != MediaKind.Video && asset.byteSize <= AssetPreviewPolicy.MaxEncodedBytes }
        }
}

internal fun canRetryLibraryPreview(
    entry: MediaAssetLibraryEntry,
    loaderAvailable: Boolean,
    failed: Boolean,
    loading: Boolean,
): Boolean = loaderAvailable && failed && !loading && entry.previewAssetId != null

/** Derivatives can finish after insertion. Resolve display references from scoped ready metadata
 * without mutating the Node, and keep old-server Node references as a compatibility fallback.
 * Every returned ID still goes through the normal authorized, checksum-verified preview loader.
 */
internal fun mediaNodePreviewAssetId(
    node: MediaNode,
    asset: WorkspaceAsset?,
    workspaceId: WorkspaceId?,
): String? {
    if (asset == null || workspaceId == null || asset.workspaceId != workspaceId ||
        asset.id != node.assetId || asset.status != AssetStatus.Ready ||
        mediaKindForAssetMediaType(asset.mediaType) != node.mediaKind
    ) {
        return null
    }

    fun derivative(id: String?): String? = id?.takeIf { it.isNotBlank() && it != node.assetId }
    return derivative(asset.thumbnailAssetId) ?: derivative(node.thumbnailAssetId)
        ?: node.assetId.takeIf { node.mediaKind != MediaKind.Video }
}

internal fun canInsertMediaInSession(
    opened: WorkspaceSession,
    current: WorkspaceSession?,
    connectionFailed: Boolean,
): Boolean =
    !connectionFailed && current != null && current.canEditContent && current.userId == opened.userId &&
        current.clientId == opened.clientId && current.workspace.id == opened.workspace.id

/** Reading recovery metadata does not grant permission to edit or insert its asset. */
internal fun recoveredMediaAssetForSession(
    opened: WorkspaceSession,
    current: WorkspaceSession?,
    requestedId: String,
    asset: WorkspaceAsset,
): WorkspaceAsset? {
    if (current == null || current.userId != opened.userId || current.clientId != opened.clientId ||
        current.workspace.id != opened.workspace.id
    ) {
        return null
    }
    require(asset.id == requestedId && asset.workspaceId == opened.workspace.id) {
        "Recovery metadata does not match the requested workspace asset"
    }
    return asset
}

internal fun mediaAssetLibraryEntries(
    workspaceId: WorkspaceId,
    assets: Collection<WorkspaceAsset>,
    nodes: Collection<CanvasObject>,
    query: String = "",
    kind: MediaKind? = null,
): List<MediaAssetLibraryEntry> {
    val titles =
        nodes
            .filterIsInstance<MediaNode>()
            .filter { it.altText.isNotBlank() }
            .sortedBy { it.id.value }
            .associate { it.assetId to it.altText }
    val tokens =
        query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    return assets
        .filter { it.workspaceId == workspaceId }
        .distinctBy { it.id }
        .map { MediaAssetLibraryEntry(it, titles[it.id] ?: it.id) }
        .filter { entry ->
            (kind == null || entry.kind == kind) &&
                tokens.all {
                    "${entry.title} ${entry.asset.id} ${entry.asset.mediaType} ${entry.asset.status.token}".lowercase().contains(it)
                }
        }.sortedWith(compareByDescending<MediaAssetLibraryEntry> { it.asset.createdAt }.thenBy { it.asset.id })
}

internal fun mediaNodeFromReadyAsset(
    asset: WorkspaceAsset,
    workspaceId: WorkspaceId,
    nodeId: CanvasObjectId,
    center: Vec2,
    density: Float,
    zIndex: Long,
    altText: String,
    thumbnailAssetId: String? = asset.thumbnailAssetId,
    requestedAssetId: String = asset.id,
): MediaNode {
    require(asset.id == requestedAssetId && asset.workspaceId == workspaceId && asset.status == AssetStatus.Ready) {
        "Only the requested ready asset from this workspace can be inserted"
    }
    val kind = requireNotNull(mediaKindForAssetMediaType(asset.mediaType)) { "Unsupported media type" }
    require(asset.byteSize <= MaxWorkspaceAssetBytes) { "Media asset exceeds the supported size limit" }
    require(density.isFinite() && density > 0 && center.x.isFinite() && center.y.isFinite())
    val aspect = if (asset.width != null && asset.height != null) asset.width.toFloat() / asset.height.toFloat() else 16f / 9f
    val width = 320f * density
    val height = (width / aspect).coerceIn(80f * density, 360f * density)
    require(width.isFinite() && height.isFinite())
    return MediaNode(
        nodeId,
        zIndex = zIndex,
        transform =
            CanvasTransform(
                position = Vec2(center.x - width / 2, center.y - height / 2),
                size = CanvasSize(width, height),
            ),
        assetId = asset.id,
        mediaKind = kind,
        altText = altText,
        thumbnailAssetId = thumbnailAssetId?.takeUnless { it == asset.id },
    )
}
