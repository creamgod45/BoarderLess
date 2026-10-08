@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.feature.canvas.QuickSchemeBundleCodec
import cg.creamgod.boarderless.feature.canvas.SchemeBundleDestination
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*
import platform.posix.*

/** Full-original bundle staging uses a file, not an in-memory list of media chunks. */
internal class IosSchemeBundleDestination private constructor(
    private val part: String,
    private var descriptor: Int,
    private val publish: (String, Long, () -> Boolean) -> Unit,
    private val canWrite: () -> Boolean,
) : SchemeBundleDestination {
    private val mutex = Mutex()
    private var size = 0L
    private var closed = false

    override suspend fun writeChunk(bytes: ByteArray) = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(!closed && descriptor >= 0)
            currentCoroutineContext().ensureActive()
            check(canWrite())
            require(bytes.size <= 1024 * 1024 && size + bytes.size <= QuickSchemeBundleCodec.MaximumBundleBytes)
            if (bytes.isNotEmpty()) bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive(); check(canWrite())
                    val count = write(descriptor, pinned.addressOf(offset), (bytes.size - offset).toULong())
                    if (count < 0 && errno == EINTR) continue
                    check(count > 0) { "Bundle stage write failed" }
                    offset += count.toInt()
                }
            }
            size += bytes.size
        }
    }

    override suspend fun commit() = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(!closed && descriptor >= 0 && size >= 45)
            closed = true
            val context = currentCoroutineContext()
            val guard = { context.ensureActive(); canWrite() }
            var failure: Throwable? = null
            try {
                check(guard()); check(fsync(descriptor) == 0) { "Bundle stage force failed" }
                closeDescriptor()
                check(guard())
                publish(part, size, guard)
            } catch (caught: Throwable) { failure = caught; throw caught }
            finally {
                withContext(NonCancellable) {
                    try { cleanup() } catch (cleanup: Throwable) {
                        if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
                    }
                }
            }
        }
    }

    override suspend fun abort() = withContext(NonCancellable + Dispatchers.Default) {
        mutex.withLock { closed = true; cleanup() }
    }

    private fun closeDescriptor() {
        if (descriptor >= 0) {
            val owned = descriptor; descriptor = -1
            check(close(owned) == 0) { "Bundle stage close failed" }
        }
    }
    private fun cleanup() {
        var failure: Throwable? = null
        try { closeDescriptor() } catch (caught: Throwable) { failure = caught }
        if (unlink(part) != 0 && errno != ENOENT) {
            val removal = IllegalStateException("Bundle stage removal failed")
            if (failure == null) failure = removal else failure.addSuppressed(removal)
        }
        failure?.let { throw it }
    }

    companion object {
        suspend fun create(folder: NSURL, suggested: String, canWrite: () -> Boolean): IosSchemeBundleDestination {
            require(suggested.matches(Regex("[A-Za-z0-9_-]+\\.blscheme")))
            val prefix = suggested.removeSuffix(".blscheme")
            return create(canWrite) { source, bytes, guard ->
                check(guard() && folder.isFileURL() && folder.startAccessingSecurityScopedResource()) { "Folder access denied" }
                try {
                    val target = checkNotNull(folder.URLByAppendingPathComponent("$prefix-${NSUUID().UUIDString}.blscheme"))
                    coordinateIosSchemeBundleWrite(source, target, bytes, guard)
                } finally { folder.stopAccessingSecurityScopedResource() }
            }
        }

        /** Injectable publication boundary for native byte/cancellation fixtures. */
        internal suspend fun create(canWrite: () -> Boolean, publish: (String, Long, () -> Boolean) -> Unit): IosSchemeBundleDestination {
            var created: IosSchemeBundleDestination? = null
            try {
                return withContext(Dispatchers.Default) {
                    currentCoroutineContext().ensureActive(); check(canWrite())
                    val part = NSTemporaryDirectory() + "boarderless-bundle-${NSUUID().UUIDString}.partial"
                    val descriptor = open(part, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
                    check(descriptor >= 0) { "Unable to create bundle stage" }
                    IosSchemeBundleDestination(part, descriptor, publish, canWrite).also { created = it }
                }
            } catch (failure: Throwable) {
                withContext(NonCancellable) { created?.abort() }
                throw failure
            }
        }
    }
}

internal fun coordinateIosSchemeBundleWrite(source: String, target: NSURL, bytes: Long, canWrite: () -> Boolean) {
    check(target.isFileURL() && canWrite())
    var failure: Throwable? = null
    var written = false
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(target,
            options = NSFileCoordinatorWritingForReplacing, error = error.ptr) { coordinated ->
            try {
                writeIosSchemeBundleNewFile(source, checkNotNull(checkNotNull(coordinated).path), bytes, canWrite)
                written = true
            } catch (caught: Throwable) { failure = caught }
        }
        failure?.let { throw it }
        check(error.value == null && written) { "Bundle file coordination failed" }
    }
}

/** Same-directory private stage, bounded copy, fsync, exclusive publication and directory fsync.
 * A post-publication failure keeps the final file; cleanup owns only the two temporary stages. */
internal fun writeIosSchemeBundleNewFile(source: String, path: String, bytes: Long, canWrite: () -> Boolean) {
    require(source.startsWith('/') && path.startsWith('/') && '\u0000' !in source && '\u0000' !in path)
    require(bytes in 45..QuickSchemeBundleCodec.MaximumBundleBytes)
    check(canWrite())
    val parent = path.substringBeforeLast('/')
    val part = "$parent/.scheme-bundle-${NSUUID().UUIDString}.partial"
    val input = open(source, O_RDONLY or O_NOFOLLOW)
    check(input >= 0) { "Unable to open bundle stage" }
    var output = -1
    var outputOpen = false
    var ownsPart = false
    var published = false
    var failure: Throwable? = null
    try {
        memScoped {
            val stat = alloc<stat>(); check(fstat(input, stat.ptr) == 0)
            check(stat.st_mode.toInt() and S_IFMT == S_IFREG && stat.st_size == bytes) { "Bundle stage changed" }
        }
        output = open(part, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
        check(output >= 0) { "Unable to create bundle publication stage" }
        outputOpen = true; ownsPart = true
        val buffer = ByteArray(1024 * 1024)
        var copied = 0L
        buffer.usePinned { pinned ->
            while (true) {
                check(canWrite())
                val count = read(input, pinned.addressOf(0), buffer.size.toULong())
                if (count < 0 && errno == EINTR) continue
                check(count >= 0) { "Bundle stage read failed" }
                if (count == 0L) break
                check(copied + count <= bytes) { "Bundle stage grew" }
                var offset = 0
                while (offset < count.toInt()) {
                    check(canWrite())
                    val written = write(output, pinned.addressOf(offset), (count.toInt() - offset).toULong())
                    if (written < 0 && errno == EINTR) continue
                    check(written > 0) { "Bundle publication write failed" }
                    offset += written.toInt()
                }
                copied += count
            }
        }
        check(copied == bytes && canWrite()) { "Bundle stage truncated or scope changed" }
        check(fsync(output) == 0) { "Bundle publication force failed" }
        val result = close(output); outputOpen = false
        check(result == 0) { "Bundle publication close failed" }
        check(canWrite())
        check(renamex_np(part, path, RENAME_EXCL.toUInt()) == 0) { "Unable to publish new bundle file" }
        published = true
        val directory = open(parent, O_RDONLY or O_DIRECTORY or O_NOFOLLOW)
        check(directory >= 0) { "Unable to open bundle directory" }
        try { check(fsync(directory) == 0) { "Bundle directory force failed" } }
        finally { check(close(directory) == 0) { "Bundle directory close failed" } }
    } catch (caught: Throwable) { failure = caught; throw caught }
    finally {
        var cleanup: Throwable? = null
        if (close(input) != 0) cleanup = IllegalStateException("Bundle input close failed")
        if (outputOpen && close(output) != 0) {
            val caught = IllegalStateException("Bundle output close failed")
            if (cleanup == null) cleanup = caught else cleanup.addSuppressed(caught)
        }
        if (ownsPart && !published && unlink(part) != 0 && errno != ENOENT) {
            val caught = IllegalStateException("Bundle publication stage removal failed")
            if (cleanup == null) cleanup = caught else cleanup.addSuppressed(caught)
        }
        cleanup?.let { if (failure == null) throw it else failure.addSuppressed(it) }
    }
}
