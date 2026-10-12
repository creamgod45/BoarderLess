package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow

data class WorkspaceSummary(
    val id: WorkspaceId,
    val title: String,
    val role: String,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
)

data class WorkspaceActivity(
    val serverSeq: Long,
    val actorId: String,
    val clientId: String,
    val workspaceVersion: Long,
    val operationType: String,
    val committedAt: String,
)

data class PendingWorkspaceChange(
    val transactionId: String,
    val operationCount: Int,
    val resendBlocked: Boolean = false,
)

data class StoppedWorkspaceChange(
    val transactionId: String,
    val operationCount: Int,
    val hasSavedEvidence: Boolean = false,
)

/** Transient verified read, not a persisted settlement or authorization for a replacement write. */
data class StoppedSubmissionVerification(
    val transactionId: String,
    val status: PendingReceiptStatus,
    val current: WorkspaceSession,
    val committedWorkspaceVersion: Long? = null,
    val committedServerSeq: Long? = null,
)

enum class PendingReceiptStatus { Committed, Fenced, Unknown }

/** Read-only server evidence. Not a full acknowledgement or permission to clear/resend a draft. */
data class PendingSubmissionReceipt(
    val status: PendingReceiptStatus,
    val headServerSeq: Long,
    val headWorkspaceVersion: Long,
    val committedWorkspaceVersion: Long? = null,
)

enum class WorkspaceMemberRole(
    val token: String,
) {
    Owner("owner"),
    Editor("editor"),
    Commenter("commenter"),
    Viewer("viewer"),
    ;

    companion object {
        fun fromToken(token: String): WorkspaceMemberRole? = entries.firstOrNull { it.token == token }
    }
}

data class WorkspaceMember(
    val userId: String,
    val displayName: String,
    val role: WorkspaceMemberRole,
    val joinedAt: String,
)

data class WorkspaceSession(
    val userId: String,
    val clientId: String,
    val role: WorkspaceMemberRole,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
    val workspace: Workspace,
)

data class PendingWorkspaceDraft(
    val id: String,
    val operationCount: Int,
    val requiresReview: Boolean,
)

data class RestoredWorkspaceDraft(
    val session: WorkspaceSession,
    val operations: List<WorkspaceOperation>,
    val history: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null,
)

/** A verified original commit, not an instruction to replay it on the current canvas. */
data class ConfirmedWorkspaceReceipt(
    val session: WorkspaceSession,
    val originalOperation: WorkspaceOperation? = null,
    val mediaReferenceOrigins: List<cg.creamgod.boarderless.domain.history.MediaReferenceHistoryOrigin> = emptyList(),
    val historyIntent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent = cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit,
    val confirmedHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null,
)

val WorkspaceSession.canEditContent: Boolean
    get() = role == WorkspaceMemberRole.Owner || role == WorkspaceMemberRole.Editor

sealed interface SubmitOutcome {
    data class Accepted(
        val workspaceVersion: Long,
        val lastServerSeq: Long,
        val mediaReferenceOrigins: List<cg.creamgod.boarderless.domain.history.MediaReferenceHistoryOrigin> = emptyList(),
    ) : SubmitOutcome

    data class Conflict(
        val current: WorkspaceSession,
    ) : SubmitOutcome
}

/** Configured transport capability, not a claim that a physical connection is currently live. */
enum class WorkspaceCollaborationTransport { Unavailable, RestPolling, WebSocket }

interface WorkspaceRepository {
    /** History may need committed original identity before generating an inverse. */
    val awaitsHistoryCommit: Boolean get() = false
    val collaborationTransport: WorkspaceCollaborationTransport get() = WorkspaceCollaborationTransport.Unavailable
    /** Stable fingerprint of the configured server route; never a credential. */
    val syncServerScope: String? get() = null
    val isLocalOnly: Boolean get() = false
    /** True when draft retention must await an asynchronous storage transaction. */
    val awaitsDraftPublication: Boolean get() = false
    val supportsExternalMedia: Boolean get() = false
    val supportsCanvasStyle: Boolean get() = false
    val supportsRelationGeometry: Boolean get() = false
    val supportsSequenceDiagrams: Boolean get() = false
    val supportsVectorPaths: Boolean get() = false
    val supportsTextPlacement: Boolean get() = false
    val cloudQuickSchemeServer: cg.creamgod.boarderless.data.persistence.CloudSchemeServer? get() = null

    /** Local storage capability only; false must not be treated as permission for multi-key writes. */
    val supportsAtomicStoppedEvidence: Boolean get() = false

    /** Opens [preferredWorkspaceId] when it can, otherwise the last opened or a new workspace. */
    suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId? = null): WorkspaceSession

    /** Local confirmed stacks only; the supplied authoritative canvas is never replaced. */
    suspend fun restoreConfirmedHistory(session: WorkspaceSession): cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null

    suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary>

    suspend fun createWorkspace(
        session: WorkspaceSession,
        title: String,
    ): WorkspaceSession

    suspend fun openWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ): WorkspaceSession

    suspend fun renameWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
        title: String,
    ): WorkspaceSummary

    suspend fun deleteWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    )

    suspend fun listRecentActivity(
        session: WorkspaceSession,
        limit: Int = 100,
    ): List<WorkspaceActivity>

    suspend fun listWorkspaceMembers(session: WorkspaceSession): List<WorkspaceMember>

    suspend fun setWorkspaceMemberRole(
        session: WorkspaceSession,
        userId: String,
        role: WorkspaceMemberRole,
    )

    suspend fun removeWorkspaceMember(
        session: WorkspaceSession,
        userId: String,
    )

    suspend fun refresh(session: WorkspaceSession): WorkspaceSession

    /** Optional authenticated, session-scoped notification stream. Null keeps REST fallback. */
    fun observeRemoteChanges(session: WorkspaceSession): Flow<WorkspaceRemoteNotification>? = null

    /** Authenticated ephemeral snapshots only; null means presence is unavailable.
     * Echo this local subscription nonce, not a peer-supplied identity. Reconnect adapters must
     * validate connection epoch and emit renewed full snapshots using server-confirmed liveness.
     * This stream cannot change membership, content, acknowledgements or saved checkpoints.
     */
    fun observePresence(
        session: WorkspaceSession,
        subscriptionId: String,
    ): Flow<WorkspacePresenceSnapshot>? = null

    /** Optional outgoing handle for an authenticated live room; null means unavailable.
     * No guessed endpoint or automatic sharing. Must be opened only after trusted Live presence.
     * The transport owns physical connection epoch validation, including at the final write.
     */
    fun openPresencePublisher(
        session: WorkspaceSession,
        subscriptionId: String,
        roomEpoch: String,
    ): WorkspacePresencePublisher? = null

    suspend fun pendingChange(session: WorkspaceSession): PendingWorkspaceChange? = null

    /** Prepare and save exact wire IDs/client sequence without HTTP. Requires a saved draft.
     * Repeat with the same draft only; an existing different pending request cannot be replaced. */
    suspend fun prepareSubmission(session: WorkspaceSession, operation: WorkspaceOperation): PreparedWorkspaceSubmission =
        throw UnsupportedOperationException("Two-phase submission preparation is unavailable")

    /** Inspect saved identity only. Not a receipt or permission to send. */
    suspend fun preparedSubmission(session: WorkspaceSession): PreparedWorkspaceSubmission? = null
    /** Verify original receipt/log using saved identity, even after adapter pending ACK cleanup.
     * Never sends canvas operations or updates local recovery state. */
    suspend fun verifyPreparedSubmission(session: WorkspaceSession, prepared: PreparedWorkspaceSubmission,
        operation: WorkspaceOperation, baseline: WorkspaceSession? = null): PreparedSubmissionVerification =
        throw UnsupportedOperationException("Prepared submission verification is unavailable")

    /** Explicit send of the saved request. Caller owns fresh ACL/scope checks and consent;
     * unknown response recovery must inspect the original receipt before deciding to resend. */
    suspend fun sendPreparedSubmission(session: WorkspaceSession, prepared: PreparedWorkspaceSubmission): SubmitOutcome =
        throw UnsupportedOperationException("Prepared submission transport is unavailable")

    /** Local archive summaries only; no payload disclosure or permission to resend/merge. */
    suspend fun stoppedChanges(session: WorkspaceSession): List<StoppedWorkspaceChange> = emptyList()

    suspend fun inspectStoppedReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): PendingSubmissionReceipt = throw UnsupportedOperationException("Stopped submission receipt is unavailable")

    suspend fun verifyStoppedSubmission(
        session: WorkspaceSession,
        transactionId: String,
    ): StoppedSubmissionVerification = throw UnsupportedOperationException("Stopped submission verification is unavailable")

    /** Explicitly save freshly verified evidence atomically; does not merge or clear quarantine. */
    suspend fun saveStoppedSubmissionEvidence(
        session: WorkspaceSession,
        transactionId: String,
    ): StoppedSubmissionVerification = throw UnsupportedOperationException("Atomic stopped evidence storage is unavailable")

    /** Only inspects this device's exact pending request; never fences or mutates local recovery. */
    suspend fun inspectPendingReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): PendingSubmissionReceipt? = null

    /** Explicit irreversible server fence, only after user confirmation. Never discards a draft. */
    suspend fun fencePendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): PendingSubmissionReceipt = throw UnsupportedOperationException("Original submission fence is unavailable")

    /** Explicitly settle a committed original transaction using full server records, never resend. */
    suspend fun confirmPendingReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession = throw UnsupportedOperationException("Receipt settlement is unavailable")

    suspend fun confirmPendingReceiptWithHistory(
        session: WorkspaceSession,
        transactionId: String,
    ): ConfirmedWorkspaceReceipt = ConfirmedWorkspaceReceipt(confirmPendingReceipt(session, transactionId))

    /** Explicit saved-wire retry; history is returned only after the original ACK is verified. */
    suspend fun retryPendingChangeWithHistory(
        session: WorkspaceSession,
        transactionId: String,
    ): ConfirmedWorkspaceReceipt = ConfirmedWorkspaceReceipt(retryPendingChange(session, transactionId))

    suspend fun retainHistoryDraftAwaited(session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null) {
        retainDraftAwaited(session, before, operation)
    }

    /** Save before optimistic publication. Default repositories may not support local journals. */
    fun retainDraft(
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) {}

    fun retainHistoryDraft(session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null) {
        retainDraft(session, before, operation)
    }

    /** Await actual durable publication before exposing a derived canvas/history result. */
    suspend fun retainDraftAwaited(
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) { retainDraft(session, before, operation) }

    suspend fun pendingDraft(session: WorkspaceSession): PendingWorkspaceDraft? = null

    suspend fun reviewPendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): WorkspaceDraftReview = throw UnsupportedOperationException("Draft review is unavailable")

    suspend fun restorePendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): RestoredWorkspaceDraft = throw UnsupportedOperationException("Draft recovery is unavailable")

    suspend fun dismissPendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): WorkspaceSession = throw UnsupportedOperationException("Draft recovery is unavailable")

    /** Explicit resend, followed by an authoritative refresh, never an optimistic old snapshot. */
    suspend fun retryPendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession = throw UnsupportedOperationException("Pending recovery is unavailable")

    /** Stops local resend only. Does not undo/delete anything already accepted by the server. */
    suspend fun dismissPendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession = throw UnsupportedOperationException("Pending recovery is unavailable")

    suspend fun submit(
        session: WorkspaceSession,
        operation: WorkspaceOperation,
    ): SubmitOutcome

    fun close()
}

/** Canvas content and asset lookup share one explicitly selected storage destination. */
interface CanvasWorkspaceRepository :
    WorkspaceRepository,
    AssetRepository {
    val localSyncStore: LocalWorkspaceSyncStore? get() = null
    val quickSchemeRepository: cg.creamgod.boarderless.data.persistence.QuickSchemeRepository? get() = null
    /** Explicit legacy metadata recovery only; never called during ordinary library startup. */
    val legacyQuickSchemeReader: (suspend () -> List<cg.creamgod.boarderless.data.persistence.QuickScheme>)? get() = null
    val localAssetGateway: AssetTransferGateway? get() = null
    /** Owned remote gateway from this repository's exact endpoint configuration. */
    val syncAssetGatewayFactory: (() -> AssetTransferGateway)? get() = null
    val syncAssetPreparationGatewayFactory: (() -> cg.creamgod.boarderless.data.remote.BackendAssetPreparationGateway)? get() = null
    fun preparedMediaImport(session: WorkspaceSession, canContinue: () -> Boolean): PreparedMediaImportController? = null
    /** Null for local-only repositories. Counts actual expanded protocol operations. */
    val maximumAtomicOperations: Int? get() = null
    fun submissionOperationCount(operation: WorkspaceOperation): Int? = null
}

internal fun WorkspaceSession.hasRemoteChangesComparedTo(current: WorkspaceSession): Boolean =
    userId == current.userId && clientId == current.clientId && workspace.id == current.workspace.id &&
        workspaceVersion >= current.workspaceVersion &&
        lastServerSeq >= current.lastServerSeq &&
        (
            workspaceVersion > current.workspaceVersion ||
                lastServerSeq > current.lastServerSeq ||
                role != current.role ||
                workspace.title != current.workspace.title
        )

/** A slow refresh cannot overwrite a changed owner or an advanced durable checkpoint. */
internal fun shouldApplyRemoteRefresh(
    requested: WorkspaceSession,
    current: WorkspaceSession,
    refreshed: WorkspaceSession,
): Boolean =
    current.userId == requested.userId && current.clientId == requested.clientId &&
        current.workspace.id == requested.workspace.id && current.workspaceVersion == requested.workspaceVersion &&
        current.lastServerSeq == requested.lastServerSeq && current.role == requested.role &&
        current.workspace.title == requested.workspace.title && refreshed.hasRemoteChangesComparedTo(current)
