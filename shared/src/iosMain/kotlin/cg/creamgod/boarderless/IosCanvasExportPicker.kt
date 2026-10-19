@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime
import cg.creamgod.boarderless.data.IosCanvasExportDestination
import cg.creamgod.boarderless.data.IosSchemeBundleDestination
import cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** Directory permission only; no export bytes are written while the picker is open. */
internal class IosCanvasExportPicker(
    private val presenter: () -> UIViewController,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    private var pending: CancellableContinuation<NSURL?>? = null
    private var picker: UIDocumentPickerViewController? = null
    val runtime =
        CanvasExportRuntime { suggested, format, canWrite ->
            require(suggested.matches(Regex("[A-Za-z0-9_-]+\\.${format.extension}")))
            check(canWrite())
            withContext(Dispatchers.Main) { selectFolder() }?.let {
                check(canWrite())
                IosCanvasExportDestination(it, suggested.removeSuffix(".${format.extension}"), format, canWrite)
            }
        }

    val schemeBundleRuntime = SchemeBundleRuntime(chooseDestination = { suggested, canWrite ->
        require(suggested.matches(Regex("[A-Za-z0-9_-]+\\.blscheme")))
        check(canWrite())
        withContext(Dispatchers.Main) { selectFolder() }?.let {
            check(canWrite())
            IosSchemeBundleDestination.create(it, suggested, canWrite)
        }
    })

    private suspend fun selectFolder(): NSURL? =
        suspendCancellableCoroutine { request ->
            check(pending == null && presenter().presentedViewController == null) { "Close the current system panel first" }
            val opened = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
            opened.allowsMultipleSelection = false
            opened.delegate = this
            pending = request
            picker = opened
            request.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (pending === request) {
                        pending = null
                        picker = null
                        opened.dismissViewControllerAnimated(true, completion = null)
                    }
                }
            }
            presenter().presentViewController(opened, animated = true, completion = null)
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
        val request = pending
        pending = null
        picker = null
        controller.dismissViewControllerAnimated(true) { if (request?.isActive == true) request.resume(url) }
    }

    fun close() {
        val opened = picker
        val request = pending
        pending = null
        picker = null
        request?.cancel()
        opened?.dismissViewControllerAnimated(false, completion = null)
    }
}
