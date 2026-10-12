package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.flow.StateFlow

/** Web uses the same mode chooser and switch gates as native platforms. Local media never falls
 * back to HTTP: document and original gateways must be supplied by the selected repository.
 */
fun browserWorkspaceStorageRuntime(activity: StateFlow<MediaPlaybackActivity>? = null): WorkspaceStorageRuntime =
    WorkspaceStorageRuntime(
        serverRepository = { browserServerRepository() },
        serverRepositoryWithSubmission = { browserServerRepository(it) },
        localRepository = ::browserLocalWorkspaceRepository,
        mediaRuntimeFor = { repository ->
            val local = repository.localAssetGateway
            val download = if (repository.isLocalOnly) {
                checkNotNull(local as? AssetDownloadGateway) { "Local browser downloads require a persistent gateway" }
            } else {
                check(local == null) { "Server browser runtime cannot use a local gateway" }
                null
            }
            browserMediaImportRuntime(activity, download)
        },
    )

private fun browserServerRepository(submission: cg.creamgod.boarderless.data.remote.AwaitedSubmissionRuntime? = null): cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository {
            val lease = cg.creamgod.boarderless.data.remote.BrowserServerWriterLease()
            val owner = cg.creamgod.boarderless.data.remote.ServerRepositoryWriterCoordinator.candidate()
            val operations = owner.operations()
            return cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository(
            awaitedSubmissionRuntime = submission,
            prepareServerWriter = { lease.acquire(); owner.claimAfterDrain(); lease.requireActive() },
            requireServerWriterActive = { lease.requireActive(); owner.requireActive() },
            releaseServerWriter = { owner.close(lease::close) },
            awaitServerWriterDrain = { owner.awaitClosedAndDrained() },
            retryServerWriterRelease = { owner.retryFailedRelease() },
            serverWriterReleaseFailed = { owner.releaseFailed },
            serverWriterOperations = operations,
            mediaReferenceProofRuntime = cg.creamgod.boarderless.data.remote.MediaReferenceProofRuntime {
                lease.requireActive(); owner.requireActive()
                BrowserDurableLocalRecordStore(BrowserRecordScope.Documents, operations)
            },
            realtimeRuntime = cg.creamgod.boarderless.data.remote.RealtimeCheckpointRuntime {
                lease.requireActive(); owner.requireActive()
                BrowserDurableLocalRecordStore(BrowserRecordScope.Documents, operations)
            })
}
