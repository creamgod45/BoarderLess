@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.export.CanvasExportFormat
import cg.creamgod.boarderless.feature.canvas.CanvasExportDestination
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.*

internal class IosCanvasExportDestination(
    private val folder: NSURL,
    private val prefix: String,
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
        chunks += bytes.copyOf(); byteCount += bytes.size
    }

    override suspend fun commit() = withContext(Dispatchers.Default) {
        check(!closed && byteCount > 0)
        closed = true
        val context = currentCoroutineContext()
        val guard = { context.ensureActive(); canWrite() }
        try {
            require(prefix.matches(Regex("[A-Za-z0-9_-]+")))
            check(guard())
            check(folder.isFileURL() && folder.startAccessingSecurityScopedResource()) { "Folder access denied" }
            try {
                val name = "$prefix-${NSUUID().UUIDString}.${format.extension}"
                val target = checkNotNull(folder.URLByAppendingPathComponent(name))
                coordinateIosCanvasExportWrite(target, chunks, guard)
            } finally { folder.stopAccessingSecurityScopedResource() }
        } finally { chunks.clear(); byteCount = 0 }
    }

    override suspend fun abort() { closed = true; chunks.clear(); byteCount = 0 }
}

internal fun coordinateIosCanvasExportWrite(target: NSURL, chunks: List<ByteArray>, canWrite: () -> Boolean) {
    check(target.isFileURL() && canWrite())
    var failure: Throwable? = null
    var written = false
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(target,
            options = NSFileCoordinatorWritingForReplacing, error = error.ptr) { coordinated ->
            try {
                writeIosCanvasExportNewFile(checkNotNull(checkNotNull(coordinated).path), chunks, canWrite)
                written = true
            } catch (caught: Throwable) { failure = caught }
        }
        failure?.let { throw it }
        check(error.value == null && written) { "Export file coordination failed" }
    }
}

/** Same-directory private stage, force, exclusive rename, then parent-directory force.
 * If post-publication durability fails, retain the final file for inspection. */
internal fun writeIosCanvasExportNewFile(path: String, chunks: List<ByteArray>, canWrite: () -> Boolean) {
    require(path.startsWith('/') && '\u0000' !in path)
    require(chunks.isNotEmpty() && chunks.sumOf { it.size.toLong() } in 1..32L * 1024 * 1024)
    require(chunks.all { it.size <= 1024 * 1024 })
    check(canWrite())
    val parent = path.substringBeforeLast('/')
    val partial = "$parent/.canvas-export-${NSUUID().UUIDString}.partial"
    val fd = open(partial, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
    check(fd >= 0) { "Unable to create export stage" }
    var descriptorOpen = true
    var published = false
    var failure: Throwable? = null
    try {
        for (chunk in chunks) if (chunk.isNotEmpty()) chunk.usePinned { pinned ->
            var offset = 0
            while (offset < chunk.size) {
                check(canWrite())
                val count = write(fd, pinned.addressOf(offset), (chunk.size - offset).toULong())
                if (count < 0 && errno == EINTR) continue
                check(count > 0) { "Export write failed" }
                offset += count.toInt()
            }
        }
        check(canWrite())
        check(fsync(fd) == 0) { "Export force failed" }
        val closeResult = close(fd); descriptorOpen = false
        check(closeResult == 0) { "Export close failed" }
        check(canWrite())
        check(renamex_np(partial, path, RENAME_EXCL.toUInt()) == 0) { "Unable to publish new export file" }
        published = true
        // Never remove path after this point, including a failed directory force.
        val directory = open(parent, O_RDONLY or O_DIRECTORY or O_NOFOLLOW)
        check(directory >= 0) { "Unable to open export directory for force" }
        try { check(fsync(directory) == 0) { "Export directory force failed" } }
        finally { check(close(directory) == 0) { "Export directory close failed" } }
    } catch (caught: Throwable) {
        failure = caught
        throw caught
    } finally {
        var cleanup: Throwable? = null
        if (descriptorOpen && close(fd) != 0) cleanup = IllegalStateException("Export stage close failed")
        if (!published && unlink(partial) != 0 && errno != ENOENT) {
            val removal = IllegalStateException("Unable to remove export stage")
            if (cleanup == null) cleanup = removal else cleanup.addSuppressed(removal)
        }
        cleanup?.let { if (failure == null) throw it else failure.addSuppressed(it) }
    }
}
