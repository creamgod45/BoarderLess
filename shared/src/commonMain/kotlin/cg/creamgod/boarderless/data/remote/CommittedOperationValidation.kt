package cg.creamgod.boarderless.data.remote

private const val MaxSafeSequence = 9_007_199_254_740_991L

/** Full transaction boundary, reusable by REST ack and a future authenticated fan-out adapter.
 * This validates records only; it neither applies payloads nor advances any checkpoint.
 * baseVersion may be older than the commit version: the server permits non-conflicting rebases.
 */
internal fun isValidCommittedTransaction(
    operations: List<CommittedWorkspaceOperationDto>,
    fromServerSeq: Long,
    toServerSeq: Long,
    workspaceVersion: Long,
): Boolean {
    if (workspaceVersion !in 1..MaxSafeSequence || fromServerSeq !in 1..MaxSafeSequence ||
        toServerSeq !in fromServerSeq..MaxSafeSequence || operations.size !in 1..200 ||
        toServerSeq - fromServerSeq + 1 != operations.size.toLong()) return false
    val first = operations.first()
    if (first.transactionId.isBlank() || first.actorId.isBlank() || first.clientId.isBlank() ||
        first.baseVersion !in 0 until workspaceVersion || first.clientSeq !in 0..MaxSafeSequence ||
        operations.map { it.operationId }.toSet().size != operations.size) return false
    return operations.withIndex().all { (index, record) ->
        record.serverSeq == fromServerSeq + index && record.operationId.isNotBlank() &&
            record.transactionId == first.transactionId && record.actorId == first.actorId &&
            record.clientId == first.clientId && record.baseVersion == first.baseVersion &&
            record.workspaceVersion == workspaceVersion && record.clientSeq in 0..MaxSafeSequence &&
            record.operationType.isNotBlank() &&
            record.schemaVersion == 1 && record.committedAt.isNotBlank()
    }
}

/** Validate the full existing REST acknowledgement before persisting client sequence/checkpoint.
 * Payload remains authoritative opaque JSON here; this is not an operation replay reducer.
 */
internal fun AcceptedOperationsDto.validateAcknowledgement(request: SubmitOperationsRequest, actorId: String) {
    fun invalid(): Nothing = throw BackendContractException("Invalid committed transaction acknowledgement")
    if (status != "accepted" && status != "duplicate") invalid()
    if (!isValidCommittedTransaction(operations, fromServerSeq, toServerSeq, workspaceVersion) ||
        request.operations.isEmpty() || operations.size != request.operations.size) invalid()
    operations.zip(request.operations).forEachIndexed { index, (committed, submitted) ->
        if (committed.serverSeq != fromServerSeq + index || committed.operationId != submitted.operationId ||
            committed.transactionId != request.transactionId || committed.actorId != actorId ||
            committed.clientId != request.clientId || committed.clientSeq != submitted.clientSeq ||
            committed.clientSeq !in 0..MaxSafeSequence || committed.baseVersion !in 0..MaxSafeSequence ||
            committed.baseVersion != request.baseVersion || committed.workspaceVersion != workspaceVersion ||
            committed.operationType != submitted.kind || committed.schemaVersion != 1 ||
            committed.committedAt.isBlank()) invalid()
    }
}
