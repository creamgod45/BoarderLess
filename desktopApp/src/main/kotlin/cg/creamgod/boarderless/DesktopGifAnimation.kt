package cg.creamgod.boarderless

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.nio.file.Files

internal class DesktopGifAnimation private constructor(
    private val data: Data,
    private val codec: Codec,
) : GifAnimation {
    private val guard = Mutex()
    private var released = false
    override val frameCount = codec.frameCount
    override val repetitionCount = codec.repetitionCount
    private val delays = List(frameCount) { if (frameCount > 1) gifFrameDelay(codec.getFrameInfo(it).duration) else 100L }

    override fun durationMs(frameIndex: Int): Long = delays[frameIndex]

    override suspend fun frame(frameIndex: Int): ImageBitmap =
        withContext(Dispatchers.IO) {
            guard.withLock {
                check(!released) { "GIF decoder is released" }
                require(frameIndex in 0 until frameCount)
                currentCoroutineContext().ensureActive()
                Bitmap().use { bitmap ->
                    bitmap.allocPixels(codec.imageInfo)
                    // No prior frame supplied: Skia reconstructs dependencies and disposal correctly.
                    codec.readPixels(bitmap, frameIndex)
                    currentCoroutineContext().ensureActive()
                    Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                }
            }
        }

    override suspend fun release() =
        withContext(NonCancellable + Dispatchers.IO) {
            guard.withLock {
                if (!released) {
                    released = true
                    codec.close()
                    data.close()
                }
            }
        }

    companion object {
        fun decode(bytes: ByteArray): DesktopGifAnimation {
            require(bytes.size <= AssetPreviewPolicy.MaxEncodedBytes)
            val data = Data.makeFromBytes(bytes)
            var codec: Codec? = null
            try {
                codec = Codec.makeFromData(data)
                require(codec.encodedImageFormat == EncodedImageFormat.GIF) { "Animation content is not GIF" }
                AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                require(codec.frameCount in 1..10_000) { "GIF has too many frames" }
                return DesktopGifAnimation(data, codec)
            } catch (error: Throwable) {
                codec?.close()
                data.close()
                throw error
            }
        }
    }
}

internal suspend fun loadDesktopGif(
    gateway: AssetDownloadGateway,
    session: WorkspaceSession,
    assetId: String,
): GifAnimation {
    val ticket = gateway.authorize(session, assetId)
    AssetPreviewPolicy.validate(ticket, session, assetId)
    require(ticket.asset.mediaType == "image/gif")
    var animation: DesktopGifAnimation? = null
    try {
        return withContext(Dispatchers.IO) {
            val directory = Files.createTempDirectory("boarderless-gif-")
            try {
                val sink = JvmFileAssetDownloadSink.create(directory.toString(), "gif", "image/gif")
                val authorized =
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
                val local = AssetDownloadCoordinator(authorized).download(session, assetId, sink)
                DesktopGifAnimation
                    .decode(
                        Files.readAllBytes(
                            java.nio.file.Paths
                                .get(local.token),
                        ),
                    ).also { animation = it }
            } finally {
                Files.newDirectoryStream(directory).use { it.forEach { file -> Files.deleteIfExists(file) } }
                Files.deleteIfExists(directory)
            }
        }
    } catch (error: Throwable) {
        animation?.release()
        throw error
    }
}
