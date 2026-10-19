package cg.creamgod.boarderless.data.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Opaque platform writer-operation accounting. Closing a repository rejects new work
 * but cannot release ownership while a started synchronous storage action still runs. */
class ServerWriterOperationLease internal constructor(private val enter: () -> (() -> Unit),
    private val requireActive: () -> Unit) {
    internal fun <T> run(block: () -> T): T {
        val leave = enter()
        try { requireActive(); return block() } finally { leave() }
    }
    internal suspend fun <T> runAwaited(block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val leave = enter()
        try { requireActive(); return block() } finally { leave() }
    }
    /** Browser cancellation returns before IDB's terminal event. The bridge must keep
     * this ticket until complete/abort, even when the Kotlin continuation is cancelled. */
    internal fun beginPhysicalOperation(): () -> Unit {
        val leave = enter()
        try { requireActive(); return leave } catch (failure: Throwable) { leave(); throw failure }
    }
}
