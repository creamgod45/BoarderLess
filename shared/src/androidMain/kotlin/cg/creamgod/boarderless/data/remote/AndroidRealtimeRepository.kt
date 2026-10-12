package cg.creamgod.boarderless.data.remote

import android.content.Context
import cg.creamgod.boarderless.data.persistence.AndroidAtomicLocalRecordStore
import cg.creamgod.boarderless.data.persistence.backgroundAwaited
import cg.creamgod.boarderless.data.persistence.withWriterOperations
import java.io.File

fun androidRealtimeBackendRepository(context: Context, submission: AwaitedSubmissionRuntime? = null): BackendWorkspaceRepository {
    val lease = AndroidServerWriterLease.acquire(File(context.applicationContext.noBackupFilesDir.canonicalFile, "server-writer-control"))
    lease.requireActive()
    val owner = ServerRepositoryWriterCoordinator.candidate()
    val operations = owner.operations()
    val root = File(context.applicationContext.noBackupFilesDir.canonicalFile, "realtime-checkpoints")
    return BackendWorkspaceRepository(
        awaitedSubmissionRuntime = submission,
        prepareServerWriter = { lease.requireActive(); owner.claimAfterDrain(); lease.requireActive() },
        requireServerWriterActive = { lease.requireActive(); owner.requireActive() },
        releaseServerWriter = { owner.close() },
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
