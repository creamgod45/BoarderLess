package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.*
import kotlinx.serialization.Serializable

@Serializable data class CommittedLocalSyncTransaction(
    val outgoing: OutgoingLocalSyncTransaction,
    val workspaceVersion: Long,
    val serverSeq: Long,
) {
    init {
        require(outgoing.prepared != null)
        require(workspaceVersion in (outgoing.remoteVersion + 1)..9_007_199_254_740_991L)
        require(serverSeq in (outgoing.remoteSeq + 1)..9_007_199_254_740_991L)
    }
}

/** Frozen canvas transaction, not authorization to send it and not a server ACK. The worker
 * must still verify capabilities, materialized media, current ACL and original receipt/fence.
 * Credentials and transport headers never belong in this durable document. */
@Serializable data class OutgoingLocalSyncTransaction(
    val proposalId: String,
    val remoteVersion: Long,
    val remoteSeq: Long,
    val remoteClientId: String,
    val operation: TransactionOperation,
    val prepared: PreparedWorkspaceSubmission? = null,
) {
    init {
        require(proposalId.isNotBlank() && proposalId.length <= 256)
        require(remoteClientId.isNotBlank() && remoteClientId.length <= 256)
        require(remoteVersion in 0..9_007_199_254_740_991L && remoteSeq in 0..9_007_199_254_740_991L)
    }
    internal fun requireProposal(proposal: LocalWorkspaceSyncProposal) {
        require(proposal.id == proposalId && proposal.remoteVersion == remoteVersion && proposal.remoteSeq == remoteSeq)
        require(proposal.remoteClientId == remoteClientId)
        prepared?.let { identity ->
            require(identity.serverScope == proposal.binding.serverScope && identity.userId == proposal.binding.userId)
            require(identity.workspaceId == proposal.binding.workspaceId && identity.clientId == remoteClientId)
            require(identity.localOperationId == operation.operationId && identity.baseVersion == remoteVersion)
        }
        val ids = mutableSetOf<String>()
        var expanded = 0
        fun validate(command: WorkspaceOperation, depth: Int) {
            require(depth <= 16 && command.operationId.isNotBlank() && command.operationId.length <= 256 && ids.add(command.operationId))
            if (command is TransactionOperation) command.operations.forEach { validate(it, depth + 1) }
            else {
                expanded += when (command) {
                    is CreateObjectsOperation -> command.objects.size
                    is CreateRelationsOperation -> command.relations.size
                    is ReparentObjectsOperation -> command.changes.size
                    is DeleteObjectsOperation, is DeleteRelationsOperation, is TransformObjectsOperation,
                    is EditTextOperation, is UpdateTextNodeAttributesOperation, is UpdateGroupFrameAttributesOperation,
                    is UpdateMediaNodeAttributesOperation, is UpdateMediaReferenceOperation,
                    is UpdateRelationAttributesOperation, is UpdateCanvasStyleOperation, is UpdateSequenceDiagramsOperation -> 1
                    else -> error("Unsupported outgoing canvas operation")
                }
                require(expanded <= 200)
            }
        }
        validate(operation, 0)
        val result = operation.applyTo(proposal.remote)
        require(result is OperationResult.Applied && sameSyncContent(result.workspace, proposal.target)) {
            "Outgoing transaction differs from reviewed target"
        }
    }
}
