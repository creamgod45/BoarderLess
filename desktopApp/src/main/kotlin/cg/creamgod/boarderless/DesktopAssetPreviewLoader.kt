package cg.creamgod.boarderless

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cg.creamgod.boarderless.data.AssetDownloadCoordinator
import cg.creamgod.boarderless.data.AssetDownloadGateway
import cg.creamgod.boarderless.data.AssetDownloadTicket
import cg.creamgod.boarderless.data.AssetPreviewPolicy
import cg.creamgod.boarderless.data.AuthorizedAssetPreviewCache
import cg.creamgod.boarderless.data.bitmapPreviewCache
import cg.creamgod.boarderless.data.JvmFileAssetDownloadSink
import cg.creamgod.boarderless.data.WorkspaceSession
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image

/** Short-lived verified files; no signed URLs or cross-account persistent cache. */
internal class DesktopAssetPreviewLoader(
    private val gateway: AssetDownloadGateway,
    private val cacheRoot: Path? = null,
    private val cache: AuthorizedAssetPreviewCache<ImageBitmap> = bitmapPreviewCache(),
) {
    private val downloads = Semaphore(2)

    suspend fun load(session: WorkspaceSession, assetId: String): ImageBitmap = downloads.withPermit {
        cache.load(gateway, session, assetId) { ticket -> withContext(Dispatchers.IO) {
            val directory = if (cacheRoot == null) Files.createTempDirectory("boarderless-preview-")
                else Files.createTempDirectory(cacheRoot, "preview-")
            try {
                val sink = JvmFileAssetDownloadSink.create(directory.toString(), "preview", ticket.asset.mediaType)
                // Reuse this authorization instead of requesting a second, potentially different ticket.
                val authorizedGateway = object : AssetDownloadGateway {
                    override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) =
                        gateway.download(ticket, onChunk)
                }
                val local = AssetDownloadCoordinator(authorizedGateway).download(session, assetId, sink)
                currentCoroutineContext().ensureActive()
                val bytes = Files.readAllBytes(java.nio.file.Paths.get(local.token))
                decodePreview(bytes).also { currentCoroutineContext().ensureActive() }
            } finally {
                // This directory was created by this load; it contains only its validated file/part.
                Files.newDirectoryStream(directory).use { entries -> entries.forEach { Files.deleteIfExists(it) } }
                Files.deleteIfExists(directory)
            }
        } }
    }

    companion object {
        const val MaxPreviewEncodedBytes = AssetPreviewPolicy.MaxEncodedBytes
        const val MaxPreviewPixels = AssetPreviewPolicy.MaxPixels

        internal fun decodePreview(bytes: ByteArray): ImageBitmap {
            require(bytes.size <= MaxPreviewEncodedBytes) { "Preview file is too large" }
            return Data.makeFromBytes(bytes).use { data ->
                Codec.makeFromData(data).use { codec ->
                    val size = codec.size
                    AssetPreviewPolicy.validateDimensions(size.x, size.y)
                    // A checked, eagerly decoded bitmap prevents corrupted lazy images reaching the UI.
                    codec.readPixels().use { bitmap -> Image.makeFromBitmap(bitmap).toComposeImageBitmap() }
                }
            }
        }
    }
}
