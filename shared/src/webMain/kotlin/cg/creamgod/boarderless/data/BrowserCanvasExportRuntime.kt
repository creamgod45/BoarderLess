package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.export.CanvasExportFormat
import cg.creamgod.boarderless.feature.canvas.CanvasExportDestination
import cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Blob dispatch is a download request; browsers cannot acknowledge completed disk storage. */
fun browserCanvasExportRuntime() = CanvasExportRuntime(usesBrowserDownload = true) { name, format, canWrite ->
    require(name.matches(Regex("[A-Za-z0-9_-]+\\.${format.extension}")))
    check(canWrite())
    BrowserCanvasExportDestination("${name.removeSuffix(".${format.extension}")}-${randomUuid()}.${format.extension}", format, canWrite)
}

internal class BrowserCanvasExportDestination(
    private val name: String,
    private val format: CanvasExportFormat,
    private val canWrite: () -> Boolean,
) : CanvasExportDestination {
    private val chunks = mutableListOf<ByteArray>()
    private var byteCount = 0L
    private var closed = false

    override suspend fun writeChunk(bytes: ByteArray) {
        check(!closed)
        currentCoroutineContext().ensureActive()
        check(canWrite())
        require(bytes.size <= 1024 * 1024 && byteCount + bytes.size <= 32L * 1024 * 1024)
        chunks += bytes.copyOf()
        byteCount += bytes.size
    }

    override suspend fun commit() {
        check(!closed && byteCount > 0)
        closed = true // No retry after a dispatch attempt, including an uncertain browser result.
        val context = currentCoroutineContext()
        try {
            context.ensureActive()
            check(canWrite())
            browserDispatchCanvasDownload(name, format.mediaType, chunks) {
                context.ensureActive()
                canWrite()
            }
        } finally { chunks.clear(); byteCount = 0 }
    }

    override suspend fun abort() { closed = true; chunks.clear(); byteCount = 0 }
}

internal expect fun browserDispatchCanvasDownload(
    name: String, mediaType: String, chunks: List<ByteArray>, canWrite: () -> Boolean,
)
