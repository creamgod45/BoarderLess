@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.IosFileAssetTransferSource
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.IosAssetPreviewLoader
import cg.creamgod.boarderless.data.IosGifAnimation
import cg.creamgod.boarderless.data.loadIosGif
import cg.creamgod.boarderless.data.loadIosVideo
import cg.creamgod.boarderless.data.IosVideoSurface
import cg.creamgod.boarderless.data.bitmapPreviewCache
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.loadGiphyStill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

internal class IosMediaPicker(private val presenter: () -> UIViewController) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var continuation: CancellableContinuation<NSURL?>? = null
    private var picker: UIDocumentPickerViewController? = null

    private val previewPermits = Semaphore(2)
    val activityTracker = MediaActivityTracker()
    private val previewCache = bitmapPreviewCache()
    val runtime = MediaImportRuntime(clearPreviewCache = previewCache::clear, playbackActivity = activityTracker.activity,
    loadGiphyAnimation = { url ->
        cg.creamgod.boarderless.data.remote.loadGiphyAnimation(url) { IosGifAnimation.decode(it) }
    },
    loadGiphyStill = { url -> withContext(Dispatchers.Default) {
        loadGiphyStill(url) { IosAssetPreviewLoader.decodePreview(it) }
    } }, selectSource = {
        val url = selectDocument()
        url?.let { IosFileAssetTransferSource.fromUrl(it) }
    }, loadPreview = { session, assetId ->
        previewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { IosAssetPreviewLoader(gateway, previewCache).load(session, assetId) } finally { gateway.close() }
        }
    }, loadGif = { session, assetId ->
        previewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { loadIosGif(gateway, session, assetId) } finally { gateway.close() }
        }
    }, loadVideo = { session, assetId ->
        previewPermits.withPermit {
            val gateway = BackendAssetTransferGateway()
            try { loadIosVideo(gateway, session, assetId) } finally { gateway.close() }
        }
    }, videoSurface = { playback, modifier -> IosVideoSurface(playback, modifier) })

    private suspend fun selectDocument(): NSURL? = suspendCancellableCoroutine { pending ->
        check(continuation == null) { "A document selection is already active" }
        check(presenter().presentedViewController == null) { "Close the current system panel before selecting media" }
        val types = listOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm")
            .mapNotNull { UTType.typeWithMIMEType(it) }
        val documentPicker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        documentPicker.allowsMultipleSelection = false
        documentPicker.delegate = this
        continuation = pending
        picker = documentPicker
        pending.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                if (continuation === pending) {
                    continuation = null
                    picker = null
                    documentPicker.dismissViewControllerAnimated(true, completion = null)
                }
            }
        }
        presenter().presentViewController(documentPicker, animated = true, completion = null)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(controller, didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(controller, null)
    }

    private fun finish(controller: UIDocumentPickerViewController, url: NSURL?) {
        if (picker != controller) return
        val pending = continuation
        continuation = null
        picker = null
        controller.dismissViewControllerAnimated(true) {
            if (pending?.isActive == true) pending.resume(url)
        }
    }
}
