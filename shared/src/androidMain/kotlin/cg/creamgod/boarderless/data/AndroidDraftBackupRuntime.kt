package cg.creamgod.boarderless.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** chooseNewDocument must use ACTION_CREATE_DOCUMENT, never an existing-document picker. */
fun androidDraftBackupRuntime(context: Context, chooseNewDocument: suspend (String) -> Uri?) =
    DraftBackupRuntime { suggested ->
        chooseNewDocument(suggested)?.let { AndroidDraftBackupDestination(context.applicationContext, it) }
    }

/** A late result after recreation/cancellation contains only a newly-created blank document.
 * Never delete nonempty/unreadable results: keep them for the user's inspection instead.
 */
suspend fun discardBlankDraftDocument(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
    check(uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri))
    val resolver = context.contentResolver
    val empty = resolver.openInputStream(uri)?.use { it.read() == -1 } ?: false
    check(empty) { "Refusing to delete a nonempty or unreadable document" }
    check(DocumentsContract.deleteDocument(resolver, uri)) { "Unable to remove blank draft document" }
}

internal class AndroidDraftBackupDestination(private val context: Context, private val uri: Uri) : DraftBackupDestination {
    private var closed = false
    private var used = false
    private var completed = false
    private var verifiedEmpty = false

    override suspend fun write(json: String, canWrite: () -> Boolean) = withContext(Dispatchers.IO) {
        check(!closed && !used)
        used = true
        coroutineContext.ensureActive()
        check(canWrite()) { "Draft backup scope changed" }
        check(uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri))
        val resolver = context.contentResolver
        verifiedEmpty = resolver.openInputStream(uri)?.use { it.read() == -1 } ?: false
        check(verifiedEmpty) { "Destination is not a new empty document" }
        coroutineContext.ensureActive()
        check(canWrite()) { "Draft backup scope changed" }
        checkNotNull(resolver.openOutputStream(uri, "w")) { "Document cannot be opened" }.use { output ->
            coroutineContext.ensureActive()
            check(canWrite()) { "Draft backup scope changed" }
            output.write(json.toByteArray(Charsets.UTF_8))
            output.flush()
            coroutineContext.ensureActive()
            check(canWrite()) { "Draft backup scope changed" }
        }
        completed = true
    }

    override fun close() { closed = true }
    override suspend fun dispose() = withContext(Dispatchers.IO) {
        if (closed) return@withContext
        closed = true
        if (!completed) {
            if (verifiedEmpty) check(DocumentsContract.deleteDocument(context.contentResolver, uri))
            else discardBlankDraftDocument(context, uri)
        }
    }
}
