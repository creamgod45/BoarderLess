package cg.creamgod.boarderless.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AndroidAssetPreviewLoader(
    private val cacheDirectory: File,
) {
    private val downloads = Semaphore(2)
    private val cache = bitmapPreviewCache()

    suspend fun clear() = cache.clear()

    suspend fun load(
        gateway: AssetDownloadGateway,
        session: WorkspaceSession,
        assetId: String,
    ): ImageBitmap =
        downloads.withPermit {
            cache.load(gateway, session, assetId) { ticket ->
                withContext(Dispatchers.IO) {
                    val directory = File(cacheDirectory, "asset-preview-${UUID.randomUUID()}")
                    check(directory.mkdir()) { "Cannot create private preview directory" }
                    try {
                        val sink = AndroidFileAssetDownloadSink.create(directory, ticket.asset.mediaType)
                        val authorizedGateway =
                            object : AssetDownloadGateway {
                                override suspend fun authorize(
                                    session: WorkspaceSession,
                                    assetId: String,
                                ) = ticket

                                override suspend fun download(
                                    ticket: AssetDownloadTicket,
                                    onChunk: suspend (ByteArray) -> Unit,
                                ) = gateway.download(ticket, onChunk)
                            }
                        val local = AssetDownloadCoordinator(authorizedGateway).download(session, assetId, sink)
                        decodePreview(local.token)
                    } finally {
                        // Only this call's randomly named private directory, never the cache root.
                        directory.listFiles().orEmpty().forEach { check(it.delete()) { "Cannot clear preview file" } }
                        check(directory.delete()) { "Cannot clear preview directory" }
                    }
                }
            }
        }

    private suspend fun decodePreview(path: String): ImageBitmap {
        currentCoroutineContext().ensureActive()
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
        BitmapFactory.decodeFile(path, bounds)
        AssetPreviewPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
        val options =
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = AssetPreviewPolicy.sampleSize(bounds.outWidth, bounds.outHeight, MaxPreviewEdge)
            }
        val bitmap = checkNotNull(BitmapFactory.decodeFile(path, options)) { "Cannot decode verified preview" }
        try {
            currentCoroutineContext().ensureActive()
            AssetPreviewPolicy.validateDimensions(bitmap.width, bitmap.height)
            check(bitmap.width <= MaxPreviewEdge && bitmap.height <= MaxPreviewEdge) { "Decoder did not honor the preview sample limit" }
            return bitmap.asImageBitmap()
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    companion object {
        const val MaxPreviewEdge = 1024

        /** Decode an ephemeral provider still without creating a cache file. */
        suspend fun decodeBytes(bytes: ByteArray): ImageBitmap {
            require(bytes.isNotEmpty() && bytes.size <= AssetPreviewPolicy.MaxEncodedBytes)
            currentCoroutineContext().ensureActive()
            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    inScaled = false
                }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            AssetPreviewPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
            val options =
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = AssetPreviewPolicy.sampleSize(bounds.outWidth, bounds.outHeight, MaxPreviewEdge)
                }
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            try {
                currentCoroutineContext().ensureActive()
                AssetPreviewPolicy.validateDimensions(bitmap.width, bitmap.height)
                check(bitmap.width <= MaxPreviewEdge && bitmap.height <= MaxPreviewEdge)
                return bitmap.asImageBitmap()
            } catch (failed: Throwable) {
                bitmap.recycle()
                throw failed
            }
        }
    }
}
