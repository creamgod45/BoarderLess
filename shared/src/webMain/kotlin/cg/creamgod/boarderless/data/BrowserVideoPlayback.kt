package cg.creamgod.boarderless.data

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

@Serializable
private data class BrowserVideoStatus(val durationMs: Long, val positionMs: Long, val width: Int, val height: Int,
    val playing: Boolean, val muted: Boolean, val ended: Boolean, val failed: Boolean, val released: Boolean) {
    fun state() = VideoPlaybackState(durationMs, positionMs, width, height, playing, muted, ended, failed, released)
}

internal class BrowserVideoSink(val id: String, private val mime: String) : AssetDownloadSink {
    private var size = 0L
    private var closed = false
    private val digest = SHA256()
    init { browserVideoCreate(id, mime) }
    override suspend fun writeChunk(offset: Long, bytes: ByteArray) {
        check(!closed)
        require(offset == size && bytes.isNotEmpty() && bytes.size <= MaxWorkspaceAssetBytes - size)
        currentCoroutineContext().ensureActive()
        digest.update(bytes)
        var partOffset = 0
        while (partOffset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val end = minOf(bytes.size, partOffset + DefaultAssetUploadChunkBytes)
            browserVideoAppend(id, Base64.encode(bytes.copyOfRange(partOffset, end)))
            partOffset = end
            yield()
        }
        size += bytes.size
        yield()
    }
    override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference {
        check(!closed && size == expectedByteSize && size > 0)
        currentCoroutineContext().ensureActive()
        val checksum = "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        check(checksum.equals(expectedChecksum, ignoreCase = true))
        closed = true
        return LocalAssetReference(id, mime)
    }
    override suspend fun abort() { browserVideoRelease(id); closed = true }
}

class BrowserVideoPlayback private constructor(private val id: String) : VideoPlayback {
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state = mutableState.asStateFlow()
    override suspend fun setPlaying(playing: Boolean) = command("play", if (playing) 1.0 else 0.0)
    override suspend fun setMuted(muted: Boolean) = command("muted", if (muted) 1.0 else 0.0)
    override suspend fun seekTo(positionMs: Long) = command("seek", positionMs.coerceIn(0, state.value.durationMs).toDouble())
    private suspend fun command(action: String, value: Double) {
        check(!state.value.released && !state.value.failed)
        suspendCancellableCoroutine<Unit> { pending ->
            browserVideoCommand(id, action, value) { error ->
                if (pending.isActive) {
                    if (error == null) pending.resume(Unit) else pending.resumeWithException(IllegalStateException(error))
                }
            }
        }
    }
    internal fun attached(value: Boolean) { if (!state.value.released) browserVideoAttached(id, value) }
    internal suspend fun frame(): ImageBitmap {
        val request = randomUuid()
        val bytes = suspendCancellableCoroutine<ByteArray> { pending ->
            pending.invokeOnCancellation { browserVideoCancelFrame(id, request) }
            browserVideoFrame(id, request) { data, error ->
                if (pending.isActive) {
                    if (error != null || data == null) pending.resumeWithException(IllegalStateException(error))
                    else try { pending.resume(Base64.decode(data)) } catch (failure: Exception) { pending.resumeWithException(failure) }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        awaitBrowserImageDecoder()
        val data = Data.makeFromBytes(bytes)
        try {
            val codec = Codec.makeFromData(data)
            try {
                AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                val bitmap = codec.readPixels()
                try { return Image.makeFromBitmap(bitmap).toComposeImageBitmap() }
                finally { bitmap.close() }
            } finally { codec.close() }
        } finally { data.close() }
    }
    internal fun fail() { mutableState.value = state.value.copy(playing = false, failed = true) }
    override suspend fun release() = withContext(NonCancellable) {
        browserVideoRelease(id)
        mutableState.value = state.value.copy(playing = false, released = true)
    }
    companion object {
        internal suspend fun open(id: String): BrowserVideoPlayback {
            val player = BrowserVideoPlayback(id)
            try {
                withTimeout(31_000) {
                    suspendCancellableCoroutine<Unit> { pending ->
                        pending.invokeOnCancellation { browserVideoRelease(id) }
                        browserVideoOpen(id, { status ->
                            if (!player.state.value.released) {
                                try { player.mutableState.value = Json.decodeFromString<BrowserVideoStatus>(status).state() }
                                catch (_: Exception) { player.fail() }
                            }
                        }) { error ->
                            if (pending.isActive) {
                                if (error == null) pending.resume(Unit) else pending.resumeWithException(IllegalStateException(error))
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(!player.state.value.failed && !player.state.value.released && player.state.value.durationMs > 0)
                AssetPreviewPolicy.validateDimensions(player.state.value.width, player.state.value.height)
                return player
            } catch (error: Throwable) { player.release(); throw error }
        }
    }
}

suspend fun loadBrowserVideo(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String): VideoPlayback {
    val ticket = gateway.authorize(session, assetId)
    VideoAssetPolicy.validate(ticket, session, assetId)
    val sink = BrowserVideoSink(randomUuid(), ticket.asset.mediaType)
    var opened: BrowserVideoPlayback? = null
    try {
        val authorized = object : AssetDownloadGateway {
            override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
            override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
        }
        val local = AssetDownloadCoordinator(authorized).download(session, assetId, sink)
        val playback = BrowserVideoPlayback.open(local.token).also { opened = it }
        currentCoroutineContext().ensureActive()
        return playback
    } catch (error: Throwable) {
        withContext(NonCancellable) { if (opened != null) opened.release() else sink.abort() }
        throw error
    }
}

@Composable
fun BrowserVideoSurface(playback: VideoPlayback, modifier: Modifier) {
    val player = playback as BrowserVideoPlayback
    var image by remember(player) { mutableStateOf<ImageBitmap?>(null) }
    val state by player.state.collectAsState()
    DisposableEffect(player) { player.attached(true); onDispose { player.attached(false) } }
    LaunchedEffect(player) {
        try {
            while (isActive) {
                image = player.frame()
                delay(if (player.state.value.playing) 16 else 250)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { player.fail() }
        finally { image = null }
    }
    if (!state.released) image?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
}

internal expect fun browserVideoCreate(id: String, mime: String)
internal expect fun browserVideoAppend(id: String, bytes: String)
internal expect fun browserVideoOpen(id: String, state: (String) -> Unit, done: (String?) -> Unit)
internal expect fun browserVideoCommand(id: String, action: String, value: Double, done: (String?) -> Unit)
internal expect fun browserVideoFrame(id: String, request: String, done: (String?, String?) -> Unit)
internal expect fun browserVideoCancelFrame(id: String, request: String)
internal expect fun browserVideoAttached(id: String, attached: Boolean)
internal expect fun browserVideoRelease(id: String)
