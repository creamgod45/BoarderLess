@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext

class IosGifAnimation private constructor(private val data: Data, private val codec: Codec) : GifAnimation {
    private val guard = Mutex()
    private var released = false
    override val frameCount = codec.frameCount
    override val repetitionCount = codec.repetitionCount
    private val delays = List(frameCount) { if (frameCount > 1) gifFrameDelay(codec.getFrameInfo(it).duration) else 100L }

    override fun durationMs(frameIndex: Int) = delays[frameIndex]

    override suspend fun frame(frameIndex: Int): ImageBitmap = withContext(Dispatchers.Default) {
        guard.withLock {
            check(!released) { "GIF decoder is released" }
            require(frameIndex in 0 until frameCount)
            coroutineContext.ensureActive()
            val bitmap = Bitmap()
            try {
                bitmap.allocPixels(codec.imageInfo)
                codec.readPixels(bitmap, frameIndex)
                coroutineContext.ensureActive()
                Image.makeFromBitmap(bitmap).toComposeImageBitmap()
            } finally { bitmap.close() }
        }
    }

    override suspend fun release() = withContext(NonCancellable + Dispatchers.Default) {
        guard.withLock {
            if (!released) { released = true; codec.close(); data.close() }
        }
    }

    companion object {
        internal fun decode(bytes: ByteArray): IosGifAnimation {
            require(bytes.size <= AssetPreviewPolicy.MaxEncodedBytes)
            val data = Data.makeFromBytes(bytes)
            var codec: Codec? = null
            try {
                codec = Codec.makeFromData(data)
                require(codec.encodedImageFormat == EncodedImageFormat.GIF)
                AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                require(codec.frameCount in 1..10_000)
                return IosGifAnimation(data, codec)
            } catch (error: Throwable) { codec?.close(); data.close(); throw error }
        }
    }
}

suspend fun loadIosGif(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String): GifAnimation {
    val ticket = gateway.authorize(session, assetId)
    AssetPreviewPolicy.validate(ticket, session, assetId)
    require(ticket.asset.mediaType == "image/gif")
    var sink: IosFileAssetDownloadSink? = null
    var opened: IosGifAnimation? = null
    try {
        return withContext(Dispatchers.Default) {
            val destination = IosFileAssetDownloadSink.create("image/gif").also { sink = it }
            val authorized = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
            }
            val local = AssetDownloadCoordinator(authorized).download(session, assetId, destination)
            coroutineContext.ensureActive()
            val input = NSFileHandle.fileHandleForReadingAtPath(local.token) ?: error("Verified GIF is unavailable")
            val bytes = try {
                autoreleasepool {
                    val data = input.readDataOfLength(ticket.asset.byteSize.toULong())
                    check(data.length.toLong() == ticket.asset.byteSize)
                    ByteArray(data.length.toInt()).also { bytes ->
                        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    }
                }
            } finally { input.closeFile() }
            coroutineContext.ensureActive()
            IosGifAnimation.decode(bytes).also { opened = it; coroutineContext.ensureActive() }
        }
    } catch (error: Throwable) {
        opened?.release()
        throw error
    } finally {
        withContext(NonCancellable) {
            try { sink?.release() }
            catch (error: Throwable) { opened?.release(); throw error }
        }
    }
}
