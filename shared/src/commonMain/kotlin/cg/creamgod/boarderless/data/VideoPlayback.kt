package cg.creamgod.boarderless.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Device-local player state. Never persisted in an operation, clipboard or scheme payload. */
data class VideoPlaybackState(
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val playing: Boolean = false,
    val muted: Boolean = true,
    val ended: Boolean = false,
    val failed: Boolean = false,
    val released: Boolean = false,
    val audioFocusBlocked: Boolean = false,
)

interface VideoPlayback {
    val state: StateFlow<VideoPlaybackState>
    suspend fun setPlaying(playing: Boolean)
    suspend fun setMuted(muted: Boolean)
    suspend fun seekTo(positionMs: Long)
    /** Idempotent; releases native player and this session's verified local file. */
    suspend fun release()
}

/** Serializes UI commands and closes a failed player even if its native state never reports failure. */
class VideoPlaybackLease(val player: VideoPlayback) {
    private val guard = Mutex()
    private var closed = false
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()

    suspend fun execute(command: suspend VideoPlayback.() -> Unit) = guard.withLock {
        check(!closed) { "Video lease is closed" }
        try { player.command() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutableFailed.value = true
            try { withContext(NonCancellable) { closeNative() } }
            catch (cleanup: Exception) { if (cleanup !== error) error.addSuppressed(cleanup) }
            throw error
        }
    }

    suspend fun release() = withContext(NonCancellable) { guard.withLock { closeNative() } }

    private suspend fun closeNative() {
        if (closed) return
        // Terminal before calling native code; a close failure must never allow more commands.
        closed = true
        try { player.release() }
        catch (error: Exception) { mutableFailed.value = true; throw error }
    }
}

object VideoAssetPolicy {
    fun validate(ticket: AssetDownloadTicket, session: WorkspaceSession, assetId: String) {
        require(ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id)
        require(ticket.asset.status == AssetStatus.Ready)
        require(ticket.asset.mediaType in setOf("video/mp4", "video/webm"))
        require(ticket.asset.byteSize in 1..MaxWorkspaceAssetBytes)
        require(ticket.asset.checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
    }
}
