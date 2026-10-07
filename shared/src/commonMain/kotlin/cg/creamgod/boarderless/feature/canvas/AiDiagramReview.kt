package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.ai.AiDiagramRequestReview
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.CompletableDeferred

/** UI-only rendezvous. Approval is bound to the exact immutable review instance and consumed
 * once; stale buttons cannot approve another round. Close/cancel releases suspended requests.
 */
internal class AiDiagramRoundApproval(
    private val onPending: (AiDiagramRequestReview?) -> Unit,
) {
    private var pending: Pair<AiDiagramRequestReview, CompletableDeferred<Boolean>>? = null
    private var closed = false

    suspend fun await(review: AiDiagramRequestReview): Boolean {
        check(!closed && pending == null)
        val deferred = CompletableDeferred<Boolean>()
        pending = review to deferred
        try {
            onPending(review)
            return deferred.await()
        } finally {
            deferred.cancel()
            if (pending?.first === review) {
                pending = null
                onPending(null)
            }
        }
    }

    fun respond(
        review: AiDiagramRequestReview,
        approved: Boolean,
    ): Boolean {
        val current = pending ?: return false
        if (closed || current.first !== review) return false
        return current.second.complete(approved)
    }

    fun close() {
        closed = true
        pending?.second?.complete(false)
        pending = null
        onPending(null)
    }
}

internal enum class AiDiagramApplyFailure { Scope, Permission, Version, Snapshot, Empty, Conflict, Unsupported }

internal sealed interface AiDiagramApplyReview {
    data class Ready(
        val operation: TransactionOperation,
    ) : AiDiagramApplyReview

    data class Rejected(
        val reason: AiDiagramApplyFailure,
    ) : AiDiagramApplyReview
}

/** Pure final gate. Fresh authority must be obtained by the caller before this function. No
 * rebasing, identity allocation or submission; unchanged baseline is what the user reviewed.
 */
internal fun reviewAiDiagramApply(
    proposal: AiProposal,
    baseline: Workspace,
    current: Workspace,
    opened: WorkspaceSession,
    live: WorkspaceSession,
    fresh: WorkspaceSession,
): AiDiagramApplyReview {
    fun fail(reason: AiDiagramApplyFailure) = AiDiagramApplyReview.Rejected(reason)
    if (listOf(live, fresh).any { it.userId != opened.userId || it.clientId != opened.clientId || it.workspace.id != baseline.id } ||
        opened.workspace.id != baseline.id
    ) {
        return fail(AiDiagramApplyFailure.Scope)
    }
    if (!live.canEditContent || !fresh.canEditContent) return fail(AiDiagramApplyFailure.Permission)
    if (proposal.status != AiProposalStatus.Ready || proposal.contextWorkspaceVersion != baseline.version ||
        listOf(opened, live, fresh).any { it.workspaceVersion != baseline.version } || fresh.lastServerSeq < opened.lastServerSeq
    ) {
        return fail(AiDiagramApplyFailure.Version)
    }
    if (current != baseline || opened.workspace != baseline || live.workspace != baseline || fresh.workspace != baseline) {
        return fail(AiDiagramApplyFailure.Snapshot)
    }
    return when (val preview = proposal.preview(current)) {
        is AiProposalPreview.Ready -> {
            if (!aiSequenceUnchanged(current, preview.workspace)) fail(AiDiagramApplyFailure.Unsupported)
            else AiDiagramApplyReview.Ready(preview.operation)
        }
        AiProposalPreview.Empty -> fail(AiDiagramApplyFailure.Empty)
        is AiProposalPreview.Conflict -> fail(AiDiagramApplyFailure.Conflict)
    }
}

internal enum class AiDiagramApplyOutcome { Queued, Rejected }
