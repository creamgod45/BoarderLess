package cg.creamgod.boarderless.data.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Await actual Web Lock grant, not an enqueued request. No localStorage TTL/steal fallback.
 * Same-tab repositories share a JS lock with independent tokens; cutover is separate. */
internal class BrowserServerWriterLease {
    private val mutex = Mutex()
    private val closed = MutableStateFlow(false)
    private val token = MutableStateFlow<String?>(null)
    suspend fun acquire() = mutex.withLock {
        currentCoroutineContext().ensureActive(); check(!closed.value)
        if (token.value != null) { requireActive(); return@withLock }
        val id = randomUuid(); token.value = id
        try {
            val granted = withTimeout(5_000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    continuation.invokeOnCancellation { browserReleaseServerWriterLease(id) }
                    if (continuation.isActive) browserAcquireServerWriterLease(id) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
            }
            currentCoroutineContext().ensureActive(); check(granted); requireActive()
        } catch (failure: Throwable) {
            browserReleaseServerWriterLease(id); token.value = null
            throw failure
        }
    }
    fun requireActive() {
        check(!closed.value && token.value?.let(::browserServerWriterLeaseHeld) == true) { "Browser server writer lease unavailable" }
    }
    fun close() {
        closed.value = true
        token.value?.let(::browserReleaseServerWriterLease); token.value = null
    }
}
internal expect fun browserAcquireServerWriterLease(id: String, done: (Boolean) -> Unit)
internal expect fun browserReleaseServerWriterLease(id: String)
internal expect fun browserServerWriterLeaseHeld(id: String): Boolean
