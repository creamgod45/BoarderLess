package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.ActivatedAwaitedSubmission
import cg.creamgod.boarderless.data.remote.PendingSubmissionScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns one prepared/adopted repository and the ORIGINAL activation/exclusion authority.
 * Host must supply actual all-store physical drain and idempotent exclusion release, with
 * no empty defaults. This does not capture sources, activate, initialize or switch UI.
 * Keep this object alive through disposal failures; never start a competing installation. */
internal class ActivatedServerInstallation private constructor(
    private val activation: ActivatedAwaitedSubmission,
    private val target: PendingSubmissionScope,
    private val candidate: CanvasWorkspaceRepository,
    private val media: MediaImportRuntime,
    private val awaitPhysicalDrain: suspend () -> Unit,
    private val releaseExclusion: suspend () -> Unit,
) {
    private enum class Phase { Prepared, Installed, Stopping, CallsDrained, PhysicalDrained, Released }
    private val phase = MutableStateFlow(Phase.Prepared)
    private val closing = Mutex()
    private var activationStopped = false
    private var repositoryStopped = false
    val released: Boolean get() = phase.value == Phase.Released

    private fun ready() {
        check(phase.value in setOf(Phase.Prepared, Phase.Installed))
        activation.runtime.requireActive(target)
    }

    /** One host handoff. Repeated/stale adoption cannot claim another repository or scope. */
    fun adopt(): Pair<CanvasWorkspaceRepository, MediaImportRuntime> {
        ready()
        check(phase.compareAndSet(Phase.Prepared, Phase.Installed)) { "Installation already adopted or closing" }
        ready()
        return candidate to media
    }

    /** Explicit retry resumes the same close phase. Cancellation/timeouts never prove release.
     * Stop new runtime calls first, then repository/network work; logical drain is distinct
     * from physical IDB/native I/O drain. Only afterward may platform exclusion be released. */
    suspend fun closeAndDrain() = closing.withLock {
        currentCoroutineContext().ensureActive()
        if (phase.value == Phase.Released) return@withLock
        if (phase.value in setOf(Phase.Prepared, Phase.Installed)) phase.value = Phase.Stopping
        if (phase.value == Phase.Stopping) {
            if (!activationStopped) { activation.close(); activationStopped = true }
            if (!repositoryStopped) { candidate.close(); repositoryStopped = true }
            activation.closeAndAwaitCalls()
            currentCoroutineContext().ensureActive()
            phase.value = Phase.CallsDrained
        }
        if (phase.value == Phase.CallsDrained) {
            awaitPhysicalDrain()
            currentCoroutineContext().ensureActive()
            phase.value = Phase.PhysicalDrained
        }
        if (phase.value == Phase.PhysicalDrained) {
            // Callback must be idempotent: cancellation can arrive after actual release.
            releaseExclusion()
            currentCoroutineContext().ensureActive()
            phase.value = Phase.Released
        }
    }

    companion object {
        /** Media runtime preparation is part of candidate construction, not UI adoption.
         * On failure close only the candidate; caller still owns the original activation
         * and exclusion, and must explicitly drain/release it. Never recreate a legacy repo. */
        fun prepare(
            storage: WorkspaceStorageRuntime,
            activation: ActivatedAwaitedSubmission,
            target: PendingSubmissionScope,
            awaitPhysicalDrain: suspend () -> Unit,
            releaseExclusion: suspend () -> Unit,
        ): ActivatedServerInstallation {
            val candidate = storage.createActivatedServerRepository(activation, target)
            try {
                val media = storage.mediaRuntimeFor(candidate)
                activation.runtime.requireActive(target)
                return ActivatedServerInstallation(activation, target, candidate, media, awaitPhysicalDrain, releaseExclusion)
            } catch (failure: Throwable) {
                try { candidate.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
    }
}
