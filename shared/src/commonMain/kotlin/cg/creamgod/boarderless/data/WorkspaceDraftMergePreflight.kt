package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class DraftMergePreflightBlock {
    ScopeExpired,
    ReadFailed,
    CannotEdit,
    PendingSubmission,
    PendingDraft,
    QuarantinedBackup,
    UnconfirmedBackup,
    ContractUnavailable,
    AssetUnavailable,
}

internal sealed interface DraftMergePreflightResult {
    data class Blocked(
        val reason: DraftMergePreflightBlock,
        val contractGaps: Set<DraftMergeContractGap> = emptySet(),
    ) : DraftMergePreflightResult

    data class ReviewAgain(
        val current: WorkspaceSession,
    ) : DraftMergePreflightResult

    /** Read-only proposal, NOT durable authorization. Confirmation/submission must revalidate it. */
    data class Checked(
        val session: WorkspaceSession,
        val target: Workspace,
        val operation: TransactionOperation?,
    ) : DraftMergePreflightResult
}

/** Explicit read-only replanning; keep original draft/history/flags, replace only remote comparison. */
internal fun refreshDraftMergeReview(
    review: WorkspaceDraftReview,
    current: WorkspaceSession,
    expected: WorkspaceSession,
): WorkspaceDraftReview {
    require(
        current.userId == expected.userId && current.clientId == expected.clientId &&
            current.workspace.id == expected.workspace.id && current.workspace.id == review.current.id,
    )
    require(current.workspaceVersion >= review.currentVersion && current.lastServerSeq >= review.currentServerSeq)
    return review.copy(
        current = current.workspace,
        currentVersion = current.workspaceVersion,
        currentServerSeq = current.lastServerSeq,
    )
}

/** No POST, journal, adoption, retry or deletion. Callback reads must be authenticated and scoped.
 * Backup flags can block but their absence does not establish that original submissions settled.
 * Later confirmation still needs original-operation reconciliation and tombstone/restore contracts.
 */
internal suspend fun checkDraftMergeProposal(
    plan: WorkspaceDraftMergePlan,
    choices: Map<DraftMergeFieldId, DraftMergeChoice>,
    opened: WorkspaceSession,
    canPublish: () -> Boolean,
    refresh: suspend (WorkspaceSession) -> WorkspaceSession,
    pendingChange: suspend (WorkspaceSession) -> PendingWorkspaceChange?,
    pendingDraft: suspend (WorkspaceSession) -> PendingWorkspaceDraft?,
    getAsset: suspend (WorkspaceSession, String) -> WorkspaceAsset,
): DraftMergePreflightResult {
    val selected = choices.toMap() // Freeze the user's decisions before any asynchronous read.

    fun blocked(reason: DraftMergePreflightBlock) = DraftMergePreflightResult.Blocked(reason)

    suspend fun guard(): Boolean {
        currentCoroutineContext().ensureActive()
        return canPublish()
    }

    fun sameScope(session: WorkspaceSession) =
        session.userId == opened.userId &&
            session.clientId == opened.clientId && session.workspace.id == opened.workspace.id

    suspend fun pending(session: WorkspaceSession): DraftMergePreflightBlock? {
        if (!guard()) return DraftMergePreflightBlock.ScopeExpired
        val change = pendingChange(session)
        if (!guard()) return DraftMergePreflightBlock.ScopeExpired
        if (change != null) return DraftMergePreflightBlock.PendingSubmission
        val draft = pendingDraft(session)
        if (!guard()) return DraftMergePreflightBlock.ScopeExpired
        return if (draft != null) DraftMergePreflightBlock.PendingDraft else null
    }
    try {
        if (!guard()) return blocked(DraftMergePreflightBlock.ScopeExpired)
        val fresh = refresh(opened)
        if (!guard() || !sameScope(fresh) || fresh.workspaceVersion < opened.workspaceVersion ||
            fresh.lastServerSeq < opened.lastServerSeq
        ) {
            return blocked(DraftMergePreflightBlock.ScopeExpired)
        }
        if (!fresh.canEditContent) return blocked(DraftMergePreflightBlock.CannotEdit)
        if (fresh.workspace != plan.review.current || fresh.workspaceVersion != plan.review.currentVersion ||
            fresh.lastServerSeq != plan.review.currentServerSeq
        ) {
            return DraftMergePreflightResult.ReviewAgain(fresh)
        }
        pending(fresh)?.let { return blocked(it) }
        if (plan.review.quarantined) return blocked(DraftMergePreflightBlock.QuarantinedBackup)
        if (plan.review.hasUnconfirmedSubmission) return blocked(DraftMergePreflightBlock.UnconfirmedBackup)
        val target = plan.resolve(selected)
        val operation = buildDraftMergeOperation(plan, selected, fresh.workspace)
        val gaps = operation?.let { draftMergeContractGaps(fresh.workspace, it) }.orEmpty()
        if (gaps.isNotEmpty()) return DraftMergePreflightResult.Blocked(DraftMergePreflightBlock.ContractUnavailable, gaps)

        // Only changed references need reads; unrelated remote missing media must not block text edits.
        // Both before and after references are checked because Undo must not bypass asset ACL.
        val cache = mutableMapOf<String, WorkspaceAsset>()

        suspend fun validate(
            node: MediaNode,
            requireReady: Boolean,
        ): Boolean {
            for (id in listOfNotNull(node.assetId, node.thumbnailAssetId).distinct()) {
                if (!guard()) return false
                val asset = cache[id] ?: getAsset(fresh, id).also { cache[id] = it }
                if (!guard()) return false
                if (asset.id != id || asset.workspaceId != fresh.workspace.id) return false
                if (requireReady && asset.status != AssetStatus.Ready) return false
                if (id == node.assetId && mediaKindForAssetMediaType(asset.mediaType) != node.mediaKind) return false
                if (id == node.thumbnailAssetId && mediaKindForAssetMediaType(asset.mediaType) != MediaKind.Image) return false
            }
            return true
        }
        val changed =
            target.objects.values.filterIsInstance<MediaNode>().filter { desired ->
                val before = fresh.workspace.objects[desired.id] as? MediaNode
                before?.mediaReference() != desired.mediaReference()
            }
        check(changed.size <= 200)
        for (desired in changed) {
            val before = fresh.workspace.objects[desired.id] as? MediaNode
            if ((before != null && !validate(before, false)) || !validate(desired, true)) {
                return blocked(if (guard()) DraftMergePreflightBlock.AssetUnavailable else DraftMergePreflightBlock.ScopeExpired)
            }
        }
        if (!guard()) return blocked(DraftMergePreflightBlock.ScopeExpired)
        val latest = refresh(fresh)
        if (!guard() || !sameScope(latest)) return blocked(DraftMergePreflightBlock.ScopeExpired)
        if (!latest.canEditContent) return blocked(DraftMergePreflightBlock.CannotEdit)
        if (latest.workspaceVersion < fresh.workspaceVersion || latest.lastServerSeq < fresh.lastServerSeq) {
            return blocked(DraftMergePreflightBlock.ScopeExpired)
        }
        if (latest != fresh) return DraftMergePreflightResult.ReviewAgain(latest)
        pending(latest)?.let { return blocked(it) }
        return DraftMergePreflightResult.Checked(latest, target, operation)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        return blocked(if (canPublish()) DraftMergePreflightBlock.ReadFailed else DraftMergePreflightBlock.ScopeExpired)
    }
}
