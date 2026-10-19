package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.serialization.Serializable

/** Frozen original metadata, not a download ticket, credentials or current authorization. */
@Serializable data class MaterializationSourceDescriptor(
    val id: String,
    val workspaceId: WorkspaceId,
    val ownerId: String,
    val mediaType: String,
    val byteSize: Long,
    val checksum: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val createdAt: String,
) {
    init {
        listOf(id, workspaceId.value, ownerId, createdAt).forEach {
            require(it.isNotBlank() && it.length <= 256)
            it.encodeToByteArray(throwOnInvalidSequence = true)
        }
        require(mediaType == mediaType.trim().lowercase() && mediaKindForAssetMediaType(mediaType) != null)
        require(byteSize in 1..MaxWorkspaceAssetBytes && checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
        require(width == null || width > 0); require(height == null || height > 0)
        require(durationMs == null || durationMs >= 0)
    }
    companion object {
        fun fromAsset(asset: WorkspaceAsset): MaterializationSourceDescriptor {
            require(asset.status == AssetStatus.Ready && asset.rejectionReason == null)
            return MaterializationSourceDescriptor(asset.id, asset.workspaceId, asset.ownerId, asset.mediaType,
                asset.byteSize, asset.checksum, asset.width, asset.height, asset.durationMs, asset.createdAt)
        }
    }
}

/** Saved before local prepare/download. Identity is immutable; this record never marks bytes
 * Ready or authorizes a transfer. Original creator can differ from the authenticated reader. */
@Serializable data class LocalAssetMaterialization(
    val binding: LocalWorkspaceSyncBinding,
    val userId: String,
    val clientId: String,
    val workspaceId: WorkspaceId,
    val reviewId: String,
    val source: MaterializationSourceDescriptor,
    val localAssetId: String,
    val createdAt: String,
    /** Uploads keep the existing private descriptor; measured server metadata must not
     * rewrite local bytes/catalog/history. Downloads default to the server descriptor. */
    val localSource: MaterializationSourceDescriptor? = null,
) {
    init {
        require(source.workspaceId == binding.workspaceId && source.id != localAssetId)
        require(localAssetId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        listOf(userId, clientId, workspaceId.value, reviewId, createdAt).forEach {
            require(it.isNotBlank() && it.length <= 256)
            it.encodeToByteArray(throwOnInvalidSequence = true)
        }
        localSource?.let {
            require(it.id == localAssetId && it.workspaceId == workspaceId && it.ownerId == userId && it.createdAt == createdAt)
            require(it.mediaType == source.mediaType && it.byteSize == source.byteSize && it.checksum.lowercase() == source.checksum.lowercase())
        }
    }
    fun pendingAsset(): WorkspaceAsset = WorkspaceAsset(localAssetId, workspaceId, userId, source.mediaType,
        source.byteSize, source.checksum, (localSource ?: source).width, (localSource ?: source).height,
        (localSource ?: source).durationMs, AssetStatus.Pending, createdAt)
}

internal fun newLocalAssetMaterialization(snapshot: LocalWorkspaceSyncSnapshot, source: WorkspaceAsset,
    localAssetId: String, createdAt: String): LocalAssetMaterialization = LocalAssetMaterialization(
    requireNotNull(snapshot.checkpoint).binding, snapshot.local.userId, snapshot.local.clientId, snapshot.local.workspace.id,
    snapshot.rebaseProposal?.id ?: requireNotNull(snapshot.proposal).id,
    MaterializationSourceDescriptor.fromAsset(source), localAssetId, createdAt)
