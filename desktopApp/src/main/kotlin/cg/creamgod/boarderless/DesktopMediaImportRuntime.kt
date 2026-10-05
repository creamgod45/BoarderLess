package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.JvmFileAssetTransferSource
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.MediaPlaybackActivity
import cg.creamgod.boarderless.data.bitmapPreviewCache
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.i18n.Strings
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun desktopMediaImportRuntime(window: Window, playbackActivity: StateFlow<MediaPlaybackActivity>? = null): MediaImportRuntime {
    val previewCache = bitmapPreviewCache()
    val imageLoader = DesktopViewportImageLoader()
    return MediaImportRuntime(
    playbackActivity = playbackActivity,
    giphyApiKey = System.getenv("BOARDERLESS_GIPHY_API_KEY")?.takeIf { it.isNotBlank() },
    loadGiphyStill = ::loadDesktopGiphyStill,
    loadGiphyAnimation = ::loadDesktopGiphyAnimation,
    clearPreviewCache = { previewCache.clear(); imageLoader.clear() },
    loadImageTiles = { session, assetId, request, emit ->
        desktopPreviewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { imageLoader.load(gateway, session, assetId, request, emit) }
            finally { gateway.close() }
        }
    },
    loadVideo = { session, assetId ->
        desktopPreviewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { loadDesktopVideo(gateway, session, assetId) } finally { gateway.close() }
        }
    },
    videoSurface = { playback, modifier -> DesktopVideoSurface(playback, modifier) },
    loadGif = { session, assetId ->
        desktopPreviewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { loadDesktopGif(gateway, session, assetId) } finally { gateway.close() }
        }
    },
    selectSource = {
        val path = selectMediaFile(window)
        path?.let { JvmFileAssetTransferSource.fromPath(it) }
    },
    loadPreview = { session, assetId ->
        desktopPreviewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try {
                DesktopAssetPreviewLoader(gateway, cache = previewCache).load(session, assetId)
            } finally {
                gateway.close()
            }
        }
    },
)
}

private val desktopPreviewPermits = kotlinx.coroutines.sync.Semaphore(2)

private suspend fun selectMediaFile(window: Window): String? = suspendCancellableCoroutine { continuation ->
    var dialog: FileDialog? = null
    continuation.invokeOnCancellation {
        EventQueue.invokeLater { dialog?.dispose() }
    }
    EventQueue.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val picker = FileDialog(window as? Frame, Strings.media.importMedia(), FileDialog.LOAD)
            dialog = picker
            picker.setFilenameFilter { _, name ->
                name.substringAfterLast('.', "").lowercase() in
                    setOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "webm")
            }
            picker.setLocationRelativeTo(window)
            picker.isVisible = true
            val path = picker.file?.let { File(picker.directory, it).absolutePath }
            picker.dispose()
            if (continuation.isActive) continuation.resume(path)
        } catch (error: Exception) {
            dialog?.dispose()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
