@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.IosAssetPreviewLoader
import cg.creamgod.boarderless.data.IosFileAssetTransferSource
import cg.creamgod.boarderless.data.IosGifAnimation
import cg.creamgod.boarderless.data.IosVideoSurface
import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.bitmapPreviewCache
import cg.creamgod.boarderless.data.loadIosGif
import cg.creamgod.boarderless.data.loadIosPhotoSelection
import cg.creamgod.boarderless.data.loadIosVideo
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.loadGiphyStill
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.PhotosUI.*
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

internal class IosMediaPicker(
    private val presenter: () -> UIViewController,
) : NSObject(),
    UIDocumentPickerDelegateProtocol,
    PHPickerViewControllerDelegateProtocol {
    private var continuation: CancellableContinuation<NSURL?>? = null
    private var picker: UIDocumentPickerViewController? = null
    private var photoPicker: PHPickerViewController? = null
    private var photoContinuation: CancellableContinuation<NSItemProvider?>? = null
    private var sourceContinuation: CancellableContinuation<Boolean?>? = null
    private var sourceMenu: UIAlertController? = null
    private var closed = false

    private val previewPermits = Semaphore(2)
    val activityTracker = MediaActivityTracker()
    private val previewCache = bitmapPreviewCache()
    val runtime =
        MediaImportRuntime(
            clearPreviewCache = previewCache::clear,
            playbackActivity = activityTracker.activity,
            loadGiphyAnimation = { url ->
                cg.creamgod.boarderless.data.remote
                    .loadGiphyAnimation(url) { IosGifAnimation.decode(it) }
            },
            loadGiphyStill = { url ->
                withContext(Dispatchers.Default) {
                    loadGiphyStill(url) { IosAssetPreviewLoader.decodePreview(it) }
                }
            },
            selectSource = {
                when (withContext(Dispatchers.Main) { chooseSource() }) {
                    true -> withContext(Dispatchers.Main) { selectPhoto() }?.let { loadIosPhotoSelection(it) }
                    false -> withContext(Dispatchers.Main) { selectDocument() }?.let { IosFileAssetTransferSource.fromUrl(it) }
                    null -> null
                }
            },
            loadPreview = { session, assetId ->
                previewPermits.withPermit {
                    val gateway = BackendAssetTransferGateway()
                    try {
                        IosAssetPreviewLoader(gateway, previewCache).load(session, assetId)
                    } finally {
                        gateway.close()
                    }
                }
            },
            loadGif = { session, assetId ->
                previewPermits.withPermit {
                    val gateway = BackendAssetTransferGateway()
                    try {
                        loadIosGif(gateway, session, assetId)
                    } finally {
                        gateway.close()
                    }
                }
            },
            loadVideo = { session, assetId ->
                previewPermits.withPermit {
                    val gateway = BackendAssetTransferGateway()
                    try {
                        loadIosVideo(gateway, session, assetId)
                    } finally {
                        gateway.close()
                    }
                }
            },
            videoSurface = { playback, modifier -> IosVideoSurface(playback, modifier) },
        )

    private suspend fun chooseSource(): Boolean? =
        suspendCancellableCoroutine { pending ->
            check(!closed && sourceContinuation == null && continuation == null && photoContinuation == null)
            check(presenter().presentedViewController == null) { "Close the current system panel before selecting media" }
            val menu = UIAlertController.alertControllerWithTitle(Strings.media.importSourceTitle(), null, UIAlertControllerStyleAlert)
            sourceContinuation = pending
            sourceMenu = menu
            var finishing = false

            fun finish(choice: Boolean?) {
                // Keep request ownership through dismissal, so disposal/cancel cannot open another picker.
                if (!finishing && sourceContinuation === pending) {
                    finishing = true
                    menu.dismissViewControllerAnimated(true) {
                        if (sourceContinuation === pending) {
                            sourceContinuation = null
                            sourceMenu = null
                            if (!closed && pending.isActive) pending.resume(choice)
                        }
                    }
                }
            }
            menu.addAction(UIAlertAction.actionWithTitle(Strings.media.importFromPhotos(), UIAlertActionStyleDefault) { finish(true) })
            menu.addAction(UIAlertAction.actionWithTitle(Strings.media.importFromFiles(), UIAlertActionStyleDefault) { finish(false) })
            menu.addAction(UIAlertAction.actionWithTitle(Strings.media.importSourceCancel(), UIAlertActionStyleCancel) { finish(null) })
            pending.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (sourceContinuation === pending) {
                        sourceContinuation = null
                        sourceMenu = null
                        menu.dismissViewControllerAnimated(true, completion = null)
                    }
                }
            }
            presenter().presentViewController(menu, animated = true, completion = null)
        }

    private suspend fun selectPhoto(): NSItemProvider? =
        suspendCancellableCoroutine { pending ->
            check(!closed && photoContinuation == null && presenter().presentedViewController == null)
            val configuration = iosMediaPhotoConfiguration()
            val opened = PHPickerViewController(configuration)
            opened.delegate = this
            photoContinuation = pending
            photoPicker = opened
            pending.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (photoContinuation === pending) {
                        photoContinuation = null
                        photoPicker = null
                        opened.dismissViewControllerAnimated(true, completion = null)
                    }
                }
            }
            presenter().presentViewController(opened, animated = true, completion = null)
        }

    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        // Objective-C may bridge the same native controller through a different Kotlin wrapper.
        // Reference identity would ignore BOTH selection and the system X/cancel callback.
        if (!isCurrentIosPhotoPicker(photoPicker, picker)) return
        val pending = photoContinuation
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        picker.dismissViewControllerAnimated(true) {
            if (photoContinuation === pending) {
                photoContinuation = null
                photoPicker = null
                if (!closed && pending?.isActive == true) pending.resume(provider)
            }
        }
    }

    private suspend fun selectDocument(): NSURL? =
        suspendCancellableCoroutine { pending ->
            check(!closed && continuation == null) { "A document selection is already active" }
            check(presenter().presentedViewController == null) { "Close the current system panel before selecting media" }
            val types =
                listOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm")
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

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        finish(controller, didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(controller, null)
    }

    private fun finish(
        controller: UIDocumentPickerViewController,
        url: NSURL?,
    ) {
        if (picker != controller) return
        val pending = continuation
        controller.dismissViewControllerAnimated(true) {
            if (continuation === pending) {
                continuation = null
                picker = null
                if (!closed && pending?.isActive == true) pending.resume(url)
            }
        }
    }

    fun close() {
        closed = true
        val opened = sourceMenu ?: photoPicker ?: picker
        val menuRequest = sourceContinuation
        val photoRequest = photoContinuation
        val fileRequest = continuation
        sourceMenu = null
        photoPicker = null
        picker = null
        sourceContinuation = null
        photoContinuation = null
        continuation = null
        menuRequest?.cancel()
        photoRequest?.cancel()
        fileRequest?.cancel()
        opened?.dismissViewControllerAnimated(false, completion = null)
    }
}

internal fun iosMediaPhotoConfiguration() =
    PHPickerConfiguration().apply {
        selectionLimit = 1
        filter = PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter))
        preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCompatible
    }

internal fun isCurrentIosPhotoPicker(
    expected: PHPickerViewController?,
    incoming: PHPickerViewController,
): Boolean = expected != null && expected == incoming
