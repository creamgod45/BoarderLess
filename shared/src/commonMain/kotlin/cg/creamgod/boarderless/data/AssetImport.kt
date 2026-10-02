package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

const val MaxWorkspaceAssetBytes: Long = 200L * 1024L * 1024L
const val DefaultAssetUploadChunkBytes: Int = 1024 * 1024

private val SupportedAssetMediaTypes = mapOf(
    "image/png" to MediaKind.Image,
    "image/jpeg" to MediaKind.Image,
    "image/webp" to MediaKind.Image,
    "image/gif" to MediaKind.Gif,
    "video/mp4" to MediaKind.Video,
    "video/webm" to MediaKind.Video,
)

fun mediaKindForAssetMediaType(mediaType: String): MediaKind? = SupportedAssetMediaTypes[mediaType.trim().lowercase()]

/** Platform-owned source that can be uploaded without reading a 200 MB video into memory. */
interface AssetTransferSource {
    val displayName: String
    val mediaType: String
    val byteSize: Long
    val checksum: String
    val width: Int?
    val height: Int?
    val durationMs: Long?

    /** Returns at most [maximumBytes] beginning at [offset], or an empty array at EOF. */
    suspend fun readChunk(offset: Long, maximumBytes: Int = DefaultAssetUploadChunkBytes): ByteArray

    /** Releases device-local picker snapshots after success, failure, or cancellation. */
    suspend fun release() {}
}

/** Allows a browser to send its native File directly without a full Kotlin ByteArray copy. */
interface DirectAssetUploadSource : AssetTransferSource {
    suspend fun uploadDirect(ticket: AssetUploadTicket, onProgress: (Long) -> Unit)
}

data class AssetUploadTicket(
    val asset: WorkspaceAsset,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String> = emptyMap(),
) {
    init {
        require(uploadUrl.isNotBlank()) { "asset upload URL must not be blank" }
    }
}

data class ImportedMedia(
    val asset: WorkspaceAsset,
    val mediaKind: MediaKind,
    val thumbnailAssetId: String? = null,
)

sealed interface AssetImportStage {
    data object Validating : AssetImportStage
    data object Preparing : AssetImportStage
    data class Uploading(val uploadedBytes: Long, val totalBytes: Long) : AssetImportStage
    data object Confirming : AssetImportStage
    data class Processing(val status: AssetStatus) : AssetImportStage
    data class Ready(val imported: ImportedMedia) : AssetImportStage
}

enum class AssetImportIssue {
    BlankName,
    UnsupportedMediaType,
    InvalidByteSize,
    AssetTooLarge,
    InvalidChecksum,
    InvalidDimensions,
    InvalidDuration,
    TruncatedSource,
    Rejected,
    Missing,
    NotReady,
}

class AssetImportException(
    val issue: AssetImportIssue,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Server/object-storage boundary. Implementations own signed-URL details and never expose them to
 * Workspace operations or MediaNode properties.
 */
interface AssetTransferGateway {
    suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket

    suspend fun upload(
        ticket: AssetUploadTicket,
        source: AssetTransferSource,
        onProgress: (uploadedBytes: Long) -> Unit,
    )

    suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset

    suspend fun awaitReady(
        session: WorkspaceSession,
        assetId: String,
        onStatus: (AssetStatus) -> Unit,
    ): WorkspaceAsset

    suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String?

    /** Best-effort cleanup for a prepared upload that did not become ready. */
    suspend fun abandon(session: WorkspaceSession, assetId: String)
}

class AssetImportCoordinator(
    private val gateway: AssetTransferGateway,
) {
    suspend fun import(
        session: WorkspaceSession,
        source: AssetTransferSource,
        onStage: (AssetImportStage) -> Unit = {},
    ): ImportedMedia {
        onStage(AssetImportStage.Validating)
        val mediaKind = validateAssetTransferSource(source)
        onStage(AssetImportStage.Preparing)
        var ticket: AssetUploadTicket? = null
        var becameReady = false
        try {
            ticket = gateway.prepare(session, source)
            requireTicketMatchesSource(ticket, session, source)
            var latestProgress = 0L
            gateway.upload(ticket, source) { uploadedBytes ->
                val normalized = uploadedBytes.coerceIn(latestProgress, source.byteSize)
                latestProgress = normalized
                onStage(AssetImportStage.Uploading(normalized, source.byteSize))
            }
            if (latestProgress != source.byteSize) {
                throw AssetImportException(
                    AssetImportIssue.TruncatedSource,
                    "Asset upload ended at $latestProgress of ${source.byteSize} bytes",
                )
            }
            onStage(AssetImportStage.Confirming)
            var asset = gateway.confirm(session, ticket)
            validateReturnedAsset(asset, session, source)
            requireSameAssetId(asset, ticket.asset.id)
            if (asset.status == AssetStatus.Pending) {
                onStage(AssetImportStage.Processing(asset.status))
                asset = gateway.awaitReady(session, asset.id) { status ->
                    onStage(AssetImportStage.Processing(status))
                }
                validateReturnedAsset(asset, session, source)
                requireSameAssetId(asset, ticket.asset.id)
            }
            when (asset.status) {
                AssetStatus.Ready -> Unit
                AssetStatus.Rejected -> throw AssetImportException(
                    AssetImportIssue.Rejected,
                    "Asset ${asset.id} was rejected by the server",
                )
                AssetStatus.Missing -> throw AssetImportException(
                    AssetImportIssue.Missing,
                    "Asset ${asset.id} is missing from object storage",
                )
                AssetStatus.Pending -> throw AssetImportException(
                    AssetImportIssue.NotReady,
                    "Asset ${asset.id} did not become ready",
                )
            }
            becameReady = true
            val imported = ImportedMedia(
                asset = asset,
                mediaKind = mediaKind,
                // Thumbnail metadata is recoverable on the next asset refresh. A transient
                // derivative lookup failure must not delete or orphan an already-ready original.
                thumbnailAssetId = try {
                    gateway.thumbnailAssetId(session, asset.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                },
            )
            onStage(AssetImportStage.Ready(imported))
            return imported
        } catch (error: Throwable) {
            ticket?.takeUnless { becameReady }?.let { prepared ->
                withContext(NonCancellable) {
                    runCatching { gateway.abandon(session, prepared.asset.id) }
                }
            }
            if (error is CancellationException) throw error
            throw error
        }
    }
}

private fun requireSameAssetId(asset: WorkspaceAsset, expectedId: String) {
    if (asset.id != expectedId) {
        throw AssetImportException(
            AssetImportIssue.NotReady,
            "Server returned a different asset than the prepared upload",
        )
    }
}

fun validateAssetTransferSource(source: AssetTransferSource): MediaKind {
    if (source.displayName.isBlank()) {
        throw AssetImportException(AssetImportIssue.BlankName, "Asset display name must not be blank")
    }
    val normalizedMediaType = source.mediaType.trim().lowercase()
    val kind = mediaKindForAssetMediaType(normalizedMediaType)
        ?: throw AssetImportException(
            AssetImportIssue.UnsupportedMediaType,
            "Media type '$normalizedMediaType' is not supported",
        )
    if (source.byteSize <= 0) {
        throw AssetImportException(AssetImportIssue.InvalidByteSize, "Asset byte size must be positive")
    }
    if (source.byteSize > MaxWorkspaceAssetBytes) {
        throw AssetImportException(
            AssetImportIssue.AssetTooLarge,
            "Asset exceeds the $MaxWorkspaceAssetBytes byte limit",
        )
    }
    if (source.checksum.isBlank() || source.checksum.length > 200) {
        throw AssetImportException(AssetImportIssue.InvalidChecksum, "Asset checksum is invalid")
    }
    if (source.width?.let { it <= 0 } == true || source.height?.let { it <= 0 } == true) {
        throw AssetImportException(AssetImportIssue.InvalidDimensions, "Asset dimensions must be positive")
    }
    if (source.durationMs?.let { it < 0 } == true) {
        throw AssetImportException(AssetImportIssue.InvalidDuration, "Asset duration must not be negative")
    }
    return kind
}

private fun requireTicketMatchesSource(
    ticket: AssetUploadTicket,
    session: WorkspaceSession,
    source: AssetTransferSource,
) {
    validateReturnedAsset(ticket.asset, session, source)
    if (ticket.asset.status != AssetStatus.Pending) {
        throw AssetImportException(
            AssetImportIssue.NotReady,
            "Prepared asset ${ticket.asset.id} must begin in pending state",
        )
    }
}

private fun validateReturnedAsset(
    asset: WorkspaceAsset,
    session: WorkspaceSession,
    source: AssetTransferSource,
) {
    if (asset.workspaceId != session.workspace.id || asset.mediaType != source.mediaType.trim().lowercase() ||
        asset.byteSize != source.byteSize || asset.checksum != source.checksum
    ) {
        throw AssetImportException(
            AssetImportIssue.NotReady,
            "Server asset metadata does not match the selected source",
        )
    }
}
