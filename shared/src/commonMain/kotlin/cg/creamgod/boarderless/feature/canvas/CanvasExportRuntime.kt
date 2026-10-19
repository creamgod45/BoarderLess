package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.export.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A new file. writeChunk stages bytes; commit starts publication. Platforms without atomic
 * document replacement can retain uncertain output if commit fails. abort must preserve it. */
interface CanvasExportDestination {
    suspend fun writeChunk(bytes: ByteArray)
    suspend fun commit()
    suspend fun abort()
    /** Available after commit has confirmed publication. */
    val savedLocation: String? get() = null
    val revealSavedFile: (() -> Unit)? get() = null
}

class CanvasExportRuntime(
    val usesBrowserDownload: Boolean = false,
    val maxDimension: Int = 4096,
    val maxPixels: Long = 4_194_304,
    val chooseDestination: (suspend (name: String, format: CanvasExportFormat, canWrite: () -> Boolean) -> CanvasExportDestination?)? = null,
) {
    companion object { val Unavailable = CanvasExportRuntime() }
}

/** Caller owns frozen pixels and a fresh session/snapshot guard; no canvas operation is created. */
internal suspend fun publishCanvasExport(
    raster: CanvasExportRaster, format: CanvasExportFormat,
    destination: CanvasExportDestination, onPublishing: () -> Unit = {}, canWrite: () -> Boolean,
) {
    try {
        currentCoroutineContext().ensureActive()
        check(canWrite()) { "Canvas export scope changed" }
        val bytes = withContext(Dispatchers.Default) { raster.encode(format) }
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            check(canWrite()) { "Canvas export scope changed" }
            val end = minOf(offset + 1024 * 1024, bytes.size)
            destination.writeChunk(bytes.copyOfRange(offset, end))
            offset = end
        }
        currentCoroutineContext().ensureActive()
        check(canWrite()) { "Canvas export scope changed" }
        onPublishing()
        destination.commit()
    } catch (failure: Throwable) {
        try { withContext(NonCancellable) { destination.abort() } }
        catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
        throw failure
    }
}
