package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.persistence.IosAtomicLocalRecordStore
import cg.creamgod.boarderless.data.persistence.backgroundAwaited
import cg.creamgod.boarderless.data.persistence.withWriterOperations
import cg.creamgod.boarderless.data.persistence.iosPrivateRecordRoot

internal fun iosRealtimeBackendRepository(submission: AwaitedSubmissionRuntime? = null): BackendWorkspaceRepository {
    val preferences = submission?.originalPreferences() ?: SessionPreferences()
    val baseUrl = submission?.serverBaseUrl() ?: defaultBackendBaseUrl()
    val lease = IosServerWriterLease.acquire(iosPrivateRecordRoot("server-writer-control"))
    lease.requireActive()
    val owner = ServerRepositoryWriterCoordinator.candidate()
    val operations = owner.operations()
    return BackendWorkspaceRepository(
        baseUrl = baseUrl,
        preferences = preferences,
        awaitedSubmissionRuntime = submission,
        prepareServerWriter = { lease.requireActive(); owner.claimAfterDrain(); lease.requireActive() },
        requireServerWriterActive = { lease.requireActive(); owner.requireActive() },
        releaseServerWriter = { owner.close() },
        awaitServerWriterDrain = { owner.awaitClosedAndDrained() },
        retryServerWriterRelease = { owner.retryFailedRelease() },
        serverWriterReleaseFailed = { owner.releaseFailed },
        serverWriterOperations = operations,
        mediaReferenceProofRuntime = MediaReferenceProofRuntime {
            lease.requireActive(); owner.requireActive()
            IosAtomicLocalRecordStore(iosPrivateRecordRoot("media-reference-proofs")).backgroundAwaited().withWriterOperations(operations)
        },
        realtimeRuntime = RealtimeCheckpointRuntime {
        lease.requireActive(); owner.requireActive()
        IosAtomicLocalRecordStore(iosPrivateRecordRoot("realtime-checkpoints")).backgroundAwaited().withWriterOperations(operations)
    })
}
