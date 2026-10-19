@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*
import platform.posix.*

/** A private snapshot owns no provider grant after selection and never deletes the source. */
internal class IosSchemeBundleSource private constructor(private val path: String, override val byteSize: Long) : SchemeBundleSource {
    private val mutex = Mutex()
    private var released = false
    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = withContext(Dispatchers.Default) {
        mutex.withLock {
            currentCoroutineContext().ensureActive(); check(!released)
            require(offset in 0..byteSize && maximumBytes in 1..QuickSchemeBundleCodec.ChunkBytes)
            val fd = open(path, O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
            check(fd >= 0)
            try {
                memScoped { val info = alloc<stat>(); check(fstat(fd, info.ptr) == 0)
                    check(info.st_mode.toInt() and 0xf000 == 0x8000 && info.st_size == byteSize) }
                val bytes = ByteArray(minOf(maximumBytes.toLong(), byteSize - offset).toInt())
                if (bytes.isNotEmpty()) bytes.usePinned { pinned ->
                    var copied = 0
                    while (copied < bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = pread(fd, pinned.addressOf(copied), (bytes.size - copied).toULong(), offset + copied)
                        if (count < 0 && errno == EINTR) continue
                        check(count > 0); copied += count.toInt()
                    }
                }
                bytes
            } finally { check(close(fd) == 0) }
        }
    }
    override suspend fun release() = withContext(NonCancellable + Dispatchers.Default) {
        mutex.withLock { if (!released) { check(unlink(path) == 0 || errno == ENOENT); released = true }; Unit }
    }

    companion object {
        suspend fun snapshot(url: NSURL, canRead: () -> Boolean): IosSchemeBundleSource {
            var created: IosSchemeBundleSource? = null
            try {
                return withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val guard = { context.ensureActive(); canRead() }
                    check(guard() && url.isFileURL())
                    val granted = url.startAccessingSecurityScopedResource()
                    try {
                        check(granted || isOwnSandboxDraftUrl(url)) { "Bundle file access denied" }
                        var failure: Throwable? = null
                        memScoped {
                            val error = alloc<ObjCObjectVar<NSError?>>()
                            error.value = null
                            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, options = 0uL, error = error.ptr) { coordinated ->
                                try { created = copySnapshot(checkNotNull(checkNotNull(coordinated).path), guard) }
                                catch (caught: Throwable) { failure = caught }
                            }
                            failure?.let { throw it }
                            check(error.value == null && created != null) { "Bundle read coordination failed" }
                        }
                        check(guard()); checkNotNull(created)
                    } finally { if (granted) url.stopAccessingSecurityScopedResource() }
                }
            } catch (failure: Throwable) {
                try { withContext(NonCancellable) { created?.release() } } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }

        private fun copySnapshot(source: String, canRead: () -> Boolean): IosSchemeBundleSource {
            require(source.startsWith('/') && '\u0000' !in source); check(canRead())
            val input = open(source, O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
            check(input >= 0)
            var output = -1; var outputOpen = false; var ownsPart = false; var complete = false
            val part = NSTemporaryDirectory() + "boarderless-bundle-${NSUUID().UUIDString}.snapshot"
            try {
                val expected = memScoped { val info = alloc<stat>(); check(fstat(input, info.ptr) == 0)
                    check(info.st_mode.toInt() and 0xf000 == 0x8000)
                    require(info.st_size in 45..QuickSchemeBundleCodec.MaximumBundleBytes); info.st_size }
                check(canRead())
                output = open(part, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
                check(output >= 0); outputOpen = true; ownsPart = true
                val buffer = ByteArray(QuickSchemeBundleCodec.ChunkBytes); var size = 0L
                buffer.usePinned { pinned ->
                    while (true) {
                        check(canRead())
                        val count = read(input, pinned.addressOf(0), buffer.size.toULong())
                        if (count < 0 && errno == EINTR) continue
                        check(count >= 0); if (count == 0L) break
                        require(size + count <= expected)
                        var copied = 0
                        while (copied < count.toInt()) {
                            check(canRead())
                            val written = write(output, pinned.addressOf(copied), (count.toInt() - copied).toULong())
                            if (written < 0 && errno == EINTR) continue
                            check(written > 0); copied += written.toInt()
                        }
                        size += count
                    }
                }
                check(size == expected && canRead()); check(fsync(output) == 0)
                val result = close(output); outputOpen = false; check(result == 0)
                complete = true
                return IosSchemeBundleSource(part, size)
            } finally {
                var failure: Throwable? = null
                if (close(input) != 0) failure = IllegalStateException("Bundle source close failed")
                if (outputOpen && close(output) != 0) failure = IllegalStateException("Bundle snapshot close failed")
                if (ownsPart && (!complete || failure != null)) check(unlink(part) == 0 || errno == ENOENT)
                failure?.let { throw it }
            }
        }
    }
}
