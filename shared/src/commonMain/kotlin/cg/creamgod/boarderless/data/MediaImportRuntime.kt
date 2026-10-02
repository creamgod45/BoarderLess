package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** Epoch prevents a quick hide/show from resuming a previously activated decoder. */
data class MediaPlaybackActivity(val available: Boolean = true, val epoch: Long = 0)

/** Device picker handles stay outside shared canvas state and persistent payloads. */
class MediaImportRuntime(
    val selectSource: (suspend () -> AssetTransferSource?)? = null,
    /** Authorized device-local preview. Neither pixels nor cache handles enter canvas operations. */
    val loadPreview: (suspend (WorkspaceSession, String) -> ImageBitmap)? = null,
    val clearPreviewCache: (suspend () -> Unit)? = null,
    val loadGif: (suspend (WorkspaceSession, String) -> GifAnimation)? = null,
    val loadVideo: (suspend (WorkspaceSession, String) -> VideoPlayback)? = null,
    val videoSurface: (@Composable (VideoPlayback, Modifier) -> Unit)? = null,
    val playbackActivity: StateFlow<MediaPlaybackActivity>? = null,
) {
    companion object {
        val Unavailable = MediaImportRuntime()
    }
}
