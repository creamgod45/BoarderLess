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
internal class ActivatedServerInstallation(
    private val activation: ActivatedAwaitedSubmission,
    private val target: PendingSubmissionScope,
    private val awaitPhysicalDrain: suspend () -> Unit,
    private val releaseExclusion: suspend () -> Unit,
) {
    private enum class Phase { New, Preparing, Failed, Prepared, Installed, Stopping, CallsDrained, PhysicalDrained, Released }
    private val phase = MutableStateFlow(Phase.New)
    private val closing = Mutex()
    private var candidate: CanvasWorkspaceRepository? = null
    private var media: MediaImportRuntime? = null
    private var activationStopped = false
    private var repositoryStopped = false
    val released: Boolean get() = phase.value == Phase.Released

    /** Host retains THIS installation before calling prepare. Failed preparation keeps the
     * original candidate and authority for explicit closeAndDrain recovery, even when media
     * construction or runtime validation fails. It cannot build a replacement candidate. */
    fun prepare(storage: WorkspaceStorageRuntime) {
        check(phase.compareAndSet(Phase.New, Phase.Preparing)) { "Installation preparation already attempted" }
        try {
            val repository = storage.createActivatedServerRepository(activation, target) { candidate = it }
            media = storage.mediaRuntimeFor(repository)
            activation.runtime.requireActive(target)
            check(phase.compareAndSet(Phase.Preparing, Phase.Prepared))
        } catch (failure: Throwable) {
            phase.compareAndSet(Phase.Preparing, Phase.Failed)
            throw failure
        }
    }

    private fun ready() {
        check(phase.value in setOf(Phase.Prepared, Phase.Installed))
        activation.runtime.requireActive(target)
    }

    /** One host handoff. Repeated/stale adoption cannot claim another repository or scope. */
    fun adopt(): Pair<CanvasWorkspaceRepository, MediaImportRuntime> {
        ready()
        check(phase.compareAndSet(Phase.Prepared, Phase.Installed)) { "Installation already adopted or closing" }
        ready()
        return requireNotNull(candidate) to requireNotNull(media)
    }

    /** Explicit retry resumes the same close phase. Cancellation/timeouts never prove release.
     * Stop new runtime calls first, then repository/network work; logical drain is distinct
     * from physical IDB/native I/O drain. Only afterward may platform exclusion be released. */
    suspend fun closeAndDrain() = closing.withLock {
        currentCoroutineContext().ensureActive()
        if (phase.value == Phase.Released) return@withLock
        while (true) {
            val before = phase.value
            check(before != Phase.Preparing) { "Wait for the original preparation to finish before draining" }
            if (before !in setOf(Phase.New, Phase.Failed, Phase.Prepared, Phase.Installed) ||
                phase.compareAndSet(before, Phase.Stopping)) break
        }
        if (phase.value == Phase.Stopping) {
            if (!activationStopped) { activation.close(); activationStopped = true }
            if (!repositoryStopped) { candidate?.close(); repositoryStopped = true }
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

}
