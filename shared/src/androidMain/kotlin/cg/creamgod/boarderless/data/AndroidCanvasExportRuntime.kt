package cg.creamgod.boarderless.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import cg.creamgod.boarderless.domain.export.CanvasExportFormat
import cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The picker must create a new document, never return an existing file. */
fun androidCanvasExportRuntime(
    context: Context,
    chooseNewDocument: suspend (String, CanvasExportFormat) -> Uri?,
) = CanvasExportRuntime { name, format, canWrite ->
    check(canWrite())
    val uri = chooseNewDocument(name, format)
    if (uri == null) null else {
        if (!canWrite()) {
            withContext(NonCancellable) { discardBlankDraftDocument(context, uri) }
            error("Canvas export scope changed")
        }
        val app = context.applicationContext
        check(uri.scheme == "content" && DocumentsContract.isDocumentUri(app, uri))
        val resolver = app.contentResolver
        StagedAndroidCanvasExportDestination(app.cacheDir, canWrite, object : AndroidCanvasExportDocument {
            override fun isBlank() = resolver.openInputStream(uri)?.use { it.read() == -1 } ?: false
            override fun openOutput() = checkNotNull(resolver.openOutputStream(uri, "w"))
            override suspend fun discardBlank() = discardBlankDraftDocument(app, uri)
        })
    }
}
