@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data

import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFoundation.*
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObjectProtocol

/** Local-file AVPlayer only. Its item, timer and observers share the Main dispatcher. */
class IosVideoPlayback private constructor(
    private val player: AVPlayer,
    private val item: AVPlayerItem,
    private val sink: IosFileAssetDownloadSink,
    private val audioSession: IosVideoAudioSession,
) : VideoPlayback {
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val releaseGuard = Mutex()
    private var wantsPlay = false
    private var hasAudio = false
    private var surfaceOwner: Any? = null
    private val layers = mutableSetOf<AVPlayerLayer>()
    private val observers = mutableListOf<NSObjectProtocol>()

    private fun fail() {
        wantsPlay = false
        mutableState.value = state.value.copy(playing = false, failed = true)
        try { player.muted = true; player.pause() } finally {
            try { audioSession.release(this) } catch (_: Exception) { }
        }
    }

    private fun audioInterrupted() {
        if (state.value.released || state.value.failed) return
        wantsPlay = false
        mutableState.value = state.value.copy(playing = false, audioFocusBlocked = true)
        player.muted = true
        player.pause()
    }

    private fun refresh() {
        if (state.value.released || state.value.failed) return
        if (item.status == AVPlayerItemStatusFailed || player.status == AVPlayerStatusFailed) { fail(); return }
        val seconds = CMTimeGetSeconds(player.currentTime())
        mutableState.value = state.value.copy(
            positionMs = if (seconds.isFinite()) (seconds * 1000).toLong().coerceIn(0, state.value.durationMs) else state.value.positionMs,
            playing = player.rate > 0f,
        )
    }

    private fun updatePlaying() {
        if (state.value.released || state.value.failed) return
        try {
            val play = wantsPlay && surfaceOwner != null
            val audible = play && !state.value.muted && hasAudio
            if (!audible) {
                player.muted = true
                if (!play) player.pause()
                audioSession.release(this)
            } else if (!audioSession.acquire(this) { audioInterrupted() }) {
                audioInterrupted()
                return
            }
            player.muted = !play || state.value.muted
            if (play) player.play() else player.pause()
            mutableState.value = state.value.copy(audioFocusBlocked = false)
            refresh()
        } catch (error: Exception) { fail(); throw error }
    }

    internal fun attach(layer: AVPlayerLayer, owner: Any) {
        if (state.value.released || state.value.failed) return
        layers.add(layer)
        surfaceOwner = owner
        layer.player = player
        updatePlaying()
    }

    internal fun detach(layer: AVPlayerLayer, owner: Any) {
        layer.player = null
        layers.remove(layer)
        if (surfaceOwner === owner) { surfaceOwner = null; updatePlaying() }
    }

    override suspend fun setPlaying(playing: Boolean) = withContext(Dispatchers.Main) {
        check(!state.value.released && !state.value.failed)
        if (playing && state.value.ended) seekTo(0)
        wantsPlay = playing
        updatePlaying()
    }

    override suspend fun setMuted(muted: Boolean) = withContext(Dispatchers.Main) {
        check(!state.value.released && !state.value.failed)
        if (muted) player.muted = true
        mutableState.value = state.value.copy(muted = muted)
        updatePlaying()
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) {
        check(!state.value.released && !state.value.failed)
        val target = positionMs.coerceIn(0, state.value.durationMs)
        player.seekToTime(CMTimeMake(target, 1000))
        mutableState.value = state.value.copy(positionMs = target, ended = false)
    }

    override suspend fun release() = withContext(NonCancellable) {
        releaseGuard.withLock {
            try { withContext(Dispatchers.Main) {
                if (!state.value.released) {
                    scope.cancel()
                    wantsPlay = false
                    surfaceOwner = null
                    mutableState.value = state.value.copy(playing = false, released = true)
                    var failure: Exception? = null
                    fun cleanup(action: () -> Unit) {
                        try { action() } catch (error: Exception) {
                            val previous = failure
                            if (previous == null) failure = error else if (previous !== error) previous.addSuppressed(error)
                        }
                    }
                    cleanup { player.muted = true }
                    cleanup { player.pause() }
                    cleanup { audioSession.release(this) }
                    cleanup { item.cancelPendingSeeks() }
                    cleanup { item.asset.cancelLoading() }
                    layers.forEach { cleanup { it.player = null } }
                    layers.clear()
                    observers.forEach { observer -> cleanup { NSNotificationCenter.defaultCenter.removeObserver(observer) } }
                    observers.clear()
                    cleanup { player.replaceCurrentItemWithPlayerItem(null) }
                    failure?.let { throw it }
                }
            } } finally { sink.release() }
        }
    }

    companion object {
        internal suspend fun open(local: LocalAssetReference, sink: IosFileAssetDownloadSink,
            audioSession: IosVideoAudioSession = systemIosVideoAudioSession): IosVideoPlayback {
            var opened: IosVideoPlayback? = null
            try {
                return withContext(Dispatchers.Main) {
                    val url = NSURL.fileURLWithPath(local.token)
                    val item = AVPlayerItem.playerItemWithURL(url)
                    val player = AVPlayer.playerWithPlayerItem(item)
                    val playback = IosVideoPlayback(player, item, sink, audioSession).also { opened = it }
                    player.muted = true
                    player.actionAtItemEnd = AVPlayerActionAtItemEndPause
                    val center = NSNotificationCenter.defaultCenter
                    playback.observers += center.addObserverForName(
                        AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue,
                    ) {
                        if (!playback.state.value.released) {
                            playback.wantsPlay = false
                            playback.mutableState.value = playback.state.value.copy(playing = false, ended = true, positionMs = playback.state.value.durationMs)
                            try { playback.audioSession.release(playback) } catch (_: Exception) { playback.fail() }
                        }
                    }
                    playback.observers += center.addObserverForName(
                        UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
                    ) { playback.scope.launch { playback.release() } }
                    playback.observers += center.addObserverForName(
                        AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue,
                    ) { if (!playback.state.value.released) playback.fail() }
                    playback.observers += center.addObserverForName(
                        AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue,
                    ) {
                        // Every AVPlayer (including muted ones) must be reconstructed after a media-server reset.
                        if (!playback.state.value.released && !playback.state.value.failed) {
                            try { playback.fail() } catch (_: Exception) { }
                        }
                    }
                    withTimeout(30_000) {
                        while (true) {
                            check(!playback.state.value.released) { "Video preparation was stopped" }
                            check(item.status != AVPlayerItemStatusFailed && player.status != AVPlayerStatusFailed) {
                                val error = item.error ?: player.error
                                "Video preparation failed (${error?.domain}/${error?.code}: ${error?.localizedDescription})"
                            }
                            if (item.status == AVPlayerItemStatusReadyToPlay) {
                                val seconds = CMTimeGetSeconds(item.duration)
                                val dimensions = item.presentationSize.useContents { width.toInt() to height.toInt() }
                                if (seconds.isFinite() && seconds > 0 && dimensions.first > 0 && dimensions.second > 0) {
                                    AssetPreviewPolicy.validateDimensions(dimensions.first, dimensions.second)
                                    check(seconds <= Long.MAX_VALUE / 1000.0)
                                    playback.hasAudio = item.asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty()
                                    playback.mutableState.value = VideoPlaybackState((seconds * 1000).toLong(), width = dimensions.first, height = dimensions.second)
                                    break
                                }
                            }
                            delay(25)
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    playback.scope.launch {
                        while (isActive) { delay(250); playback.refresh() }
                    }
                    playback
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) { if (opened != null) opened.release() else sink.release() }
                throw error
            }
        }
    }
}

suspend fun loadIosVideo(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String): VideoPlayback {
    val ticket = gateway.authorize(session, assetId)
    VideoAssetPolicy.validate(ticket, session, assetId)
    var sink: IosFileAssetDownloadSink? = null
    var opened: IosVideoPlayback? = null
    try {
        val destination = IosFileAssetDownloadSink.create(ticket.asset.mediaType).also { sink = it }
        val authorized = object : AssetDownloadGateway {
            override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
            override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
        }
        val local = AssetDownloadCoordinator(authorized).download(session, assetId, destination)
        val playback = IosVideoPlayback.open(local, destination).also { opened = it }
        currentCoroutineContext().ensureActive()
        return playback
    } catch (error: Throwable) {
        withContext(NonCancellable) { if (opened != null) opened.release() else sink?.release() }
        throw error
    }
}
