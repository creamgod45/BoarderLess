package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId

enum class AssetStatus(val token: String) {
    Pending("pending"),
    Ready("ready"),
    Rejected("rejected"),
    Missing("missing"),
    ;

    companion object {
        fun fromToken(token: String): AssetStatus? = entries.firstOrNull { it.token == token }
    }
}

data class WorkspaceAsset(
    val id: String,
    val workspaceId: WorkspaceId,
    val ownerId: String,
    val mediaType: String,
    val byteSize: Long,
    val checksum: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val status: AssetStatus,
    val createdAt: String,
) {
    init {
        require(id.isNotBlank()) { "asset id must not be blank" }
        require(ownerId.isNotBlank()) { "asset owner id must not be blank" }
        require(mediaType.isNotBlank()) { "asset media type must not be blank" }
        require(byteSize > 0) { "asset byte size must be positive" }
        require(checksum.isNotBlank()) { "asset checksum must not be blank" }
        require(width == null || width > 0) { "asset width must be positive" }
        require(height == null || height > 0) { "asset height must be positive" }
        require(durationMs == null || durationMs >= 0) { "asset duration must not be negative" }
        require(createdAt.isNotBlank()) { "asset creation time must not be blank" }
    }
}

/** Read-only metadata supported by the current backend.
 *
 * Binary upload, completion, derivatives, and authorized download are intentionally absent until
 * the server publishes those contracts. This prevents the app from treating a pending metadata
 * row as a completed upload.
 */
interface AssetRepository {
    suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset>

    suspend fun getAsset(session: WorkspaceSession, assetId: String): WorkspaceAsset
}
