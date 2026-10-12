package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.Serializable

/** Four inputs are retained separately: submitted local intent, later local content,
 * actual original commit, and latest remote head. Never infer local deletions from unseen
 * peer objects that were committed before our transaction. */
@Serializable data class LocalSyncRebaseContext(
    val original: LocalWorkspaceSyncProposal,
    val applied: AppliedLocalSyncProposal,
    val outgoing: OutgoingLocalSyncTransaction,
    val committed: Workspace,
    val committedVersion: Long,
    val committedSeq: Long,
    val remote: Workspace,
    val remoteVersion: Long,
    val remoteSeq: Long,
    val remoteRole: WorkspaceMemberRole,
) {
    init {
        require(outgoing.prepared != null)
        applied.requireCanonicalTarget(original)
        outgoing.requireProposal(original)
        require(committed.id == original.binding.workspaceId && committed.version == committedVersion)
        require(committedVersion in (original.remoteVersion + 1)..MaximumCanvasVersion && committedSeq in (original.remoteSeq + 1)..MaximumCanvasVersion)
        require(remote.id == committed.id && remote.version == remoteVersion && remoteVersion in committedVersion..MaximumCanvasVersion && remoteSeq in committedSeq..MaximumCanvasVersion)
        require(applied.settledCheckpoint == null && applied.appliedDigest == syncWorkspaceDigest(applied.operation.appliedWorkspace()))
    }
    internal fun remoteSession() = WorkspaceSession(original.binding.userId, original.remoteClientId, remoteRole, remoteVersion, remoteSeq, remote)
    internal fun committedSession() = WorkspaceSession(original.binding.userId, original.remoteClientId, remoteRole, committedVersion, committedSeq, committed)
}

internal fun localSyncRebaseContext(snapshot: LocalWorkspaceSyncSnapshot, verified: PreparedSubmissionVerification): LocalSyncRebaseContext {
    require(verified.status == PendingReceiptStatus.Committed)
    val original = requireNotNull(snapshot.proposal)
    val outgoing = requireNotNull(snapshot.outgoing)
    require(outgoing.prepared == verified.prepared)
    original.binding.requireRemote(verified.current)
    require(verified.current.clientId == original.remoteClientId)
    return LocalSyncRebaseContext(original, requireNotNull(snapshot.applied), outgoing,
        requireNotNull(verified.committedWorkspace), requireNotNull(verified.committedWorkspaceVersion), requireNotNull(verified.committedServerSeq),
        verified.current.workspace, verified.current.workspaceVersion, verified.current.lastServerSeq, verified.current.role)
}

@Serializable data class LocalSyncRebaseProposal(
    val id: String,
    val localBefore: Workspace,
    val checkpointDigest: String,
    val context: LocalSyncRebaseContext,
    val intentChoices: Map<String, LocalSyncMergeChoice>,
    val latestChoices: Map<String, LocalSyncMergeChoice>,
    val target: Workspace,
) {
    init {
        require(id.isNotBlank() && id.length <= 256 && checkpointDigest.matches(Regex("[0-9a-f]{64}")))
        require(localBefore.id == context.original.target.id && target.id == localBefore.id)
        require(intentChoices.size <= 20_003 && latestChoices.size <= 20_003)
    }
}

internal class LocalSyncRebasePlan(val local: WorkspaceSession, val context: LocalSyncRebaseContext) {
    init { require(local.workspace.id == context.original.target.id && local.workspaceVersion == local.workspace.version) }
    /** Stage 1: replay only later local intent onto the actual commit. Unseen peer additions
     * are remote-only additions, not local deletions. Conflicting intent remains explicit. */
    val intentSnapshot = LocalWorkspaceSyncSnapshot(local, LocalWorkspaceSyncCheckpoint(context.original.binding,
        canonicalAppliedWorkspace(), context.original.remoteVersion, context.original.remoteSeq),
        materializations = context.applied.materializations)
    private fun canonicalAppliedWorkspace(): Workspace {
        val workspace = context.applied.operation.appliedWorkspace()
        return LocalWorkspaceSyncSnapshot(local.copy(workspace = workspace, workspaceVersion = workspace.version),
            LocalWorkspaceSyncCheckpoint(context.original.binding, context.original.target,
                context.original.remoteVersion, context.original.remoteSeq),
            materializations = context.applied.materializations).canonicalLocalWorkspace()
    }
    val intentPlan = LocalWorkspaceSyncMergePlan(intentSnapshot, context.committedSession())
    fun latestSnapshot(intentChoices: Map<String, LocalSyncMergeChoice>): LocalWorkspaceSyncSnapshot {
        val normalized = intentPlan.resolve(intentChoices)
        return LocalWorkspaceSyncSnapshot(local.copy(workspace = normalized, workspaceVersion = normalized.version),
            LocalWorkspaceSyncCheckpoint(context.original.binding, context.committed.copy(id = local.workspace.id), context.committedVersion, context.committedSeq))
    }
    fun latestPlan(intentChoices: Map<String, LocalSyncMergeChoice>) = LocalWorkspaceSyncMergePlan(latestSnapshot(intentChoices), context.remoteSession())
    fun proposal(id: String, checkpointDigest: String, intentChoices: Map<String, LocalSyncMergeChoice>, latestChoices: Map<String, LocalSyncMergeChoice>) =
        LocalSyncRebaseProposal(id, local.workspace, checkpointDigest, context, intentChoices.toMap(), latestChoices.toMap(), latestPlan(intentChoices).resolve(latestChoices))
}

internal class LocalSyncRebaseRequired(val snapshot: LocalWorkspaceSyncSnapshot, val verification: PreparedSubmissionVerification) :
    IllegalStateException("Committed request requires explicit intent and latest-head review")
