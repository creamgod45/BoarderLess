package cg.creamgod.boarderless.data.remote

import android.content.Context
import cg.creamgod.boarderless.data.persistence.AndroidAtomicLocalRecordStore
import cg.creamgod.boarderless.data.persistence.backgroundAwaited
import cg.creamgod.boarderless.data.persistence.withWriterOperations
import java.io.File

fun androidRealtimeBackendRepository(context: Context, submission: AwaitedSubmissionRuntime? = null): BackendWorkspaceRepository {
    val preferences = submission?.originalPreferences() ?: SessionPreferences()
    val baseUrl = submission?.serverBaseUrl() ?: defaultBackendBaseUrl()
    val lease = AndroidServerWriterLease.acquire(File(context.applicationContext.noBackupFilesDir.canonicalFile, "server-writer-control"))
    lease.requireActive()
    val owner = ServerRepositoryWriterCoordinator.candidate()
    val operations = owner.operations()
    // No activation supplied: use fresh, migration-free durable storage for drafts, sequences and recovery safety.
    val effective = submission ?: FreshAwaitedServerStorage(
        AndroidAtomicLocalRecordStore(File(context.applicationContext.noBackupFilesDir.canonicalFile, "server-fresh-v1"))
            .backgroundAwaited().withWriterOperations(operations),
        "${baseUrl.trimEnd('/')}/api/v1",
    ) { lease.requireActive(); owner.requireActive() }.runtime
    val root = File(context.applicationContext.noBackupFilesDir.canonicalFile, "realtime-checkpoints")
    return BackendWorkspaceRepository(
        baseUrl = baseUrl,
        preferences = preferences,
        awaitedSubmissionRuntime = effective,
        prepareServerWriter = { lease.requireActive(); owner.claimAfterDrain(); lease.requireActive() },
        requireServerWriterActive = { lease.requireActive(); owner.requireActive() },
        releaseServerWriter = { owner.close() },
        awaitServerWriterDrain = { owner.awaitClosedAndDrained() },
        retryServerWriterRelease = { owner.retryFailedRelease() },
        serverWriterReleaseFailed = { owner.releaseFailed },
        serverWriterOperations = operations,
        mediaReferenceProofRuntime = MediaReferenceProofRuntime {
            lease.requireActive(); owner.requireActive()
            AndroidAtomicLocalRecordStore(File(context.applicationContext.noBackupFilesDir.canonicalFile,
                "media-reference-proofs")).backgroundAwaited().withWriterOperations(operations)
        },
        realtimeRuntime = RealtimeCheckpointRuntime {
        lease.requireActive(); owner.requireActive()
        AndroidAtomicLocalRecordStore(root).backgroundAwaited().withWriterOperations(operations)
    })
}
