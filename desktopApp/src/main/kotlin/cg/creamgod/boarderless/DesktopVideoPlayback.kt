package cg.creamgod.boarderless

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import cg.creamgod.boarderless.data.*
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image as SkiaImage
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16

internal interface DesktopAudioOutput {
    fun write(bytes: ByteArray, offset: Int): Int
    fun pause()
    fun resume()
    fun close()
}

private class SystemVideoAudio(format: AudioFormat) : DesktopAudioOutput {
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
    init { try { line.open(format, 8192); line.start() } catch (error: Throwable) { line.close(); throw error } }
    override fun write(bytes: ByteArray, offset: Int): Int {
        val count = minOf(line.available(), bytes.size - offset) / line.format.frameSize * line.format.frameSize
        return if (count > 0) line.write(bytes, offset, count) else 0
    }
    override fun pause() { line.stop(); line.flush() }
    override fun resume() { line.start() }
    override fun close() { line.stop(); line.flush(); line.close() }
}

/** Native decoder/audio access is confined to one worker, never the Swing/Compose UI thread. */
class DesktopVideoPlayback private constructor(
    private val directory: Path,
    private val grabber: FFmpegFrameGrabber,
    private val worker: ExecutorCoroutineDispatcher,
    private val audioFactory: (AudioFormat) -> DesktopAudioOutput,
    private val displayTransform: DesktopVideoDisplayTransform,
) : VideoPlayback {
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state = mutableState.asStateFlow()
    private val mutableImage = MutableStateFlow<ImageBitmap?>(null)
    val image = mutableImage.asStateFlow()
    private val revision = MutableStateFlow(0L)
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val guard = Mutex()
    private val releaseGuard = Mutex()
    private var audio: DesktopAudioOutput? = null
    private var attached = false
    private var wantsPlay = false
    private var generation = 0L
    private var anchorNanos = 0L
    private var anchorUs = 0L

    private fun changed() { revision.value++ }
    private fun updatePlaying() {
        val play = wantsPlay && attached
        if (play != state.value.playing) {
            if (play) { anchorNanos = System.nanoTime(); anchorUs = state.value.positionMs * 1000; if (!state.value.muted) audio?.resume() }
            else audio?.pause()
            mutableState.value = state.value.copy(playing = play)
        }
        changed()
    }
    private suspend fun <T> onWorker(block: suspend () -> T): T = releaseGuard.withLock {
        check(!state.value.released) { "Video is released" }
        withContext(worker) { guard.withLock { block() } }
    }
    internal suspend fun attach(value: Boolean) {
        releaseGuard.withLock {
            if (state.value.released) return
            withContext(worker) { guard.withLock {
            check(!value || !state.value.failed)
            attached = value
            updatePlaying()
            } }
        }
    }
    override suspend fun setPlaying(playing: Boolean) = onWorker {
            check(!state.value.released && !state.value.failed)
            if (playing && state.value.ended) seek(0)
            wantsPlay = playing
            updatePlaying()
    }
    override suspend fun setMuted(muted: Boolean) = onWorker {
            check(!state.value.released && !state.value.failed)
            if (!muted && grabber.hasAudio() && audio == null) audio = audioFactory(AudioFormat(48000f, 16, 2, true, false))
            if (muted) audio?.pause() else if (state.value.playing) audio?.resume()
            mutableState.value = state.value.copy(muted = muted)
            changed()
    }
    override suspend fun seekTo(positionMs: Long) = onWorker {
        check(!state.value.released && !state.value.failed); seek(positionMs)
    }
    private fun seek(positionMs: Long) {
        val target = positionMs.coerceIn(0, state.value.durationMs)
        generation++
        audio?.pause()
        grabber.setTimestamp(target * 1000, true)
        val frame = grabber.grabImage()
        if (frame?.image != null) mutableImage.value = bitmap(frame)
        grabber.setTimestamp(target * 1000, true)
        anchorNanos = System.nanoTime(); anchorUs = target * 1000
        mutableState.value = state.value.copy(positionMs = target, ended = false)
        if (state.value.playing && !state.value.muted) audio?.resume()
        changed()
    }

    private data class Packet(val epoch: Long, val timestampUs: Long, val image: ImageBitmap?, val pcm: ByteArray?, val eof: Boolean = false)
    private fun readPacket(): Packet? {
        val frame = grabber.grabFrame(true, true, true, false, false) ?: return null
        val bitmap = if (frame.image != null) bitmap(frame) else null
        val samples = frame.samples?.firstOrNull() as? ShortBuffer
        val pcm = samples?.duplicate()?.let { buffer ->
            ByteArray(buffer.remaining() * 2).also { bytes ->
                var index = 0
                while (buffer.hasRemaining()) {
                    val sample = buffer.get().toInt()
                    bytes[index++] = sample.toByte(); bytes[index++] = (sample shr 8).toByte()
                }
            }
        }
        return Packet(generation, frame.timestamp.coerceAtLeast(0), bitmap, pcm)
    }
    private fun bitmap(frame: Frame): ImageBitmap {
        AssetPreviewPolicy.validateDimensions(frame.imageWidth, frame.imageHeight)
        val rowBytes = frame.imageWidth * 4
        require(frame.imageStride >= rowBytes)
        val source = (frame.image[0] as ByteBuffer).duplicate()
        val pixels = ByteArray(rowBytes * frame.imageHeight)
        for (row in 0 until frame.imageHeight) {
            source.position(row * frame.imageStride)
            source.get(pixels, row * rowBytes, rowBytes)
        }
        val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val sourceImage = SkiaImage.makeRaster(info, pixels, rowBytes)
        val displayed = try { displayTransform.apply(sourceImage) }
        catch (error: Throwable) { sourceImage.close(); throw error }
        if (displayed !== sourceImage) sourceImage.close()
        return displayed.toComposeImageBitmap()
    }
    private suspend fun runPlayback() {
        try {
            while (currentCoroutineContext().isActive) {
                if (!state.value.playing) { revision.first { state.value.playing || state.value.released }; continue }
                val packet = guard.withLock {
                    if (!state.value.playing) return@withLock null
                    readPacket() ?: Packet(generation, state.value.durationMs * 1000, null, null, eof = true)
                } ?: continue
                var offset = 0
                while (currentCoroutineContext().isActive) {
                    val wait = guard.withLock {
                        if (state.value.released || state.value.failed || packet.epoch != generation) return@withLock -1L
                        if (!state.value.playing) return@withLock 20L
                        val remaining = (packet.timestampUs - anchorUs) * 1000 - (System.nanoTime() - anchorNanos)
                        if (remaining > 0) return@withLock (remaining / 1_000_000).coerceIn(1, 20)
                        if (packet.eof) {
                            wantsPlay = false; audio?.pause()
                            mutableState.value = state.value.copy(playing = false, ended = true, positionMs = state.value.durationMs)
                            changed(); return@withLock 0L
                        }
                        if (packet.image != null) mutableImage.value = packet.image
                        mutableState.value = state.value.copy(positionMs = (packet.timestampUs / 1000).coerceIn(0, state.value.durationMs))
                        val pcm = packet.pcm
                        if (pcm != null && !state.value.muted && audio != null) {
                            offset += audio!!.write(pcm, offset)
                            if (offset < pcm.size) return@withLock 2L
                        }
                        0L
                    }
                    if (wait <= 0) break
                    delay(wait)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            guard.withLock {
                wantsPlay = false
                mutableState.value = state.value.copy(playing = false, failed = true); changed()
                // Even a disconnected audio device that also fails to pause must publish failure.
                try { audio?.pause() } catch (_: Exception) { }
            }
        }
    }

    override suspend fun release() = withContext(NonCancellable + Dispatchers.IO) {
        releaseGuard.withLock {
            if (!state.value.released) {
                try { withContext(worker) {
                    guard.withLock {
                        scope.cancel(); generation++; wantsPlay = false; attached = false
                        try { audio?.close() } finally {
                            audio = null
                            try { grabber.close() } finally {
                                mutableImage.value = null
                                mutableState.value = state.value.copy(playing = false, released = true); changed()
                            }
                        }
                    }
                } } finally {
                    // A native/audio close error must not retain the worker or verified file.
                    try { worker.close() } finally { clearDesktopVideoDirectory(directory) }
                }
            }
            clearDesktopVideoDirectory(directory)
        }
    }

    companion object {
        internal suspend fun open(local: LocalAssetReference, directory: Path, audioFactory: (AudioFormat) -> DesktopAudioOutput = ::SystemVideoAudio): DesktopVideoPlayback {
            val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "boarderless-video").apply { isDaemon = true } }.asCoroutineDispatcher()
            var opened: DesktopVideoPlayback? = null
            var native: FFmpegFrameGrabber? = null
            try {
                return withContext(worker) {
                    val grabber = FFmpegFrameGrabber(local.token).also { native = it }
                    grabber.format = if (local.mediaType == "video/mp4") "mov" else "matroska"
                    grabber.setOption("protocol_whitelist", "file")
                    grabber.setOption("enable_drefs", "0")
                    grabber.setOption("use_absolute_path", "0")
                    grabber.sampleFormat = AV_SAMPLE_FMT_S16
                    grabber.pixelFormat = AV_PIX_FMT_RGBA
                    grabber.sampleRate = 48000; grabber.audioChannels = 2
                    grabber.start()
                    currentCoroutineContext().ensureActive()
                    check(grabber.hasVideo() && grabber.lengthInTime > 0)
                    AssetPreviewPolicy.validateDimensions(grabber.imageWidth, grabber.imageHeight)
                    val display = DesktopVideoDisplayTransform.from(grabber.imageWidth, grabber.imageHeight,
                        grabber.getVideoSideData("Display Matrix") as? ByteBuffer, grabber.aspectRatio)
                    val player = DesktopVideoPlayback(directory, grabber, worker, audioFactory, display).also { opened = it }
                    player.mutableState.value = VideoPlaybackState(grabber.lengthInTime / 1000, width = display.width, height = display.height)
                    player.seek(0)
                    player.scope.launch { player.runPlayback() }
                    player
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    if (opened != null) opened.release() else {
                        try { withContext(worker) { native?.close() } } finally { worker.close(); clearDesktopVideoDirectory(directory) }
                    }
                }
                throw error
            }
        }
    }
}

internal fun clearDesktopVideoDirectory(directory: Path) {
    if (Files.exists(directory)) {
        Files.list(directory).use { paths -> paths.forEach { Files.delete(it) } }
        Files.delete(directory)
    }
}

suspend fun loadDesktopVideo(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String): VideoPlayback {
    val ticket = gateway.authorize(session, assetId)
    VideoAssetPolicy.validate(ticket, session, assetId)
    var directory: Path? = null
    var opened: DesktopVideoPlayback? = null
    try {
        val local = withContext(Dispatchers.IO) {
            val target = Files.createTempDirectory("boarderless-video-").also { directory = it }
            val sink = JvmFileAssetDownloadSink.create(target.toString(), assetId, ticket.asset.mediaType)
            val authorized = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
            }
            AssetDownloadCoordinator(authorized).download(session, assetId, sink)
        }
        val player = DesktopVideoPlayback.open(local, checkNotNull(directory)).also { opened = it }
        currentCoroutineContext().ensureActive()
        return player
    } catch (error: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) { if (opened != null) opened.release() else directory?.let(::clearDesktopVideoDirectory) }
        throw error
    }
}

@Composable
fun DesktopVideoSurface(playback: VideoPlayback, modifier: Modifier) {
    val player = playback as DesktopVideoPlayback
    val image by player.image.collectAsState()
    LaunchedEffect(player) {
        try { player.attach(true); awaitCancellation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            try { player.release() } catch (_: Exception) { }
        }
        finally {
            if (!player.state.value.released) withContext(NonCancellable) {
                try { player.attach(false) }
                catch (_: Exception) { try { player.release() } catch (_: Exception) { } }
            }
        }
    }
    image?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
}
