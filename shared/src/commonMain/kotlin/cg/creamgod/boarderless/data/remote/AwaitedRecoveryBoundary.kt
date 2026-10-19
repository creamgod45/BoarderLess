package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Select exactly one configured safety store. A failed awaited operation never falls
 * back to legacy Settings, and ordinary read-only receipt lookup never creates a marker. */
internal class AwaitedRecoveryBoundary(private val legacyFence: PendingFenceAttemptStore,
    private val legacyDeletion: CommittedDeletionStore, private val awaited: AwaitedRecoverySafetyPersistence?,
    private val operations: ServerWriterOperationLease? = null) {
    private suspend fun <T> legacyWork(action: () -> T): T = withContext(Dispatchers.Default) {
        if (operations != null) operations.run(action) else action()
    }
    suspend fun fenceState(pending: PendingWorkspaceSubmission) = if (awaited != null) awaited.fenceState(pending)
        else legacyWork { legacyFence.state(pending) }
    suspend fun beginFence(pending: PendingWorkspaceSubmission) {
        if (awaited != null) awaited.beginFence(pending) else legacyWork { legacyFence.begin(pending) }
    }
    suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus) {
        if (awaited != null) awaited.observeFence(pending, status)
        else legacyWork { legacyFence.observe(pending, status) }
    }
    suspend fun requireRestoration(scope: PendingSubmissionScope, operations: List<OperationDto>, local: WorkspaceOperation) {
        if (awaited == null) {
            legacyWork { legacyDeletion.requireRestoration(scope, operations, local) }
            return
        }
        val snapshots = withContext(Dispatchers.Default) { local.restorationSnapshotDigests() }
        for (operation in operations.filter { it.kind in setOf("restore_objects", "restore_relations") }) {
            validateRestorationWire(operation)
            val type = if (operation.kind == "restore_objects") "object" else "relation"
            for ((id, version) in checkNotNull(operation.expectedObjectVersions)) {
                val entity = "$type:$id"
                val evidence = checkNotNull(awaited.deletion(scope, entity)) { "Restoration requires confirmed deletion evidence" }
                requireDeletionRestorationEvidence(evidence, scope, entity, version, snapshots[entity])
            }
        }
    }
    suspend fun recordDeletions(entry: PendingWorkspaceSubmission, accepted: AcceptedOperationsDto) {
        if (awaited == null) {
            legacyWork { legacyDeletion.record(entry, accepted) }
            return
        }
        val records = withContext(Dispatchers.Default) { deletionEvidenceFromAcknowledgement(entry, accepted) }
        if (records.isNotEmpty()) awaited.recordDeletions(entry.scope, records)
    }
}
