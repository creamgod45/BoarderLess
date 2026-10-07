package cg.creamgod.boarderless.feature.canvas

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Native loaders may finish or throw after cancellation; neither may publish stale UI state. */
internal suspend fun <T> awaitActiveMediaRead(read: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    val result = try {
        read()
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        throw error
    }
    currentCoroutineContext().ensureActive()
    return result
}
