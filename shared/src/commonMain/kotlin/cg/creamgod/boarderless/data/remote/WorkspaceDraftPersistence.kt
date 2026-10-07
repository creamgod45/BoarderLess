package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace

/** Repository storage boundary. Implementations must not silently retry uncertain commits. */
internal interface WorkspaceDraftPersistence {
    val supportsAtomicStoppedEvidence: Boolean get() = false

    fun recoveryGeneration(scope: PendingSubmissionScope): Long? = null

    fun stoppedEvidence(scope: PendingSubmissionScope): StoppedSubmissionEvidence? = null

    fun stoppedEvidence(
        scope: PendingSubmissionScope,
        transactionId: String,
    ): StoppedSubmissionEvidence? = null

    fun recordStoppedEvidence(
        scope: PendingSubmissionScope,
        expectedGeneration: Long,
        submitted: PendingWorkspaceSubmission,
        evidence: StoppedSubmissionEvidence,
    ): Unit = throw UnsupportedOperationException("Atomic stopped evidence storage is unavailable")

    fun journal(scope: PendingSubmissionScope): WorkspaceDraftJournal?

    fun pending(scope: PendingSubmissionScope): PendingWorkspaceSubmission?

    fun stopped(scope: PendingSubmissionScope): List<PendingWorkspaceSubmission>

    fun append(
        scope: PendingSubmissionScope,
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    )

    fun stage(entry: PendingWorkspaceSubmission)

    fun acknowledge(
        entry: PendingWorkspaceSubmission,
        version: Long,
        seq: Long,
    )

    fun restore(
        scope: PendingSubmissionScope,
        id: String,
        current: WorkspaceSession,
    ): WorkspaceDraftJournal

    fun remove(
        scope: PendingSubmissionScope,
        id: String,
    ): Boolean

    fun stop(
        scope: PendingSubmissionScope,
        transactionId: String,
    )
}

/** Existing Settings behavior; deliberately does NOT claim atomic bundle/power-loss guarantees. */
internal class SettingsWorkspaceDraftPersistence(
    private val journals: WorkspaceDraftJournalStore,
    private val submissions: PendingWorkspaceSubmissionStore,
) : WorkspaceDraftPersistence {
    override fun journal(scope: PendingSubmissionScope) = journals.load(scope)

    override fun pending(scope: PendingSubmissionScope) = submissions.load(scope)

    override fun stopped(scope: PendingSubmissionScope) =
        submissions.stoppedInventory(
            scope,
            listOfNotNull(journals.load(scope)?.headTransactionId),
        )

    override fun append(
        scope: PendingSubmissionScope,
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) = journals.append(scope, session, before, operation)

    override fun stage(entry: PendingWorkspaceSubmission) {
        submissions.save(entry)
        journals.markSubmitted(entry.scope, entry.localOperationId, entry.request.transactionId)
    }

    override fun acknowledge(
        entry: PendingWorkspaceSubmission,
        version: Long,
        seq: Long,
    ) {
        journals.acknowledge(entry.scope, entry.localOperationId, entry.request.transactionId, version, seq)
        check(submissions.acknowledge(entry.scope, entry.request.transactionId))
        journals.removeEmptyAcknowledged(entry.scope, entry.request.transactionId)
    }

    override fun restore(
        scope: PendingSubmissionScope,
        id: String,
        current: WorkspaceSession,
    ) = journals.restore(scope, id, current)

    override fun remove(
        scope: PendingSubmissionScope,
        id: String,
    ) = journals.remove(scope, id)

    override fun stop(
        scope: PendingSubmissionScope,
        transactionId: String,
    ) {
        val pending = checkNotNull(submissions.load(scope))
        check(pending.request.transactionId == transactionId)
        submissions.archiveStopped(pending) // Verified exact wire BEFORE releasing the retry slot.
        val journal = journals.load(scope)
        journals.quarantine(scope)
        journal?.let {
            check(journals.load(scope) == it.copy(quarantined = true)) { "Draft quarantine was not saved" }
        }
        check(submissions.load(scope) == pending) { "Pending request changed while stopping resend" }
        check(submissions.acknowledge(scope, transactionId))
    }
}
