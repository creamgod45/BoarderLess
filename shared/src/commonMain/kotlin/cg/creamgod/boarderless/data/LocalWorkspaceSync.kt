package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.Serializable

/** A verified server destination, never credentials or a guessed local/server UUID mapping. */
@Serializable
data class LocalWorkspaceSyncBinding(
    val serverScope: String,
    val userId: String,
    val workspaceId: WorkspaceId,
) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")))
        require(userId.isNotBlank() && userId.length <= 256)
    }
}

/** Stored in the same atomic document record as offline edits. The current document is the
 * durable pending snapshot; it is never cleared by connectivity or an HTTP request starting. */
@Serializable
data class LocalWorkspaceSyncCheckpoint(
    val binding: LocalWorkspaceSyncBinding,
    val baseline: Workspace,
    val serverVersion: Long,
    val serverSeq: Long,
    val requiresInitialReview: Boolean = false,
) {
    init {
        require(serverVersion in 0..MaximumCanvasVersion && serverSeq in 0..MaximumCanvasVersion)
    }
}

data class LocalWorkspaceSyncSnapshot(
    val local: WorkspaceSession,
    val checkpoint: LocalWorkspaceSyncCheckpoint?,
    val proposal: LocalWorkspaceSyncProposal? = null,
    val applied: AppliedLocalSyncProposal? = null,
    val outgoing: OutgoingLocalSyncTransaction? = null,
    val stopped: List<StoppedLocalSyncTransaction> = emptyList(),
    val rebaseProposal: LocalSyncRebaseProposal? = null,
    val rebases: List<AppliedLocalSyncRebase> = emptyList(),
    val superseded: List<SupersededLocalSyncProposal> = emptyList(),
    val materializations: List<LocalAssetMaterialization> = emptyList(),
    val assetUploadPreparations: List<LocalAssetUploadPreparation> = emptyList(),
) {
    /** A newly uploaded identity needs a new canonical review, not mutation of an old proposal. */
    val requiresAssetIdentityReview: Boolean get() {
        val target = rebaseProposal?.target ?: proposal?.target ?: return false
        val localIds = materializations.map { it.localAssetId }.toSet()
        return target.objects.values.filterIsInstance<MediaNode>().any {
            it.assetId in localIds || it.thumbnailAssetId in localIds
        }
    }
    val rebaseIsCurrent: Boolean get() = rebaseProposal?.let {
        it.localBefore == local.workspace && checkpoint?.let(::syncCheckpointDigest) == it.checkpointDigest && it.context.outgoing == outgoing
    } ?: false
    val proposalIsSettled: Boolean get() = applied?.let {
        it.proposalId == proposal?.id && it.settledCheckpoint != null && it.settledCheckpoint == checkpoint
    } ?: false
    val appliedIsCurrent: Boolean get() = applied?.let {
        it.proposalId == proposal?.id && it.appliedDigest == syncWorkspaceDigest(local.workspace)
    } ?: false
    val proposalIsCurrent: Boolean get() = proposal?.let {
        it.localDigest == syncWorkspaceDigest(local.workspace) && checkpoint?.let(::syncCheckpointDigest) == it.checkpointDigest
    } ?: false
    val hasPendingContent: Boolean get() = checkpoint?.let {
        !sameSyncContent(canonicalLocalWorkspace(), it.baseline)
    } ?: true
}

/** Only references actually using a saved local destination are projected for comparison.
 * A reservation by itself does not rewrite an existing unrelated local reference. */
internal fun LocalWorkspaceSyncSnapshot.canonicalLocalWorkspace(): Workspace {
    val references = local.workspace.objects.values.filterIsInstance<MediaNode>().flatMap {
        listOfNotNull(it.assetId, it.thumbnailAssetId)
    }.toSet()
    val used = materializations.filter { it.localAssetId in references }
    if (used.isEmpty()) return local.workspace
    return MaterializedAssetProjection(local, requireNotNull(checkpoint).binding, used).toCanonical(local.workspace)
}

interface LocalWorkspaceSyncStore {
    suspend fun stageUploadedAssetMaterialization(verified: VerifiedUploadedAssetMaterialization): LocalWorkspaceSyncSnapshot
    /** CAS reservation/receipt storage only; no network, consent or canvas publication. */
    suspend fun stageAssetUploadPreparation(session: WorkspaceSession,
        expected: LocalAssetUploadPreparation?, next: LocalAssetUploadPreparation): LocalWorkspaceSyncSnapshot
    suspend fun applyMaterializedSyncRebaseProposal(verified: VerifiedMaterializedTarget,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation,
        receipt: PreparedSubmissionVerification): LocalSyncApplyResult
    /** Publish only the exact freshly verified local projection and its canonical review. */
    suspend fun applyMaterializedSyncProposal(verified: VerifiedMaterializedTarget,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation): LocalSyncApplyResult
    /** Explicit local reservation only. Mapping must be durable before preparePinned or download. */
    suspend fun stageSyncMaterialization(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        mapping: LocalAssetMaterialization): LocalWorkspaceSyncSnapshot
    suspend fun applySyncRebaseProposal(session: WorkspaceSession, reviewedId: String,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation,
        verification: PreparedSubmissionVerification): LocalSyncApplyResult
    /** Save a reviewed four-input plan only, after fresh original receipt/head verification. */
    suspend fun stageSyncRebaseProposal(session: WorkspaceSession, expectedId: String?, proposal: LocalSyncRebaseProposal,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot
    /** Explicit terminal-fence archive. Retains full intent/identity and local canvas; never ACKs baseline. */
    suspend fun archiveFencedSyncSubmission(session: WorkspaceSession,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot
    /** Settles a verified original commit only when the fresh head still equals that commit.
     * Later remote edits require another review; concurrent local edits are retained. */
    suspend fun settleCommittedSyncSubmission(session: WorkspaceSession,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot
    /** Persist the exact transaction before any HTTP. Does not authorize or send it. */
    suspend fun stageSyncOutgoing(session: WorkspaceSession, outgoing: OutgoingLocalSyncTransaction): LocalWorkspaceSyncSnapshot
    suspend fun bindSyncOutgoingSubmission(session: WorkspaceSession, expected: OutgoingLocalSyncTransaction,
        prepared: PreparedWorkspaceSubmission): LocalWorkspaceSyncSnapshot
    /** Only for a reviewed target already equal to the reviewed remote content. Caller must
     * refresh the exact binding, with no pending remote draft/request, immediately beforehand.
     * Uploads require separate durable request/receipt reconciliation, never this read-only ACK. */
    suspend fun settleReadOnlySyncProposal(session: WorkspaceSession, proposalId: String,
        verifiedRemote: WorkspaceSession): LocalWorkspaceSyncSnapshot
    suspend fun applySyncProposal(session: WorkspaceSession, proposalId: String,
        operation: cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation): LocalSyncApplyResult
    /** Saves an explicitly confirmed plan only. Does not apply it or authorize network writes. */
    suspend fun stageSyncProposal(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        expectedProposalId: String?, proposal: LocalWorkspaceSyncProposal): LocalWorkspaceSyncSnapshot
    suspend fun readSyncSnapshot(session: WorkspaceSession): LocalWorkspaceSyncSnapshot

    /** The caller must obtain user consent for this exact destination and initial comparison.
     * No network or copying occurs here. A stale local snapshot cannot establish a binding. */
    suspend fun bindSyncDestination(
        session: WorkspaceSession,
        binding: LocalWorkspaceSyncBinding,
        verifiedRemote: WorkspaceSession,
    ): LocalWorkspaceSyncSnapshot

    /** Call only after authoritative refresh/receipt reconciliation, never an optimistic response.
     * Expected checkpoint fencing prevents an old connection/account from advancing the baseline.
     * Concurrent local edits are preserved and remain pending relative to the new baseline. */
    suspend fun acknowledgeSyncCheckpoint(
        session: WorkspaceSession,
        expected: LocalWorkspaceSyncCheckpoint,
        verifiedRemote: WorkspaceSession,
    ): LocalWorkspaceSyncSnapshot
}

internal fun LocalWorkspaceSyncBinding.requireRemote(remote: WorkspaceSession) {
    require(remote.userId == userId && remote.workspace.id == workspaceId)
    require(remote.workspaceVersion == remote.workspace.version)
    require(remote.workspaceVersion in 0..MaximumCanvasVersion && remote.lastServerSeq in 0..MaximumCanvasVersion)
}

/** Revision counters belong to each destination. All actual properties, including title,
 * sequence bindings, style, geometry and external-media references, remain in the comparison. */
internal fun sameSyncContent(first: Workspace, second: Workspace): Boolean {
    fun content(workspace: Workspace) = workspace.copy(
        id = WorkspaceId("comparison"), version = 0,
        objects = workspace.objects.mapValues { (_, item) -> item.withVersion(1) },
        relations = workspace.relations.mapValues { (_, item) -> item.withVersion(1) },
        canvasStyleVersion = 0, sequenceDiagramsVersion = 0,
    )
    return content(first) == content(second)
}

sealed interface LocalWorkspaceSyncComparison {
    data object Unbound : LocalWorkspaceSyncComparison
    data object Unchanged : LocalWorkspaceSyncComparison
    data object UploadPending : LocalWorkspaceSyncComparison
    data class DownloadPending(val remote: Workspace) : LocalWorkspaceSyncComparison
    /** A frozen three-way review only. It cannot write either destination. */
    data class Conflict(val review: WorkspaceDraftReview) : LocalWorkspaceSyncComparison
}

/** Authentication/connectivity belongs to the transport. This function never writes data,
 * calls HTTP, assumes local/server identities match, or silently resolves conflicts. */
fun compareLocalWorkspaceSync(
    snapshot: LocalWorkspaceSyncSnapshot,
    remote: WorkspaceSession,
): LocalWorkspaceSyncComparison {
    val checkpoint = snapshot.checkpoint ?: return LocalWorkspaceSyncComparison.Unbound
    checkpoint.binding.requireRemote(remote)
    require(remote.workspaceVersion >= checkpoint.serverVersion && remote.lastServerSeq >= checkpoint.serverSeq)
    val local = snapshot.canonicalLocalWorkspace()
    val current = remote.workspace.copy(id = local.id)
    val baseline = checkpoint.baseline
    if (checkpoint.requiresInitialReview && !sameSyncContent(local, current)) {
        return LocalWorkspaceSyncComparison.Conflict(syncReview(snapshot, remote))
    }
    if (sameSyncContent(local, current)) return LocalWorkspaceSyncComparison.Unchanged
    val localChanged = !sameSyncContent(local, baseline)
    val remoteChanged = !sameSyncContent(current, baseline)
    if (!remoteChanged) return LocalWorkspaceSyncComparison.UploadPending
    if (!localChanged) return LocalWorkspaceSyncComparison.DownloadPending(current)
    return LocalWorkspaceSyncComparison.Conflict(syncReview(snapshot, remote))
}

private fun syncReview(snapshot: LocalWorkspaceSyncSnapshot, remote: WorkspaceSession): WorkspaceDraftReview {
    val checkpoint = requireNotNull(snapshot.checkpoint)
    val local = snapshot.canonicalLocalWorkspace()
    return WorkspaceDraftReview(
        draftId = "offline-sync:${local.id.value}:${local.version}:${remote.workspaceVersion}:${remote.lastServerSeq}",
        baseVersion = checkpoint.serverVersion,
        baseServerSeq = checkpoint.serverSeq,
        currentVersion = remote.workspaceVersion,
        currentServerSeq = remote.lastServerSeq,
        baseline = checkpoint.baseline,
        proposed = local,
        current = remote.workspace.copy(id = local.id),
        operations = emptyList(),
        quarantined = false,
        hasUnconfirmedSubmission = false,
    )
}
