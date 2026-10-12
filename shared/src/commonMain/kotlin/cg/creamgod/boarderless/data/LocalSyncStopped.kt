package cg.creamgod.boarderless.data

import kotlinx.serialization.Serializable

/** Retained local intent plus verified terminal fence identity. Not a commit/baseline ACK. */
@Serializable data class StoppedLocalSyncTransaction(
    val proposal: LocalWorkspaceSyncProposal,
    val applied: AppliedLocalSyncProposal,
    val outgoing: OutgoingLocalSyncTransaction,
    val observedServerVersion: Long,
    val observedServerSeq: Long,
) {
    init {
        require(outgoing.prepared != null && applied.settledCheckpoint == null && applied.committed == null)
        applied.requireCanonicalTarget(proposal)
        require(outgoing.proposalId == proposal.id)
        require(observedServerVersion in proposal.remoteVersion..9_007_199_254_740_991L)
        require(observedServerSeq in proposal.remoteSeq..9_007_199_254_740_991L)
        outgoing.requireProposal(proposal)
        require(applied.appliedDigest == syncWorkspaceDigest(applied.operation.appliedWorkspace()))
    }
}
