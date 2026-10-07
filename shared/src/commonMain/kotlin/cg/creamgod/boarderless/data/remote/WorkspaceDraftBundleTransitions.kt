package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace

/** Pure transitions to be committed with generation CAS before optimistic UI/network I/O.
 * No network authorization, retry policy, receipt reconciliation or migration is implied.
 */
internal object WorkspaceDraftBundleTransitions {
    private fun snapshot(bundle: WorkspaceDraftScopeBundle): WorkspaceDraftScopeBundle =
        WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(bundle), bundle.scope)

    fun append(current: WorkspaceDraftScopeBundle?, scope: PendingSubmissionScope, session: WorkspaceSession,
               before: Workspace, operation: WorkspaceOperation, newDraftId: String): WorkspaceDraftScopeBundle {
        require(session.userId == scope.userId && session.clientId == scope.clientId)
        require(session.workspace.id == before.id && before.id.value == scope.workspaceId)
        require(newDraftId.isNotBlank())
        val previous = current?.let(::snapshot)
        require(previous == null || previous.scope == scope)
        require(previous?.stoppedPending == null)
        // Wire-only legacy state must be reconciled, not given an invented baseline.
        require(previous == null || previous.journal != null)
        val journal = previous?.journal ?: WorkspaceDraftJournal(scope = scope, id = newDraftId,
            baseVersion = session.workspaceVersion, baseServerSeq = session.lastServerSeq,
            baseWorkspace = before, operations = emptyList())
        require(!journal.quarantined && journal.replay() == before && journal.operations.size < 200)
        require(operation.operationId.isNotBlank() && journal.operations.none { it.operationId == operation.operationId })
        val result = WorkspaceDraftScopeBundle(scope = scope,
            journal = journal.copy(operations = journal.operations + operation), pending = previous?.pending,
            acknowledged = previous?.acknowledged, legacyImportDigest = previous?.legacyImportDigest)
        return snapshot(result)
    }

    fun stage(current: WorkspaceDraftScopeBundle, pending: PendingWorkspaceSubmission): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        require(previous.scope == pending.scope)
        require(previous.stoppedPending == null)
        require(previous.journal?.quarantined != true) { "Quarantined draft must not stage or retry a wire" }
        previous.pending?.let {
            require(it == pending) { "An unresolved wire identity cannot be replaced" }
            return previous
        }
        val journal = requireNotNull(previous.journal)
        require(!journal.quarantined && journal.headTransactionId == null)
        require(journal.lastAcknowledgedTransactionId != pending.request.transactionId)
        return snapshot(previous.copy(journal = journal.copy(headTransactionId = pending.request.transactionId), pending = pending))
    }

    /** Fresh authority must be fetched by caller; saved baseline is never treated as permission. */
    fun restore(current: WorkspaceDraftScopeBundle, draftId: String, session: WorkspaceSession): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val scope = previous.scope
        require(session.userId == scope.userId && session.clientId == scope.clientId && session.workspace.id.value == scope.workspaceId)
        require(session.canEditContent)
        require(previous.pending == null)
        val journal = requireNotNull(previous.journal)
        require(journal.id == draftId && !journal.quarantined && journal.headTransactionId == null)
        require(session.workspaceVersion == journal.baseVersion && session.lastServerSeq == journal.baseServerSeq)
        require(session.workspace.copy(version = 0, title = "") == journal.baseWorkspace.copy(version = 0, title = ""))
        return snapshot(previous.copy(journal = journal.copy(baseWorkspace = session.workspace)))
    }

    /** Stops local staging without deleting unresolved wire evidence or claiming server cancellation. */
    fun quarantine(current: WorkspaceDraftScopeBundle, draftId: String): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val journal = requireNotNull(previous.journal)
        require(journal.id == draftId)
        return snapshot(previous.copy(journal = journal.copy(quarantined = true)))
    }

    fun requireRemovable(current: WorkspaceDraftScopeBundle, draftId: String) {
        val previous = snapshot(current)
        val journal = requireNotNull(previous.journal)
        require(journal.id == draftId)
        require(previous.pending == null && previous.stoppedPending == null && journal.headTransactionId == null) {
            "Unconfirmed submission evidence cannot be removed with the draft"
        }
    }

    /** Explicitly disable local retry; retain the entire unconfirmed request for reconciliation. */
    fun stop(current: WorkspaceDraftScopeBundle, transactionId: String): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val pending = requireNotNull(previous.pending)
        require(pending.request.transactionId == transactionId)
        return snapshot(previous.copy(pending = null, stoppedPending = pending,
            journal = previous.journal?.copy(quarantined = true, headTransactionId = null)))
    }

    /** Caller must first validate the complete matching server outcome. ID alone is NOT that proof.
     * Local marker makes an exact repeated ack a no-op, but cannot authorize backup Apply.
     */
    fun acknowledge(current: WorkspaceDraftScopeBundle, submitted: PendingWorkspaceSubmission,
                    version: Long, seq: Long): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        require(previous.scope == submitted.scope)
        validatePendingWorkspaceSubmission(submitted)
        val journal = requireNotNull(previous.journal) // Legacy wire-only needs explicit reconciliation.
        if (previous.pending == null && journal.lastAcknowledgedTransactionId == submitted.request.transactionId) {
            require(previous.acknowledged == LocalDraftAcknowledgement(submitted, version, seq))
            require(version == journal.baseVersion && seq == journal.baseServerSeq)
            return previous
        }
        require(previous.pending == submitted)
        require(version > journal.baseVersion && seq > journal.baseServerSeq)
        val operation = journal.operations.first()
        val after = (operation.applyTo(journal.baseWorkspace) as OperationResult.Applied).workspace
        return snapshot(previous.copy(pending = null, acknowledged = LocalDraftAcknowledgement(submitted, version, seq),
            journal = journal.copy(baseWorkspace = after,
            baseVersion = version, baseServerSeq = seq, operations = journal.operations.drop(1),
            headTransactionId = null, lastAcknowledgedTransactionId = submitted.request.transactionId)))
    }
}
