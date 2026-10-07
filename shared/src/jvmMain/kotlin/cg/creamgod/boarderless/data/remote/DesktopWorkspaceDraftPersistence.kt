package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace

/** Repository adapter; configure only after migration/writer exclusion. No default activation. */
internal class DesktopWorkspaceDraftPersistence(
    private val store: DesktopDraftScopeBundleStore,
) : WorkspaceDraftPersistence {
    override val supportsAtomicStoppedEvidence = true

    override fun recoveryGeneration(scope: PendingSubmissionScope) = store.read(scope)?.generation

    override fun stoppedEvidence(scope: PendingSubmissionScope) = store.read(scope)?.bundle?.stoppedEvidence

    override fun stoppedEvidence(
        scope: PendingSubmissionScope,
        transactionId: String,
    ): StoppedSubmissionEvidence? {
        val bundle = store.read(scope)?.bundle ?: return null
        return if (bundle.stoppedPending?.request?.transactionId == transactionId) {
            bundle.stoppedEvidence
        } else {
            bundle.retainedStopped.singleOrNull { it.submitted.request.transactionId == transactionId }?.evidence
        }
    }

    override fun recordStoppedEvidence(
        scope: PendingSubmissionScope,
        expectedGeneration: Long,
        submitted: PendingWorkspaceSubmission,
        evidence: StoppedSubmissionEvidence,
    ) {
        require(submitted.scope == scope)
        store.update(scope, expectedGeneration) {
            WorkspaceDraftBundleTransitions.recordStoppedEvidence(requireNotNull(it), submitted, evidence)
        }
    }

    override fun journal(scope: PendingSubmissionScope) = store.read(scope)?.bundle?.journal

    override fun pending(scope: PendingSubmissionScope) = store.read(scope)?.bundle?.pending

    override fun stopped(scope: PendingSubmissionScope): List<PendingWorkspaceSubmission> {
        val bundle = store.read(scope)?.bundle ?: return emptyList()
        return listOfNotNull(bundle.stoppedPending) + bundle.retainedStopped.map { it.submitted }
    }

    private fun mutate(
        scope: PendingSubmissionScope,
        transform: (WorkspaceDraftScopeBundle?) -> WorkspaceDraftScopeBundle,
    ): WorkspaceDraftScopeBundle {
        val current = store.read(scope)
        return requireNotNull(store.update(scope, current?.generation, transform).bundle)
    }

    override fun append(
        scope: PendingSubmissionScope,
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) {
        mutate(scope) { WorkspaceDraftBundleTransitions.append(it, scope, session, before, operation, randomUuid()) }
    }

    override fun stage(entry: PendingWorkspaceSubmission) {
        mutate(entry.scope) {
            val current = requireNotNull(it)
            requireNotNull(current.journal) { "Legacy wire-only submission requires reconciliation before transport" }
            WorkspaceDraftBundleTransitions.stage(current, entry)
        }
    }

    override fun acknowledge(
        entry: PendingWorkspaceSubmission,
        version: Long,
        seq: Long,
    ) {
        val current = requireNotNull(store.read(entry.scope))
        val bundle = requireNotNull(current.bundle)
        val settled = WorkspaceDraftBundleTransitions.acknowledge(bundle, entry, version, seq)
        // An exact settlement needs no new generation. Still force its existing record and
        // directory: a fresh read alone cannot resolve a prior unknown durability barrier.
        if (settled == bundle) {
            require(store.confirmDurable(entry.scope, current.generation).bundle == bundle)
            return
        }
        store.update(entry.scope, current.generation) {
            require(it == bundle) { "Draft changed during acknowledgement" }
            settled
        }
    }

    override fun restore(
        scope: PendingSubmissionScope,
        id: String,
        current: WorkspaceSession,
    ): WorkspaceDraftJournal =
        requireNotNull(mutate(scope) { WorkspaceDraftBundleTransitions.restore(requireNotNull(it), id, current) }.journal)

    override fun remove(
        scope: PendingSubmissionScope,
        id: String,
    ): Boolean {
        val current = store.read(scope) ?: return false
        if (current.bundle?.journal?.id != id) return false
        store.removeDraft(scope, current.generation, id)
        return true
    }

    override fun stop(
        scope: PendingSubmissionScope,
        transactionId: String,
    ) {
        mutate(scope) { WorkspaceDraftBundleTransitions.stop(requireNotNull(it), transactionId) }
    }
}
