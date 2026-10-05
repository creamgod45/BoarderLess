package cg.creamgod.boarderless.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeader
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import cg.creamgod.boarderless.data.remote.loadGiphyAnimation

suspend fun loadAndroidGiphyAnimation(url: String): GifAnimation =
    loadGiphyAnimation(url) { AndroidGifAnimation.decode(it) }

/** Decoder buffers are private; returned frames are never reused or recycled by this decoder. */
class AndroidGifAnimation private constructor(
    private var bytes: ByteArray?,
    private var header: GifHeader?,
) : GifAnimation {
    private val guard = Mutex()
    private var decoder: StandardGifDecoder? = newDecoder()
    override val frameCount = header!!.numFrames
    override val repetitionCount = decoder!!.totalIterationCount.let { if (it == 0) -1 else it - 1 }
    private val delays = List(frameCount) { gifFrameDelay(decoder!!.getDelay(it)) }
    override fun durationMs(frameIndex: Int) = delays[frameIndex]

    private fun newDecoder() = StandardGifDecoder(provider, checkNotNull(header), ByteBuffer.wrap(checkNotNull(bytes)))

    override suspend fun frame(frameIndex: Int): ImageBitmap = withContext(Dispatchers.Default) {
        guard.withLock {
            var active = checkNotNull(decoder) { "GIF decoder is released" }
            require(frameIndex in 0 until frameCount)
            currentCoroutineContext().ensureActive()
            // Backward/random access must reconstruct disposal history from frame zero.
            if (frameIndex <= active.currentFrameIndex) {
                active.clear()
                decoder = null
                active = newDecoder().also { decoder = it }
            }
            var result: Bitmap? = null
            try {
                while (active.currentFrameIndex < frameIndex) {
                    currentCoroutineContext().ensureActive()
                    result?.recycle()
                    result = null
                    active.advance()
                    result = checkNotNull(active.nextFrame) { "Cannot decode GIF frame" }
                    check(active.status == GifDecoder.STATUS_OK) { "Incomplete or corrupt GIF frame" }
                }
                currentCoroutineContext().ensureActive()
                checkNotNull(result).asImageBitmap().also { result = null }
            } finally { result?.recycle() }
        }
    }

    override suspend fun release() = withContext(NonCancellable + Dispatchers.Default) {
        guard.withLock {
            decoder?.clear()
            decoder = null
            bytes = null
            header = null
        }
    }

    companion object {
        private val provider = object : GifDecoder.BitmapProvider {
            override fun obtain(width: Int, height: Int, config: Bitmap.Config) = Bitmap.createBitmap(width, height, config)
            override fun release(bitmap: Bitmap) = bitmap.recycle()
            override fun obtainByteArray(size: Int) = ByteArray(size)
            override fun release(bytes: ByteArray) = Unit
            override fun obtainIntArray(size: Int) = IntArray(size)
            override fun release(array: IntArray) = Unit
        }

        internal fun decode(bytes: ByteArray): AndroidGifAnimation {
            validateAndroidGifStructure(bytes)
            val parser = GifHeaderParser()
            val header = try { parser.setData(bytes).parseHeader() } finally { parser.clear() }
            require(header.status == GifDecoder.STATUS_OK && header.numFrames in 1..10_000)
            AssetPreviewPolicy.validateDimensions(header.width, header.height)
            return AndroidGifAnimation(bytes, header)
        }
    }
}

/** Reject oversized frame rectangles before Glide allocates their LZW pixel buffers. */
internal fun validateAndroidGifStructure(bytes: ByteArray) {
    require(bytes.size in 14..AssetPreviewPolicy.MaxEncodedBytes.toInt())
    var offset = 0
    fun take() = bytes.getOrNull(offset++)?.toInt()?.and(255) ?: error("Truncated GIF")
    fun short(): Int = take() or (take() shl 8)
    fun skip(count: Int) { require(count >= 0 && count <= bytes.size - offset); offset += count }
    fun blocks() { while (true) { val count = take(); if (count == 0) break; skip(count) } }
    val signature = bytes.copyOfRange(0, 6).decodeToString()
    require(signature == "GIF87a" || signature == "GIF89a")
    offset = 6
    AssetPreviewPolicy.validateDimensions(short(), short())
    val packed = take()
    skip(2)
    if (packed and 128 != 0) skip(3 * (1 shl ((packed and 7) + 1)))
    var frames = 0
    while (true) {
        when (take()) {
            0x21 -> { take(); blocks() }
            0x2c -> {
                short(); short()
                AssetPreviewPolicy.validateDimensions(short(), short())
                require(++frames <= 10_000)
                val flags = take()
                if (flags and 128 != 0) skip(3 * (1 shl ((flags and 7) + 1)))
                require(take() in 2..8) { "Invalid GIF LZW code size" }
                blocks()
            }
            0x3b -> { require(frames > 0); return }
            else -> error("Invalid GIF block")
        }
    }
}

suspend fun loadAndroidGif(
    gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String, cacheDirectory: File,
): GifAnimation {
    val ticket = gateway.authorize(session, assetId)
    AssetPreviewPolicy.validate(ticket, session, assetId)
    require(ticket.asset.mediaType == "image/gif")
    var directory: File? = null
    var opened: AndroidGifAnimation? = null
    try {
        return withContext(Dispatchers.IO) {
            val target = File(cacheDirectory, "asset-gif-${UUID.randomUUID()}").also { directory = it }
            check(target.mkdir())
            val sink = AndroidFileAssetDownloadSink.create(target, "image/gif")
            val authorized = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
            }
            val local = AssetDownloadCoordinator(authorized).download(session, assetId, sink)
            currentCoroutineContext().ensureActive()
            val bytes = File(local.token).readBytes()
            check(bytes.size.toLong() == ticket.asset.byteSize)
            currentCoroutineContext().ensureActive()
            AndroidGifAnimation.decode(bytes).also { opened = it; currentCoroutineContext().ensureActive() }
        }
    } catch (error: Throwable) {
        opened?.release()
        throw error
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                directory?.let { target ->
                    target.listFiles().orEmpty().forEach { check(it.delete()) }
                    check(!target.exists() || target.delete())
                }
            } catch (error: Throwable) { opened?.release(); throw error }
        }
    }
}
