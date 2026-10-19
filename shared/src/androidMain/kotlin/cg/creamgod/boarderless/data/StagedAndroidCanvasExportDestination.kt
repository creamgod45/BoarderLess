package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.feature.canvas.CanvasExportDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal interface AndroidCanvasExportDocument {
    fun isBlank(): Boolean
    fun openOutput(): OutputStream
    suspend fun discardBlank()
}

/** SAF providers do not offer atomic publication. Stage privately, then copy once to a new
 * empty document. After publication begins, retain uncertain output for inspection. */
internal class StagedAndroidCanvasExportDestination(
    private val cache: File,
    private val canWrite: () -> Boolean,
    private val document: AndroidCanvasExportDocument,
    private val maximumBytes: Long = 32L * 1024 * 1024,
) : CanvasExportDestination {
    init { require(maximumBytes > 0) }
    private var stage: File? = null
    private var output: FileOutputStream? = null
    private var bytes = 0L
    private var closed = false
    private var publishing = false

    private suspend fun guard() {
        currentCoroutineContext().ensureActive()
        check(canWrite()) { "Canvas export scope changed" }
    }

    override suspend fun writeChunk(bytes: ByteArray) = withContext(Dispatchers.IO) {
        check(!closed && !publishing)
        require(bytes.size <= 1024 * 1024)
        guard()
        check(bytes.size.toLong() <= maximumBytes - this@StagedAndroidCanvasExportDestination.bytes)
        if (stage == null) {
            val file = File.createTempFile("canvas-export-", ".partial", cache)
            stage = file
            output = FileOutputStream(file)
        }
        checkNotNull(output).write(bytes)
        this@StagedAndroidCanvasExportDestination.bytes += bytes.size
    }

    override suspend fun commit() = withContext(Dispatchers.IO) {
        check(!closed && !publishing && bytes > 0)
        guard()
        checkNotNull(output).apply { flush(); fd.sync(); close() }
        output = null
        check(document.isBlank()) { "Destination is not a new empty document" }
        guard()
        publishing = true
        document.openOutput().use { destination ->
            checkNotNull(stage).inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    guard()
                    val count = input.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                }
                destination.flush()
            }
        }
        guard()
        closed = true
        removeStage()
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        if (!closed) {
            closed = true
            try { output?.close() } finally { output = null; removeStage() }
            if (!publishing) document.discardBlank()
        }
    }

    private fun removeStage() {
        stage?.let { check(!it.exists() || it.delete()) { "Unable to remove private export stage" } }
        stage = null
    }
}
