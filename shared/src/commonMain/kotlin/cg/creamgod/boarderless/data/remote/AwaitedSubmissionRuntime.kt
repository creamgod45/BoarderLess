package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only configured after identity/floor and recovery-safety activation. Construction
 * itself never initializes a ledger, migrates Settings or changes the selected client. */
class AwaitedSubmissionRuntime internal constructor(internal val drafts: AwaitedWorkspaceDraftPersistence,
    internal val sequences: AwaitedClientSequenceAllocator, internal val safety: AwaitedRecoverySafetyPersistence,
    internal val requireActive: (PendingSubmissionScope) -> Unit,
    internal val activationTarget: PendingSubmissionScope) {
    /** Factories keep the original origin and identity. These checks never initialize a
     * client, adopt a new namespace, or change the selected workspace in Settings. */
    internal fun serverBaseUrl(): String {
        requireActive(activationTarget)
        require(activationTarget.apiBase.endsWith("/api/v1"))
        return activationTarget.apiBase.removeSuffix("/api/v1").also { require(it.isNotBlank()) }
    }

    internal fun originalPreferences(preferences: SessionPreferences = SessionPreferences()): SessionPreferences {
        requireActive(activationTarget)
        val selected = activationTarget.copy(workspaceId = requireNotNull(preferences.workspaceId) {
            "Original workspace selection is missing"
        })
        requireActive(selected)
        preferences.requireExistingLegacyIdentity(selected)
        requireActive(selected)
        return preferences
    }
}

/** Repository-facing awaited operations. Legacy records stay in their existing namespace;
 * the adapter never merges the two stores or falls back after an awaited write fails. */
internal class AwaitedDraftBoundary(private val legacy: WorkspaceDraftPersistence,
    private val awaited: AwaitedWorkspaceDraftPersistence?, private val operations: ServerWriterOperationLease? = null) {
    private suspend fun <T> legacyWork(action: () -> T): T = withContext(Dispatchers.Default) {
        if (operations != null) operations.run(action) else action()
    }
    val supportsAtomicStoppedEvidence: Boolean get() = awaited != null || legacy.supportsAtomicStoppedEvidence
    suspend fun journal(scope: PendingSubmissionScope) = if (awaited != null) awaited.read(scope)?.bundle?.journal
        else legacyWork { legacy.journal(scope) }
    suspend fun pending(scope: PendingSubmissionScope) = if (awaited != null) awaited.read(scope)?.bundle?.pending
        else legacyWork { legacy.pending(scope) }
    suspend fun stopped(scope: PendingSubmissionScope): List<PendingWorkspaceSubmission> = if (awaited != null) {
        val bundle = awaited.read(scope)?.bundle
        listOfNotNull(bundle?.stoppedPending) + bundle?.retainedStopped.orEmpty().map { it.submitted }
    } else legacyWork { legacy.stopped(scope) }
    suspend fun recoveryGeneration(scope: PendingSubmissionScope) = if (awaited != null) awaited.read(scope)?.generation
        else legacyWork { legacy.recoveryGeneration(scope) }
    suspend fun stoppedEvidence(scope: PendingSubmissionScope, transactionId: String): StoppedSubmissionEvidence? = if (awaited != null) {
        val bundle = awaited.read(scope)?.bundle
        if (bundle?.stoppedPending?.request?.transactionId == transactionId) bundle?.stoppedEvidence
        else bundle?.retainedStopped?.singleOrNull { it.submitted.request.transactionId == transactionId }?.evidence
    } else legacyWork { legacy.stoppedEvidence(scope, transactionId) }
    suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
        if (awaited != null) awaited.append(scope, session, before, operation)
        else legacyWork { legacy.append(scope, session, before, operation) }
    }
    suspend fun stage(entry: PendingWorkspaceSubmission) {
        if (awaited != null) awaited.stage(entry) else legacyWork { legacy.stage(entry) }
    }
    suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null) {
        if (awaited != null) awaited.appendWithIntent(scope, session, before, operation, intent, beforeHistory)
        else legacyWork { legacy.appendWithIntent(scope, session, before, operation, intent, beforeHistory) }
    }
    suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) {
        if (awaited != null) awaited.acknowledge(entry, version, seq)
        else legacyWork { legacy.acknowledge(entry, version, seq) }
    }
    suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
        history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) {
        if (awaited != null) awaited.acknowledgeWithHistory(entry, version, seq, history)
        else legacyWork { legacy.acknowledgeWithHistory(entry, version, seq, history) }
    }
    suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession): WorkspaceDraftJournal =
        if (awaited != null) awaited.restore(scope, id, current) else legacyWork { legacy.restore(scope, id, current) }
    suspend fun remove(scope: PendingSubmissionScope, id: String) = if (awaited != null) awaited.remove(scope, id)
        else legacyWork { legacy.remove(scope, id) }
    suspend fun stop(scope: PendingSubmissionScope, transactionId: String) {
        if (awaited != null) awaited.stop(scope, transactionId)
        else legacyWork { legacy.stop(scope, transactionId) }
    }
    suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, generation: Long, submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence) {
        if (awaited != null) awaited.recordStoppedEvidence(scope, generation, submitted, evidence)
        else legacyWork { legacy.recordStoppedEvidence(scope, generation, submitted, evidence) }
    }
}
internal class AwaitedLegacyClientSequenceAllocator(private val legacy: ClientSequenceAllocator,
    private val operations: ServerWriterOperationLease? = null) : AwaitedClientSequenceAllocator {
    override suspend fun reserve(scope: PendingSubmissionScope, count: Int) = withContext(Dispatchers.Default) {
        if (operations != null) operations.run { legacy.reserve(scope, count) } else legacy.reserve(scope, count)
    }
    override suspend fun advance(scope: PendingSubmissionScope, sequence: Long) = withContext(Dispatchers.Default) {
        if (operations != null) operations.run { legacy.advance(scope, sequence) } else legacy.advance(scope, sequence)
    }
}
