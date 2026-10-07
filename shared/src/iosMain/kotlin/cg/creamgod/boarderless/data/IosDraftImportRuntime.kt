@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*

/** External picker URL only; hold the grant exclusively around coordinated read, never bookmark. */
internal suspend fun readIosExternalDraftJson(
    url: NSURL,
    canRead: () -> Boolean,
): String =
    withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        try {
            context.ensureActive()
            check(canRead() && url.isFileURL())
            val granted = url.startAccessingSecurityScopedResource()
            try {
                // Own-container files need no external grant. Never infer access to arbitrary URLs.
                check(granted || isOwnSandboxDraftUrl(url))
                coordinateIosDraftRead(url) {
                    context.ensureActive()
                    canRead()
                }
            } finally {
                if (granted) url.stopAccessingSecurityScopedResource()
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: Exception) {
            throw IllegalArgumentException("Draft file could not be read")
        }
    }

internal fun isOwnSandboxDraftUrl(url: NSURL): Boolean {
    if (!url.isFileURL()) return false
    val home = NSURL.fileURLWithPath(NSHomeDirectory()).URLByResolvingSymlinksInPath?.path ?: return false
    val path = url.URLByResolvingSymlinksInPath?.path ?: return false
    return path.startsWith(home.trimEnd('/') + "/")
}

internal fun coordinateIosDraftRead(
    url: NSURL,
    canRead: () -> Boolean,
): String {
    check(url.isFileURL() && canRead())
    var result: String? = null
    var failure: Throwable? = null
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, options = 0uL, error = error.ptr) { coordinated ->
            try {
                check(canRead())
                result = readIosDraftJson(checkNotNull(checkNotNull(coordinated).path), canRead)
            } catch (caught: Throwable) {
                failure = caught
            }
        }
        failure?.let { throw it }
        check(error.value == null && result != null) { "Draft read coordination failed" }
    }
    check(canRead())
    return checkNotNull(result)
}

/** No symlinks/FIFOs; regular read-only fd checked before bytes, bounded despite file growth. */
internal fun readIosDraftJson(
    path: String,
    canRead: () -> Boolean,
): String {
    check(canRead())
    val fd = open(path, O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
    check(fd >= 0) { "Draft file could not be opened" }
    var completed = false
    try {
        memScoped {
            val metadata = alloc<stat>()
            check(fstat(fd, metadata.ptr) == 0)
            check(metadata.st_mode.toInt() and S_IFMT == S_IFREG)
            require(metadata.st_size in 1..(4 * 1024 * 1024).toLong())
        }
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            check(canRead())
            val count = buffer.usePinned { read(fd, it.addressOf(0), buffer.size.toULong()) }.toInt()
            check(count >= 0) { "Draft read failed" }
            check(canRead())
            if (count == 0) break
            require(total + count <= 4 * 1024 * 1024)
            chunks.add(buffer.copyOf(count))
            total += count
        }
        require(total > 0)
        val bytes = ByteArray(total)
        var offset = 0
        chunks.forEach {
            it.copyInto(bytes, offset)
            offset += it.size
        }
        check(canRead())
        val text = bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
        completed = true
        return text
    } finally {
        val closed = close(fd)
        if (completed) check(closed == 0) { "Draft read close failed" }
    }
}
