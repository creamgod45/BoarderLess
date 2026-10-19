package cg.creamgod.boarderless.data

import android.content.Context
import android.media.MediaPlayer
import android.view.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** All MediaPlayer calls and callbacks run on Main; private file IO runs on IO. */
class AndroidVideoPlayback private constructor(
    private val player: MediaPlayer,
    private val directory: File,
    focusPort: VideoAudioFocusPort,
) : VideoPlayback {
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prepared = CompletableDeferred<Unit>()
    private val releaseGuard = Mutex()
    private var ready = false
    private var wantsPlay = false
    private var surface: Surface? = null
    private var owner: Any? = null
    private var hasAudio = false
    private val audioFocus =
        VideoAudioFocusSession(focusPort) {
            if (!state.value.released && !state.value.failed) {
                wantsPlay = false
                try {
                    player.setVolume(0f, 0f)
                    if (player.isPlaying) player.pause()
                } catch (_: Exception) {
                    fail()
                }
                mutableState.value = state.value.copy(playing = false, audioFocusBlocked = true)
            }
        }

    private fun publishPosition() {
        if (ready && !state.value.failed && !state.value.released) {
            mutableState.value = state.value.copy(positionMs = player.currentPosition.toLong().coerceAtLeast(0))
        }
    }

    private fun updatePlaying() {
        if (!ready || state.value.failed || state.value.released) return
        val play = wantsPlay && surface != null
        try {
            if (!audioFocus.update(play && !state.value.muted && hasAudio)) {
                wantsPlay = false
                player.setVolume(0f, 0f)
                if (player.isPlaying) player.pause()
                mutableState.value = state.value.copy(playing = false, audioFocusBlocked = true)
                return
            }
            val volume = if (play && !state.value.muted) 1f else 0f
            player.setVolume(volume, volume)
            if (play && !player.isPlaying) {
                player.start()
            } else if (!play && player.isPlaying) {
                player.pause()
            }
            mutableState.value = state.value.copy(playing = play, audioFocusBlocked = false)
        } catch (_: Exception) {
            fail()
        }
    }

    private fun fail() {
        wantsPlay = false
        mutableState.value = state.value.copy(playing = false, failed = true)
        try {
            audioFocus.release()
        } catch (_: Exception) {
        }
        if (!prepared.isCompleted) prepared.completeExceptionally(IllegalStateException("Video preparation failed"))
    }

    internal fun attachSurface(
        surfaceOwner: Any,
        textureSurface: Surface?,
    ) {
        if (state.value.released || state.value.failed) return
        if (textureSurface == null && owner !== surfaceOwner) return
        owner = if (textureSurface == null) null else surfaceOwner
        surface = textureSurface
        try {
            player.setSurface(textureSurface)
            updatePlaying()
        } catch (_: Exception) {
            fail()
        }
    }

    override suspend fun setPlaying(playing: Boolean) =
        withContext(Dispatchers.Main.immediate) {
            check(!state.value.released && !state.value.failed)
            if (playing && state.value.ended) {
                player.seekTo(0)
                mutableState.value = state.value.copy(positionMs = 0, ended = false)
            }
            wantsPlay = playing
            updatePlaying()
        }

    override suspend fun setMuted(muted: Boolean) =
        withContext(Dispatchers.Main.immediate) {
            check(!state.value.released && !state.value.failed)
            if (muted) player.setVolume(0f, 0f)
            mutableState.value = state.value.copy(muted = muted)
            updatePlaying()
        }

    override suspend fun seekTo(positionMs: Long) =
        withContext(Dispatchers.Main.immediate) {
            check(!state.value.released && !state.value.failed)
            val target = positionMs.coerceIn(0, state.value.durationMs).toInt()
            player.seekTo(target) // API 24 compatible overload; native player seeks to a supported sync frame.
            mutableState.value = state.value.copy(positionMs = target.toLong(), ended = false)
        }

    override suspend fun release() =
        withContext(NonCancellable) {
            releaseGuard.withLock {
                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (!state.value.released) {
                            wantsPlay = false
                            scope.cancel()
                            try {
                                audioFocus.release()
                            } finally {
                                try {
                                    player.release()
                                } finally {
                                    ready = false
                                    surface = null
                                    owner = null
                                    mutableState.value = state.value.copy(playing = false, released = true)
                                }
                            }
                        }
                    }
                } finally {
                    clearAndroidVideoDirectory(directory)
                }
            }
        }

    companion object {
        internal suspend fun open(
            file: File,
            directory: File,
            focusPort: VideoAudioFocusPort = UnavailableVideoAudioFocus,
        ): AndroidVideoPlayback {
            var opened: AndroidVideoPlayback? = null
            try {
                return withContext(Dispatchers.Main.immediate) {
                    val player = MediaPlayer()
                    val playback = AndroidVideoPlayback(player, directory, focusPort).also { opened = it }
                    player.setAudioAttributes(videoAudioAttributes())
                    player.setOnPreparedListener {
                        try {
                            val width = it.videoWidth
                            val height = it.videoHeight
                            AssetPreviewPolicy.validateDimensions(width, height)
                            check(it.duration > 0) { "Video has no finite duration" }
                            playback.ready = true
                            playback.hasAudio =
                                it.trackInfo.any { track -> track.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO }
                            playback.mutableState.value = VideoPlaybackState(it.duration.toLong(), width = width, height = height)
                            player.setVolume(0f, 0f)
                            playback.prepared.complete(Unit)
                        } catch (_: Exception) {
                            playback.fail()
                        }
                    }
                    player.setOnErrorListener { _, _, _ ->
                        playback.fail()
                        true
                    }
                    player.setOnCompletionListener {
                        playback.wantsPlay = false
                        playback.mutableState.value =
                            playback.state.value.copy(playing = false, ended = true, positionMs = playback.state.value.durationMs)
                        try {
                            playback.audioFocus.update(false)
                        } catch (_: Exception) {
                            playback.fail()
                        }
                    }
                    player.setOnSeekCompleteListener { playback.publishPosition() }
                    player.setDataSource(file.absolutePath)
                    player.prepareAsync()
                    withTimeout(30_000) { playback.prepared.await() }
                    currentCoroutineContext().ensureActive()
                    playback.scope.launch {
                        while (isActive) {
                            delay(250)
                            try {
                                playback.publishPosition()
                            } catch (_: Exception) {
                                playback.fail()
                            }
                        }
                    }
                    playback
                }
            } catch (error: Throwable) {
                opened?.release()
                throw error
            }
        }
    }
}

internal suspend fun clearAndroidVideoDirectory(directory: File) =
    withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().forEach { check(it.delete()) { "Cannot clear private video file" } }
        check(!directory.exists() || directory.delete()) { "Cannot clear private video directory" }
    }

suspend fun loadAndroidVideo(
    gateway: AssetDownloadGateway,
    session: WorkspaceSession,
    assetId: String,
    cacheDirectory: File,
    context: Context? = null,
): VideoPlayback {
    val ticket = gateway.authorize(session, assetId)
    VideoAssetPolicy.validate(ticket, session, assetId)
    var directory: File? = null
    var opened: AndroidVideoPlayback? = null
    try {
        val local =
            withContext(Dispatchers.IO) {
                val target = File(cacheDirectory, "asset-video-${UUID.randomUUID()}").also { directory = it }
                check(target.mkdir())
                val sink = AndroidFileAssetDownloadSink.create(target, ticket.asset.mediaType)
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
                AssetDownloadCoordinator(authorized).download(session, assetId, sink)
            }
        val playback =
            AndroidVideoPlayback
                .open(
                    File(local.token),
                    checkNotNull(directory),
                    context?.let(::SystemVideoAudioFocus) ?: UnavailableVideoAudioFocus,
                ).also { opened = it }
        currentCoroutineContext().ensureActive()
        return playback
    } catch (error: Throwable) {
        withContext(NonCancellable) {
            if (opened != null) opened.release() else directory?.let { clearAndroidVideoDirectory(it) }
        }
        throw error
    }
}
