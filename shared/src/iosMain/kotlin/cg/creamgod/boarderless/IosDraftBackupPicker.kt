@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.DraftBackupRuntime
import cg.creamgod.boarderless.data.IosDraftBackupDestination
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

/** Directory permission only; no private JSON file exists while the picker is open. */
internal class IosDraftBackupPicker(
    private val presenter: () -> UIViewController,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    private var pending: CancellableContinuation<NSURL?>? = null
    private var picker: UIDocumentPickerViewController? = null
    val runtime =
        DraftBackupRuntime(choosesFolder = true) { suggested ->
            require(suggested.matches(Regex("[A-Za-z0-9_-]+\\.json")))
            withContext(Dispatchers.Main) { selectFolder() }?.let { IosDraftBackupDestination(it, suggested.removeSuffix(".json")) }
        }

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
