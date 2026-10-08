package cg.creamgod.boarderless.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import cg.creamgod.boarderless.data.persistence.AndroidAtomicLocalRecordStore
import cg.creamgod.boarderless.data.persistence.PersistentSchemeBundleImportReceipts
import java.io.File
import cg.creamgod.boarderless.feature.canvas.QuickSchemeBundleCodec
import cg.creamgod.boarderless.feature.canvas.SchemeBundleDestination
import cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Full BLQS0001 originals, streamed to a private stage before one SAF publication attempt.
 * Import reviews a private snapshot and records pending IDs durably before completion. */
fun androidSchemeBundleRuntime(
    context: Context,
    chooseNewDocument: suspend (String) -> Uri?,
    chooseExistingDocument: suspend () -> Uri?,
) = SchemeBundleRuntime(chooseSource = { canRead ->
    check(canRead())
    val uri = chooseExistingDocument()
    if (uri == null) null else {
        val app = context.applicationContext
        check(canRead() && uri.scheme == "content")
        AndroidSchemeBundleSource.snapshot(app.cacheDir,canRead) {
            checkNotNull(app.contentResolver.openInputStream(uri))
        }
    }
}, receipts = { owner, digest, assets ->
    PersistentSchemeBundleImportReceipts(AndroidAtomicLocalRecordStore(File(context.applicationContext.filesDir.canonicalFile,"scheme-bundle-receipts")),owner,digest,assets)
}, chooseDestination = { name, canWrite ->
    require(name.endsWith(".blscheme") && name.length in 1..180 && name.none { it == '/' || it == '\\' || it.code < 32 })
    check(canWrite())
    val uri = chooseNewDocument(name)
    if (uri == null) null else {
        val app = context.applicationContext
        check(uri.scheme == "content" && DocumentsContract.isDocumentUri(app, uri))
        if (!canWrite()) {
            withContext(NonCancellable) { discardBlankDraftDocument(app, uri) }
            error("Scheme bundle scope changed")
        }
        val resolver = app.contentResolver
        val stage = StagedAndroidCanvasExportDestination(app.cacheDir, canWrite, object : AndroidCanvasExportDocument {
            override fun isBlank() = resolver.openInputStream(uri)?.use { it.read() == -1 } ?: false
            override fun openOutput() = checkNotNull(resolver.openOutputStream(uri, "w"))
            override suspend fun discardBlank() = discardBlankDraftDocument(app, uri)
        }, QuickSchemeBundleCodec.MaximumBundleBytes)
        androidSchemeBundleDestination(stage)
    }
})

internal fun androidSchemeBundleDestination(stage: StagedAndroidCanvasExportDestination) = object : SchemeBundleDestination {
    override suspend fun writeChunk(bytes: ByteArray) = stage.writeChunk(bytes)
    override suspend fun commit() = stage.commit()
    override suspend fun abort() = stage.abort()
}
