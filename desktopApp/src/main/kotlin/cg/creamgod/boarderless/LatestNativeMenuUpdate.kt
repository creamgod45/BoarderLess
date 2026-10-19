package cg.creamgod.boarderless

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Coalesces periodic updates without waiting for the native UI executor. */
internal class LatestNativeMenuUpdate<T : Any>(
    private val post: (() -> Unit) -> Unit,
    private val apply: (T) -> Unit,
) {
    private val latest = AtomicReference<T?>(null)
    private val queued = AtomicBoolean(false)

    fun offer(value: T) {
        latest.set(value)
        if (!queued.compareAndSet(false, true)) return
        try {
            post {
                try {
                    latest.get()?.let(apply)
                } finally {
                    queued.set(false)
                }
            }
        } catch (_: Exception) {
            queued.set(false)
        }
    }
}
