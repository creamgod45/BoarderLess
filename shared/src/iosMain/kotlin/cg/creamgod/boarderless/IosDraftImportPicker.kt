@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.DraftImportRuntime
import cg.creamgod.boarderless.data.readIosExternalDraftJson
import kotlinx.coroutines.*
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.*
import kotlin.coroutines.resume

internal class IosDraftImportPicker(
    private val presenter: () -> UIViewController,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    private var pending: CancellableContinuation<NSURL?>? = null
    private var picker: UIDocumentPickerViewController? = null
    private var closed = false
    val runtime =
        DraftImportRuntime { canRead ->
            currentCoroutineContext().ensureActive()
            check(canRead())
            val url = withContext(Dispatchers.Main) { selectJson() }
            currentCoroutineContext().ensureActive()
            check(canRead())
            url?.let { readIosExternalDraftJson(it, canRead) }
        }

    private suspend fun selectJson(): NSURL? =
        suspendCancellableCoroutine { request ->
            check(!closed && pending == null && presenter().presentedViewController == null)
            val opened = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = false)
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
        if (closed || picker != controller) return
        val request = pending
        picker = null
        // Keep ownership through the animation: disposal must still cancel this continuation.
        controller.dismissViewControllerAnimated(true) {
            if (pending === request) pending = null
            if (!closed && request?.isActive == true) request.resume(url)
        }
    }

    fun close() {
        closed = true
        val opened = picker
        val request = pending
        picker = null
        pending = null
        request?.cancel()
        opened?.dismissViewControllerAnimated(false, completion = null)
    }
}
