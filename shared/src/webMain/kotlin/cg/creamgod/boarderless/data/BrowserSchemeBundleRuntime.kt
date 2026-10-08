package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.persistence.browserSchemeBundleReceipts
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

/** Full original bytes, private disk stages and browser download dispatch; no heap-sized bundle. */
fun browserSchemeBundleRuntime() = SchemeBundleRuntime(
    chooseSource = { canRead ->
        val token = bundleOperation("pick", guard = canRead, ownsResult = true)
        token?.let {
            try {
                val size = requireNotNull(bundleOperation("size", token, guard = canRead)).toLong()
                require(size in 45..QuickSchemeBundleCodec.MaximumBundleBytes)
                BrowserSchemeBundleSource(token, size, canRead)
            } catch (failure: Throwable) { browserReleaseBundle(token); throw failure }
        }
    },
    chooseDestination = { name, canWrite ->
        require(name.matches(Regex("[A-Za-z0-9_-]+\\.blscheme")))
        val uniqueName = "${name.removeSuffix(".blscheme")}-${randomUuid()}.blscheme"
        val token = requireNotNull(bundleOperation("create", argument = uniqueName, guard = canWrite, ownsResult = true))
        BrowserSchemeBundleDestination(token, canWrite)
    },
    receipts = ::browserSchemeBundleReceipts,
    usesBrowserDownload = true,
    acknowledgeIdleDownloads = { bundleOperation("acknowledgeIdle"); Unit },
)

private suspend fun bundleOperation(
    action: String, token: String = "", argument: String = "", offset: Long = 0, maximumBytes: Int = 0,
    guard: () -> Boolean = { true }, ownsResult: Boolean = false,
): String? = suspendCancellableCoroutine { pending ->
    val operationId = randomUuid()
    pending.invokeOnCancellation { browserCancelBundleOperation(operationId) }
    if (pending.isActive) {
        browserBundleOperation(operationId, action, token, argument, offset.toDouble(), maximumBytes, guard) { result, error ->
            if (!pending.isActive) {
                if (ownsResult && result != null) browserReleaseBundle(result)
            } else if (error != null) {
                pending.resumeWithException(IllegalStateException("Scheme bundle file operation failed"))
            } else {
                pending.resume(result, onCancellation = { _, resource, _ ->
                    if (ownsResult && resource != null) browserReleaseBundle(resource)
                })
            }
        }
    }
}

private class BrowserSchemeBundleSource(
    private val token: String, override val byteSize: Long, private val canRead: () -> Boolean,
) : SchemeBundleSource {
    private val mutex = Mutex()
    private var released = false
    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = mutex.withLock {
        check(!released && canRead())
        require(offset >= 0 && maximumBytes in 1..QuickSchemeBundleCodec.ChunkBytes && offset + maximumBytes <= byteSize)
        val encoded = requireNotNull(bundleOperation("read", token, offset = offset, maximumBytes = maximumBytes, guard = canRead))
        currentCoroutineContext().ensureActive()
        check(canRead())
        Base64.decode(encoded).also { require(it.size == maximumBytes) }
    }
    override suspend fun release() = withContext(NonCancellable) {
        mutex.withLock {
            if (!released) { released = true; bundleOperation("drop", token) }
            Unit
        }
    }
}

private class BrowserSchemeBundleDestination(
    private val token: String, private val canWrite: () -> Boolean,
) : SchemeBundleDestination {
    private val mutex = Mutex()
    private var closed = false
    private var bytesWritten = 0L
    override suspend fun writeChunk(bytes: ByteArray) = mutex.withLock {
        check(!closed && canWrite())
        require(bytes.isNotEmpty() && bytes.size <= QuickSchemeBundleCodec.ChunkBytes &&
            bytesWritten + bytes.size <= QuickSchemeBundleCodec.MaximumBundleBytes)
        bundleOperation("append", token, Base64.encode(bytes), guard = canWrite)
        currentCoroutineContext().ensureActive()
        check(canWrite())
        bytesWritten += bytes.size
    }
    override suspend fun commit() = mutex.withLock {
        check(!closed && bytesWritten >= 45 && canWrite())
        closed = true // Never retry an uncertain download dispatch.
        try { bundleOperation("commit", token, guard = canWrite); Unit }
        catch (failure: Throwable) { browserReleaseBundle(token); throw failure }
    }
    override suspend fun acknowledgeDownload() = withContext(NonCancellable) {
        mutex.withLock { check(closed); bundleOperation("acknowledge", token); Unit }
    }
    override suspend fun abort() = withContext(NonCancellable) {
        mutex.withLock { closed = true; bundleOperation("drop", token); Unit }
    }
}

internal expect fun browserBundleOperation(
    id: String, action: String, token: String, argument: String, offset: Double, maximumBytes: Int,
    guard: () -> Boolean, done: (String?, String?) -> Unit,
)
internal expect fun browserCancelBundleOperation(id: String)
internal expect fun browserReleaseBundle(token: String)
