package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace

/** Repository adapter; configure only after migration/writer exclusion. No default activation. */
internal class DesktopWorkspaceDraftPersistence(private val store: DesktopDraftScopeBundleStore) : WorkspaceDraftPersistence {
    override fun journal(scope: PendingSubmissionScope) = store.read(scope)?.bundle?.journal
    override fun pending(scope: PendingSubmissionScope) = store.read(scope)?.bundle?.pending
    private fun mutate(scope: PendingSubmissionScope, transform: (WorkspaceDraftScopeBundle?) -> WorkspaceDraftScopeBundle): WorkspaceDraftScopeBundle {
        val current = store.read(scope)
        return requireNotNull(store.update(scope, current?.generation, transform).bundle)
    }
    override fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
        mutate(scope) { WorkspaceDraftBundleTransitions.append(it, scope, session, before, operation, randomUuid()) }
    }
    override fun stage(entry: PendingWorkspaceSubmission) {
        mutate(entry.scope) {
            val current = requireNotNull(it)
            requireNotNull(current.journal) { "Legacy wire-only submission requires reconciliation before transport" }
            WorkspaceDraftBundleTransitions.stage(current, entry)
        }
    }
    override fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) {
        mutate(entry.scope) { WorkspaceDraftBundleTransitions.acknowledge(requireNotNull(it), entry, version, seq) }
    }
    override fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession): WorkspaceDraftJournal =
        requireNotNull(mutate(scope) { WorkspaceDraftBundleTransitions.restore(requireNotNull(it), id, current) }.journal)
    override fun remove(scope: PendingSubmissionScope, id: String): Boolean {
        val current = store.read(scope) ?: return false
        if (current.bundle?.journal?.id != id) return false
        store.removeDraft(scope, current.generation, id)
        return true
    }
    override fun stop(scope: PendingSubmissionScope, transactionId: String) {
        mutate(scope) { WorkspaceDraftBundleTransitions.stop(requireNotNull(it), transactionId) }
    }
}
