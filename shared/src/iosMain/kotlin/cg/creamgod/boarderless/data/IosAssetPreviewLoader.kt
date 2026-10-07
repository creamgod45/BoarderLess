@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext

class IosAssetPreviewLoader(
    private val gateway: AssetDownloadGateway,
    private val cache: AuthorizedAssetPreviewCache<ImageBitmap> = bitmapPreviewCache(),
) {
    suspend fun load(
        session: WorkspaceSession,
        assetId: String,
    ): ImageBitmap = cache.load(gateway, session, assetId) { ticket -> loadAuthorized(session, assetId, ticket) }

    private suspend fun loadAuthorized(
        session: WorkspaceSession,
        assetId: String,
        ticket: AssetDownloadTicket,
    ): ImageBitmap {
        var sink: IosFileAssetDownloadSink? = null
        try {
            return withContext(Dispatchers.Default) {
                val destination = IosFileAssetDownloadSink.create(ticket.asset.mediaType).also { sink = it }
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
                val local = AssetDownloadCoordinator(authorizedGateway).download(session, assetId, destination)
                coroutineContext.ensureActive()
                val input = NSFileHandle.fileHandleForReadingAtPath(local.token) ?: error("Verified preview is unavailable")
                val bytes =
                    try {
                        val data = input.readDataOfLength(ticket.asset.byteSize.toULong())
                        check(data.length.toLong() == ticket.asset.byteSize)
                        ByteArray(data.length.toInt()).also { bytes ->
                            bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                        }
                    } finally {
                        input.closeFile()
                    }
                decodePreview(bytes).also { coroutineContext.ensureActive() }
            }
        } finally {
            withContext(NonCancellable) { sink?.release() }
        }
    }

    companion object {
        internal fun decodePreview(bytes: ByteArray): ImageBitmap {
            require(bytes.size <= AssetPreviewPolicy.MaxEncodedBytes)
            val data = Data.makeFromBytes(bytes)
            try {
                val codec = Codec.makeFromData(data)
                try {
                    AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                    val bitmap = codec.readPixels()
                    try {
                        return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                    } finally {
                        bitmap.close()
                    }
                } finally {
                    codec.close()
                }
            } finally {
                data.close()
            }
        }
    }
}
