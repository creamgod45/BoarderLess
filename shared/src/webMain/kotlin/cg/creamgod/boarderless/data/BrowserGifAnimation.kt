package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.coroutines.coroutineContext

class BrowserGifAnimation private constructor(private val data: Data, private val codec: Codec) : GifAnimation {
    private val guard = Mutex()
    private var released = false
    override val frameCount = codec.frameCount
    override val repetitionCount = codec.repetitionCount
    private val delays = List(frameCount) { if (frameCount > 1) gifFrameDelay(codec.getFrameInfo(it).duration) else 100L }

    override fun durationMs(frameIndex: Int) = delays[frameIndex]

    override suspend fun frame(frameIndex: Int): ImageBitmap = guard.withLock {
        check(!released) { "GIF decoder is released" }
        require(frameIndex in 0 until frameCount)
        yield()
        coroutineContext.ensureActive()
        val bitmap = Bitmap()
        try {
            bitmap.allocPixels(codec.imageInfo)
            codec.readPixels(bitmap, frameIndex)
            val result = Image.makeFromBitmap(bitmap).toComposeImageBitmap()
            yield()
            coroutineContext.ensureActive()
            result
        } finally { bitmap.close() }
    }

    override suspend fun release() = withContext(NonCancellable) {
        guard.withLock {
            if (!released) { released = true; codec.close(); data.close() }
        }
    }

    companion object {
        internal fun decode(bytes: ByteArray): BrowserGifAnimation {
            require(bytes.size <= AssetPreviewPolicy.MaxEncodedBytes)
            val data = Data.makeFromBytes(bytes)
            var codec: Codec? = null
            try {
                codec = Codec.makeFromData(data)
                require(codec.encodedImageFormat == EncodedImageFormat.GIF)
                AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                require(codec.frameCount in 1..10_000)
                return BrowserGifAnimation(data, codec)
            } catch (error: Throwable) { codec?.close(); data.close(); throw error }
        }
    }
}

suspend fun loadBrowserGif(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String): GifAnimation {
    val ticket = gateway.authorize(session, assetId)
    AssetPreviewPolicy.validate(ticket, session, assetId)
    require(ticket.asset.mediaType == "image/gif")
    val sink = BrowserAssetDownloadSink("image/gif")
    var opened: BrowserGifAnimation? = null
    try {
        val authorized = object : AssetDownloadGateway {
            override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
            override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
        }
        AssetDownloadCoordinator(authorized).download(session, assetId, sink)
        val bytes = sink.takeVerifiedBytes()
        awaitBrowserImageDecoder()
        coroutineContext.ensureActive()
        return BrowserGifAnimation.decode(bytes).also { opened = it; coroutineContext.ensureActive() }
    } catch (error: Throwable) { opened?.release(); throw error }
    finally { sink.release() }
}
