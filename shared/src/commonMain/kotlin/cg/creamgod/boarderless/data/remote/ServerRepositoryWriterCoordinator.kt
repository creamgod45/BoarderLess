package cg.creamgod.boarderless.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Single cooperating repository writer for the platform's legacy backend Settings
 * namespace. Candidate construction does not claim it. No takeover, timeout-based reset/TTL, or
 * automatic retirement of a live repository; its owner must close it explicitly.
 * Does not cover direct Settings access, old builds, or prove migration quiescence. */
internal object ServerRepositoryWriterCoordinator {
    private val active = MutableStateFlow<Owner?>(null)
    fun candidate(): Owner = Owner()

    class Owner internal constructor() {
        private enum class Settlement { Pending, Complete, Failed }
        private data class State(val closed: Boolean = false, val operations: Int = 0,
            val drained: Boolean = false, val onDrained: (() -> Unit)? = null)
        private val state = MutableStateFlow(State())
        private val settlement = MutableStateFlow(Settlement.Pending)
        /** Wait only for an owner already explicitly closed. A live owner is never
         * cancelled or retired here. Timeout retains ownership and all original records. */
        suspend fun claimAfterDrain() {
            currentCoroutineContext().ensureActive()
            check(!state.value.closed)
            val previous = active.value
            if (previous != null && previous !== this) {
                check(previous.state.value.closed) { "Another live repository owns server storage" }
                withTimeout(30_000) { previous.awaitClosedAndDrained() }
            }
            currentCoroutineContext().ensureActive()
            claim() // CAS still arbitrates competing candidates; never silently retry a loser.
        }
        suspend fun awaitClosedAndDrained() {
            check(state.value.closed) { "Close the owner before awaiting storage drain" }
            val result = settlement.first { it != Settlement.Pending }
            currentCoroutineContext().ensureActive()
            check(result == Settlement.Complete && state.value.operations == 0 && active.value !== this) {
                "Repository writer did not finish releasing storage"
            }
        }
        /** Explicit recovery of THIS owner's failed release only. Never revive its writer,
         * drop running-operation accounting, take over another owner or retry automatically.
         * The original release callback must be idempotent (e.g. a platform lease close). */
        fun retryFailedRelease() {
            val before = state.value
            check(before.closed && before.drained && before.operations == 0 &&
                settlement.value == Settlement.Failed && active.value === this) { "Failed writer release is not recoverable now" }
            // Reserve the retry before opening drain again. A concurrent close() may run
            // drain immediately; it must not complete and then get reset to Pending here.
            check(settlement.compareAndSet(Settlement.Failed, Settlement.Pending)) { "Writer release retry already started" }
            if (!state.compareAndSet(before, before.copy(drained = false))) {
                settlement.compareAndSet(Settlement.Pending, Settlement.Failed)
                error("Writer release state changed")
            }
            drain()
        }
        fun claim() {
            check(!state.value.closed) { "Repository writer was closed" }
            if (active.value !== this) {
                check(active.compareAndSet(null, this) || active.value === this) { "Another repository still owns server storage" }
            }
            try { requireActive() }
            catch (failure: Throwable) {
                drain()
                // close may drain while claim is between its first check and CAS.
                // That late CAS must not strand an already-closed owner in the slot.
                val stopped = state.value
                if (stopped.closed && stopped.operations == 0 && settlement.value == Settlement.Complete)
                    active.compareAndSet(this, null)
                throw failure
            }
        }
        fun requireActive() {
            check(!state.value.closed && active.value === this) { "Repository writer is not active" }
        }
        fun operations(): ServerWriterOperationLease = ServerWriterOperationLease(::enter, ::requireActive)
        private fun enter(): () -> Unit {
            while (true) {
                requireActive()
                val before = state.value
                check(!before.closed && before.operations < Int.MAX_VALUE)
                if (state.compareAndSet(before, before.copy(operations = before.operations + 1))) break
            }
            val left = MutableStateFlow(false)
            return {
                if (left.compareAndSet(false, true)) {
                    while (true) {
                        val before = state.value; check(before.operations > 0)
                        if (state.compareAndSet(before, before.copy(operations = before.operations - 1))) break
                    }
                    drain()
                }
            }
        }
        fun close(onDrained: () -> Unit = {}) {
            while (true) {
                val before = state.value
                if (before.closed || state.compareAndSet(before, before.copy(closed = true, onDrained = onDrained))) break
            }
            drain()
        }
        private fun drain() {
            while (true) {
                val before = state.value
                if (!before.closed || before.operations != 0 || before.drained) return
                if (state.compareAndSet(before, before.copy(drained = true))) {
                    try {
                        before.onDrained?.invoke()
                        active.compareAndSet(this, null)
                        settlement.value = Settlement.Complete
                    } catch (failure: Throwable) {
                        settlement.value = Settlement.Failed
                        throw failure
                    }
                    return
                }
            }
        }
    }
}
