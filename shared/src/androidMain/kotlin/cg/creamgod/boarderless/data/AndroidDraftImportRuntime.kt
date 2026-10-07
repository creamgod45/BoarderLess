package cg.creamgod.boarderless.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*

/** ACTION_OPEN_DOCUMENT read-only grant; no persistable permission, cleanup deletion or upload. */
fun androidDraftImportRuntime(context: Context, chooseDocument: suspend () -> Uri?) = DraftImportRuntime { canRead ->
    currentCoroutineContext().ensureActive()
    check(canRead())
    val uri = chooseDocument()
    currentCoroutineContext().ensureActive()
    check(canRead())
    if (uri == null) null else withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            check(canRead() && uri.scheme == "content")
            checkNotNull(context.applicationContext.contentResolver.openInputStream(uri)).use { input ->
                readDraftJsonChunks(canRead) { maximum ->
                    val buffer = ByteArray(maximum)
                    val count = input.read(buffer)
                    check(count != 0) // A stalled provider is not EOF; no busy loop.
                    if (count < 0) byteArrayOf() else buffer.copyOf(count)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw IllegalArgumentException("Draft file could not be read") }
    }
}
