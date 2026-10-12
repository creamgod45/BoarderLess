package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reuses the native document codec and receipt rules over a private transaction snapshot.
 * The snapshot is calculation only: no session/result escapes before awaited storage ACK.
 * Each call reloads the durable record; another tab's CAS cannot be silently overwritten.
 */
internal class AwaitedLocalWorkspaceRepository(
    private val files: AwaitedDurableLocalRecordStore,
    private val now: () -> String,
) : CanvasWorkspaceRepository, LocalWorkspaceSyncStore {
    private val mutex = Mutex()
    private var closed = false
    override val isLocalOnly = true
    override val localSyncStore: LocalWorkspaceSyncStore get() = this
    override val awaitsDraftPublication = true
    override val supportsExternalMedia = true
    override val supportsCanvasStyle = true
    override val supportsRelationGeometry = true
    override val supportsVectorPaths = true
    override val supportsSequenceDiagrams = true

    private suspend fun <T> transaction(operation: suspend (PersistentLocalWorkspaceRepository) -> T): T = mutex.withLock {
        check(!closed) { "Local repository is closed" }
        val key = PersistentLocalWorkspaceRepository.Key
        val before = files.read(key)
        currentCoroutineContext().ensureActive()
        check(!closed)
        var staged = before?.copy(payload = before.payload.copyOf())
        var changed = false
        var confirmation: Long? = null
        val snapshot = object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? {
                check(key == PersistentLocalWorkspaceRepository.Key)
                return staged?.let { it.copy(payload = it.payload.copyOf()) }
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                check(key == PersistentLocalWorkspaceRepository.Key && generation == staged?.generation && !changed)
                check(generation == null || generation in 1 until 9_007_199_254_740_991L)
                staged = DurableLocalRecord((generation ?: 0L) + 1, payload.copyOf())
                changed = true
            }
            override fun confirmDurable(key: String, generation: Long) {
                check(key == PersistentLocalWorkspaceRepository.Key && generation == staged?.generation)
                confirmation = generation
            }
        }
        val lock = object : LocalDocumentLock {
            override fun <R> withLock(block: () -> R): R = block()
        }
        val result = operation(PersistentLocalWorkspaceRepository({ snapshot }, lock, now))
        currentCoroutineContext().ensureActive()
        check(!closed)
        if (changed) {
            val next = checkNotNull(staged)
            files.compareAndSet(key, before?.generation, next.payload)
            currentCoroutineContext().ensureActive()
            check(!closed)
            files.confirmDurable(key, next.generation)
        } else confirmation?.let { files.confirmDurable(key, it) }
        currentCoroutineContext().ensureActive()
        check(!closed)
        result
    }

    override fun retainDraft(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) =
        error("Asynchronous local storage requires retainDraftAwaited before publication")

    override suspend fun retainDraftAwaited(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
        transaction { it.retainDraft(session, before, operation) }
    }
    override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?) = transaction { it.openOrCreateWorkspace(preferredWorkspaceId) }
    override suspend fun listWorkspaces(session: WorkspaceSession) = transaction { it.listWorkspaces(session) }
    override suspend fun createWorkspace(session: WorkspaceSession, title: String) = transaction { it.createWorkspace(session, title) }
    override suspend fun openWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId) = transaction { it.openWorkspace(session, workspaceId) }
    override suspend fun renameWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId, title: String) = transaction { it.renameWorkspace(session, workspaceId, title) }
    override suspend fun deleteWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId) { transaction { it.deleteWorkspace(session, workspaceId) } }
    override suspend fun listRecentActivity(session: WorkspaceSession, limit: Int) = transaction { it.listRecentActivity(session, limit) }
    override suspend fun listWorkspaceMembers(session: WorkspaceSession) = transaction { it.listWorkspaceMembers(session) }
    override suspend fun setWorkspaceMemberRole(session: WorkspaceSession, userId: String, role: WorkspaceMemberRole) { transaction { it.setWorkspaceMemberRole(session, userId, role) } }
    override suspend fun removeWorkspaceMember(session: WorkspaceSession, userId: String) { transaction { it.removeWorkspaceMember(session, userId) } }
    override suspend fun refresh(session: WorkspaceSession) = transaction { it.refresh(session) }
    override suspend fun readSyncSnapshot(session: WorkspaceSession) = transaction { it.readSyncSnapshot(session) }
    override suspend fun stageUploadedAssetMaterialization(verified: VerifiedUploadedAssetMaterialization) =
        transaction { it.stageUploadedAssetMaterialization(verified) }
    override suspend fun stageAssetUploadPreparation(session: WorkspaceSession,
        expected: LocalAssetUploadPreparation?, next: LocalAssetUploadPreparation) =
        transaction { it.stageAssetUploadPreparation(session, expected, next) }
    override suspend fun stageSyncMaterialization(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        mapping: LocalAssetMaterialization) = transaction { it.stageSyncMaterialization(session, expectedCheckpoint, mapping) }
    override suspend fun stageSyncOutgoing(session: WorkspaceSession, outgoing: OutgoingLocalSyncTransaction) =
        transaction { it.stageSyncOutgoing(session, outgoing) }
    override suspend fun applySyncRebaseProposal(session: WorkspaceSession, reviewedId: String,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation, verification: PreparedSubmissionVerification) =
        transaction { it.applySyncRebaseProposal(session, reviewedId, operation, verification) }
    override suspend fun applyMaterializedSyncRebaseProposal(verified: VerifiedMaterializedTarget,
        operation: LocalWorkspaceContentOperation, receipt: PreparedSubmissionVerification) =
        transaction { it.applyMaterializedSyncRebaseProposal(verified, operation, receipt) }
    override suspend fun stageSyncRebaseProposal(session: WorkspaceSession, expectedId: String?, proposal: LocalSyncRebaseProposal,
        verification: PreparedSubmissionVerification) = transaction { it.stageSyncRebaseProposal(session, expectedId, proposal, verification) }
    override suspend fun archiveFencedSyncSubmission(session: WorkspaceSession, verification: PreparedSubmissionVerification) =
        transaction { it.archiveFencedSyncSubmission(session, verification) }
    override suspend fun settleCommittedSyncSubmission(session: WorkspaceSession, verification: PreparedSubmissionVerification) =
        transaction { it.settleCommittedSyncSubmission(session, verification) }
    override suspend fun bindSyncOutgoingSubmission(session: WorkspaceSession, expected: OutgoingLocalSyncTransaction,
        prepared: PreparedWorkspaceSubmission) = transaction { it.bindSyncOutgoingSubmission(session, expected, prepared) }
    override suspend fun settleReadOnlySyncProposal(session: WorkspaceSession, proposalId: String,
        verifiedRemote: WorkspaceSession) = transaction { it.settleReadOnlySyncProposal(session, proposalId, verifiedRemote) }
    override suspend fun applyMaterializedSyncProposal(verified: VerifiedMaterializedTarget,
        operation: LocalWorkspaceContentOperation): LocalSyncApplyResult =
        transaction { it.applyMaterializedSyncProposal(verified, operation) }

    override suspend fun applySyncProposal(session: WorkspaceSession, proposalId: String,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation) =
        transaction { it.applySyncProposal(session, proposalId, operation) }
    override suspend fun stageSyncProposal(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        expectedProposalId: String?, proposal: LocalWorkspaceSyncProposal) =
        transaction { it.stageSyncProposal(session, expectedCheckpoint, expectedProposalId, proposal) }
    override suspend fun bindSyncDestination(session: WorkspaceSession, binding: LocalWorkspaceSyncBinding, verifiedRemote: WorkspaceSession) =
        transaction { it.bindSyncDestination(session, binding, verifiedRemote) }
    override suspend fun acknowledgeSyncCheckpoint(session: WorkspaceSession, expected: LocalWorkspaceSyncCheckpoint, verifiedRemote: WorkspaceSession) =
        transaction { it.acknowledgeSyncCheckpoint(session, expected, verifiedRemote) }
    override suspend fun submit(session: WorkspaceSession, operation: WorkspaceOperation) = transaction { it.submit(session, operation) }
    override suspend fun listAssets(session: WorkspaceSession) = transaction { it.listAssets(session) }
    override suspend fun getAsset(session: WorkspaceSession, assetId: String) = transaction { it.getAsset(session, assetId) }
    override fun close() { closed = true }
}
