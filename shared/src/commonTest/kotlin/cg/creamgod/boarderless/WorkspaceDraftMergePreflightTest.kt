package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WorkspaceDraftMergePreflightTest {
    private val media = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)),
        assetId = "old", mediaKind = MediaKind.Video)
    private val workspace = Workspace(WorkspaceId("workspace"), "Board", objects = mapOf(media.id to media))
    private val opened = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 0, 0, workspace)
    private val target = workspace.copy(objects = mapOf(media.id to media.copy(assetId = "new", mediaKind = MediaKind.Image)))
    private fun plan(after: Workspace = target, quarantined: Boolean = false, unconfirmed: Boolean = false) =
        WorkspaceDraftMergePlan(WorkspaceDraftReview("draft", 0, 0, 0, 0, workspace, after, workspace,
            emptyList(), quarantined, unconfirmed))
    private fun asset(id: String) = WorkspaceAsset(id, workspace.id, "owner", if (id == "old") "video/mp4" else "image/png",
        100, "checksum", 100, 80, null, if (id == "old") AssetStatus.Missing else AssetStatus.Ready, "date")
    private suspend fun check(plan: WorkspaceDraftMergePlan = plan(), active: () -> Boolean = { true },
        refresh: suspend (WorkspaceSession) -> WorkspaceSession = { it },
        pending: suspend (WorkspaceSession) -> PendingWorkspaceChange? = { null },
        draft: suspend (WorkspaceSession) -> PendingWorkspaceDraft? = { null },
        assets: suspend (WorkspaceSession, String) -> WorkspaceAsset = { _, id -> asset(id) }) =
        checkDraftMergeProposal(plan, plan.fields.associate { it.id to DraftMergeChoice.UseDraft }, opened,
            active, refresh, pending, draft, assets)
    private fun reason(result: DraftMergePreflightResult) = assertIs<DraftMergePreflightResult.Blocked>(result).reason

    @Test fun readsBothReferencesAndRechecksAuthorityWithoutAdoptingAnything() = runTest {
        var refreshes = 0
        var pendingReads = 0
        val ids = mutableListOf<String>()
        val result = assertIs<DraftMergePreflightResult.Checked>(check(refresh = { refreshes++; it },
            pending = { pendingReads++; null }, assets = { _, id -> ids.add(id); asset(id) }))
        assertEquals(listOf("old", "new"), ids)
        assertEquals(2, refreshes)
        assertEquals(2, pendingReads)
        assertEquals(target, result.target)
        assertNotNull(result.operation)
        assertEquals("old", (opened.workspace.objects.getValue(media.id) as MediaNode).assetId)
    }

    @Test fun revokedAndStaleAuthorityNeverReadsAssets() = runTest {
        val forbidden: suspend (WorkspaceSession, String) -> WorkspaceAsset = { _, _ -> error("Must not read") }
        assertEquals(DraftMergePreflightBlock.CannotEdit, reason(check(refresh = { it.copy(role = WorkspaceMemberRole.Viewer) }, assets = forbidden)))
        val stale = assertIs<DraftMergePreflightResult.ReviewAgain>(check(refresh = { it.copy(workspaceVersion = 1) }, assets = forbidden))
        assertEquals(1, stale.current.workspaceVersion)
        assertEquals(DraftMergePreflightBlock.ScopeExpired, reason(check(refresh = { it.copy(userId = "other") }, assets = forbidden)))
    }

    @Test fun pendingAndBackupFlagsBlockWithoutClearingOrRetrying() = runTest {
        assertEquals(DraftMergePreflightBlock.PendingSubmission, reason(check(pending = { PendingWorkspaceChange("transaction", 1) })))
        assertEquals(DraftMergePreflightBlock.PendingDraft, reason(check(draft = { PendingWorkspaceDraft("local", 1, true) })))
        assertEquals(DraftMergePreflightBlock.QuarantinedBackup, reason(check(plan(quarantined = true))))
        assertEquals(DraftMergePreflightBlock.UnconfirmedBackup, reason(check(plan(unconfirmed = true))))
        val inserted = workspace.copy(objects = workspace.objects + (CanvasObjectId("new-node") to media.copy(id = CanvasObjectId("new-node"))))
        val blocked = assertIs<DraftMergePreflightResult.Blocked>(check(plan(inserted)))
        assertEquals(DraftMergePreflightBlock.ContractUnavailable, blocked.reason)
        assertEquals(setOf(DraftMergeContractGap.RestoreForUndoRedo), blocked.contractGaps)
    }

    @Test fun rejectsWrongWorkspaceKindStatusAndLateScope() = runTest {
        listOf<(WorkspaceAsset) -> WorkspaceAsset>(
            { it.copy(workspaceId = WorkspaceId("other")) }, { it.copy(id = "other") },
            { it.copy(mediaType = "video/mp4") }, { it.copy(status = AssetStatus.Pending) },
        ).forEach { corrupt ->
            assertEquals(DraftMergePreflightBlock.AssetUnavailable, reason(check(assets = { _, id ->
                if (id == "new") corrupt(asset(id)) else asset(id)
            })))
        }
        var active = true
        var reads = 0
        assertEquals(DraftMergePreflightBlock.ScopeExpired, reason(check(active = { active }, assets = { _, id ->
            reads++; active = false; asset(id)
        })))
        assertEquals(1, reads)
    }

    @Test fun lateRemoteChangeOrPendingSubmissionRequiresNewReview() = runTest {
        var refreshes = 0
        assertIs<DraftMergePreflightResult.ReviewAgain>(check(refresh = {
            if (++refreshes == 2) it.copy(workspaceVersion = 1, lastServerSeq = 1) else it
        }))
        var pendingReads = 0
        assertEquals(DraftMergePreflightBlock.PendingSubmission, reason(check(pending = {
            if (++pendingReads == 2) PendingWorkspaceChange("late", 1) else null
        })))
        assertEquals(DraftMergePreflightBlock.ReadFailed, reason(check(assets = { _, _ -> error("private diagnostic") })))
    }

    @Test fun unrelatedRemoteMissingMediaDoesNotRequireAssetsAndCancellationPropagates() = runTest {
        val unchanged = assertIs<DraftMergePreflightResult.Checked>(check(plan(workspace), assets = { _, _ -> error("Must not read") }))
        assertNull(unchanged.operation)
        val job = async { check(refresh = { currentCoroutineContext().cancel(); it }) }
        assertFailsWith<CancellationException> { job.await() }
    }

    @Test fun explicitReplanningPreservesDraftAndFlagsButReplacesRemoteWithoutChoices() {
        val review = plan(quarantined = true, unconfirmed = true).review
        val fresh = opened.copy(workspaceVersion = 2, lastServerSeq = 3, workspace = workspace.copy(title = "Fresh"))
        val result = refreshDraftMergeReview(review, fresh, opened)
        assertEquals(review.copy(current = fresh.workspace, currentVersion = 2, currentServerSeq = 3), result)
        assertTrue(result.quarantined && result.hasUnconfirmedSubmission)
        listOf(fresh.copy(userId = "other"), fresh.copy(clientId = "other"),
            fresh.copy(workspace = fresh.workspace.copy(id = WorkspaceId("other"))),
            opened.copy(workspaceVersion = -1), opened.copy(lastServerSeq = -1)).forEach {
            assertFails { refreshDraftMergeReview(review, it, opened) }
        }
    }

    @Test fun choicesAreFrozenBeforeRefreshCanMutateTheCallerMap() = runTest {
        val plan = plan()
        val choices = plan.fields.associate { it.id to DraftMergeChoice.UseDraft }.toMutableMap()
        val result = checkDraftMergeProposal(plan, choices, opened, { true },
            refresh = { choices.clear(); it }, pendingChange = { null }, pendingDraft = { null },
            getAsset = { _, id -> asset(id) })
        assertEquals(target, assertIs<DraftMergePreflightResult.Checked>(result).target)
    }
}
