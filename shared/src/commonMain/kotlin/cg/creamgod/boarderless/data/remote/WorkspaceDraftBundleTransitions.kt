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

    fun append(
        current: WorkspaceDraftScopeBundle?,
        scope: PendingSubmissionScope,
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
        newDraftId: String,
    ): WorkspaceDraftScopeBundle {
        require(session.userId == scope.userId && session.clientId == scope.clientId)
        require(session.workspace.id == before.id && before.id.value == scope.workspaceId)
        require(newDraftId.isNotBlank())
        val previous = current?.let(::snapshot)
        require(previous == null || previous.scope == scope)
        require(previous?.stoppedPending == null)
        // Wire-only legacy state must be reconciled, not given an invented baseline.
        require(previous == null || previous.journal != null || (previous.pending == null && previous.retainedStopped.isNotEmpty()))
        val journal =
            previous?.journal ?: WorkspaceDraftJournal(
                scope = scope,
                id = newDraftId,
                baseVersion = session.workspaceVersion,
                baseServerSeq = session.lastServerSeq,
                baseWorkspace = before,
                operations = emptyList(),
            )
        require(!journal.quarantined && journal.replay() == before && journal.operations.size < 200)
        require(operation.operationId.isNotBlank() && journal.operations.none { it.operationId == operation.operationId })
        val result =
            (previous ?: WorkspaceDraftScopeBundle(scope = scope)).copy(
                journal = journal.copy(operations = journal.operations + operation),
                pending = previous?.pending,
                acknowledged = previous?.acknowledged,
                legacyImportDigest = previous?.legacyImportDigest,
            )
        return snapshot(result)
    }

    fun stage(
        current: WorkspaceDraftScopeBundle,
        pending: PendingWorkspaceSubmission,
    ): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        require(previous.scope == pending.scope)
        require(previous.stoppedPending == null)
        require(previous.retainedStopped.none { it.submitted.request.transactionId == pending.request.transactionId })
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
    fun restore(
        current: WorkspaceDraftScopeBundle,
        draftId: String,
        session: WorkspaceSession,
    ): WorkspaceDraftScopeBundle {
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
    fun quarantine(
        current: WorkspaceDraftScopeBundle,
        draftId: String,
    ): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val journal = requireNotNull(previous.journal)
        require(journal.id == draftId)
        return snapshot(previous.copy(journal = journal.copy(quarantined = true)))
    }

    fun requireRemovable(
        current: WorkspaceDraftScopeBundle,
        draftId: String,
    ) {
        val previous = snapshot(current)
        val journal = requireNotNull(previous.journal)
        require(journal.id == draftId)
        require(previous.pending == null && previous.stoppedPending == null && journal.headTransactionId == null) {
            "Unconfirmed submission evidence cannot be removed with the draft"
        }
    }

    /** Explicitly disable local retry; retain the entire unconfirmed request for reconciliation. */
    fun stop(
        current: WorkspaceDraftScopeBundle,
        transactionId: String,
    ): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val pending = requireNotNull(previous.pending)
        require(pending.request.transactionId == transactionId)
        return snapshot(
            previous.copy(
                pending = null,
                stoppedPending = pending,
                journal = previous.journal?.copy(quarantined = true, headTransactionId = null),
            ),
        )
    }

    /** Migration-only archival adoption, with explicit generation CAS by caller. No journal
     * linkage, acknowledgement or authority is inferred from a historical archive.
     */
    fun retainStopped(
        current: WorkspaceDraftScopeBundle?,
        scope: PendingSubmissionScope,
        entries: List<PendingWorkspaceSubmission>,
    ): WorkspaceDraftScopeBundle {
        require(entries.isNotEmpty() && entries.size <= 256)
        require(entries.map { it.request.transactionId }.distinct().size == entries.size)
        val previous = current?.let(::snapshot) ?: WorkspaceDraftScopeBundle(scope = scope)
        require(previous.scope == scope)
        val retained = previous.retainedStopped.toMutableList()
        entries.forEach { entry ->
            require(entry.scope == scope)
            validatePendingWorkspaceSubmission(entry)
            require(
                previous.pending?.request?.transactionId != entry.request.transactionId &&
                    previous.acknowledged
                        ?.submitted
                        ?.request
                        ?.transactionId != entry.request.transactionId,
            )
            if (previous.stoppedPending?.request?.transactionId == entry.request.transactionId) {
                require(previous.stoppedPending == entry)
            } else {
                val existing = retained.singleOrNull { it.submitted.request.transactionId == entry.request.transactionId }
                if (existing != null) {
                    require(existing.submitted == entry)
                } else {
                    retained += StoppedSubmissionArchive(entry)
                }
            }
        }
        if (retained.isEmpty()) return snapshot(previous)
        return snapshot(previous.copy(version = 3, retainedStopped = retained))
    }

    /** Caller must re-read authority before invoking. Preserve exact wire and every draft intent;
     * this transition records evidence only, it never unquarantines, rebases or authorizes Apply.
     */
    fun recordStoppedEvidence(
        current: WorkspaceDraftScopeBundle,
        submitted: PendingWorkspaceSubmission,
        evidence: StoppedSubmissionEvidence,
    ): WorkspaceDraftScopeBundle {
        val previous = snapshot(current)
        val retained = previous.retainedStopped.singleOrNull { it.submitted.request.transactionId == submitted.request.transactionId }
        require((previous.pending == null && previous.stoppedPending == submitted) || retained?.submitted == submitted)
        val status = evidence.validateAgainst(submitted)
        (if (retained != null) retained.evidence else previous.stoppedEvidence)?.let { old ->
            require(old.validateAgainst(submitted) == status) { "Conflicting terminal original submission outcomes" }
            require(old.receipt.receipts == evidence.receipt.receipts)
            old.acknowledgement?.let { prior ->
                val next = requireNotNull(evidence.acknowledgement)
                require(prior.operations.size == next.operations.size)
                prior.operations.zip(next.operations).forEach { (before, after) ->
                    require(
                        before.copy(payload = after.payload) == after &&
                            before.payload.preservesSubmittedValues(
                                after.payload,
                            ) && after.payload.preservesSubmittedValues(before.payload),
                    ) {
                        "Committed original records changed after verification"
                    }
                }
            }
            require(
                evidence.observedWorkspaceVersion >= old.observedWorkspaceVersion && evidence.observedServerSeq >= old.observedServerSeq,
            )
            require(
                evidence.receipt.headWorkspaceVersion >= old.receipt.headWorkspaceVersion &&
                    evidence.receipt.headServerSeq >= old.receipt.headServerSeq,
            )
        }
        return if (retained != null) {
            snapshot(
                previous.copy(
                    retainedStopped =
                        previous.retainedStopped.map {
                            if (it.submitted == submitted) it.copy(evidence = evidence) else it
                        },
                ),
            )
        } else {
            snapshot(previous.copy(version = if (previous.retainedStopped.isEmpty()) 2 else 3, stoppedEvidence = evidence))
        }
    }

    /** Caller must first validate the complete matching server outcome. ID alone is NOT that proof.
     * Local marker makes an exact repeated ack a no-op, but cannot authorize backup Apply.
     */
    fun acknowledge(
        current: WorkspaceDraftScopeBundle,
        submitted: PendingWorkspaceSubmission,
        version: Long,
        seq: Long,
    ): WorkspaceDraftScopeBundle {
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
        return snapshot(
            previous.copy(
                pending = null,
                acknowledged = LocalDraftAcknowledgement(submitted, version, seq),
                journal =
                    journal.copy(
                        baseWorkspace = after,
                        baseVersion = version,
                        baseServerSeq = seq,
                        operations = journal.operations.drop(1),
                        headTransactionId = null,
                        lastAcknowledgedTransactionId = submitted.request.transactionId,
                    ),
            ),
        )
    }
}
