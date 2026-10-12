package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalSyncUploadTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null
        var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    private class Server(private val delegate: CanvasWorkspaceRepository, var current: WorkspaceSession,
        override val syncServerScope: String) : CanvasWorkspaceRepository by delegate {
        override val isLocalOnly = false
        var operation: WorkspaceOperation? = null
        var prepared: PreparedWorkspaceSubmission? = null
        var prepares = 0; var sends = 0; var receiptReads = 0
        var status = PendingReceiptStatus.Unknown
        var fences = 0; var commitWinsFence = false
        var assets: Map<String, WorkspaceAsset> = emptyMap()
        override suspend fun getAsset(session: WorkspaceSession, assetId: String) = assets[assetId] ?: delegate.getAsset(session, assetId)
        private val baseline = current.workspace
        override suspend fun refresh(session: WorkspaceSession) = current
        override suspend fun pendingDraft(session: WorkspaceSession) = operation?.let { PendingWorkspaceDraft("draft", 1, false) }
        override suspend fun pendingChange(session: WorkspaceSession) = prepared?.let { PendingWorkspaceChange(it.transactionId, 1) }
        override suspend fun retainDraftAwaited(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
            check(this.operation == null); this.operation = operation
        }
        override suspend fun prepareSubmission(session: WorkspaceSession, operation: WorkspaceOperation): PreparedWorkspaceSubmission {
            check(this.operation == operation); prepares++
            return prepared ?: PreparedWorkspaceSubmission(syncServerScope, current.userId, current.clientId, current.workspace.id,
                operation.operationId, "wire", current.workspaceVersion, listOf("wire-op"), listOf(1L), "b".repeat(64)).also { prepared = it }
        }
        override suspend fun preparedSubmission(session: WorkspaceSession) = prepared
        override suspend fun verifyPreparedSubmission(session: WorkspaceSession, prepared: PreparedWorkspaceSubmission,
            operation: WorkspaceOperation, baseline: WorkspaceSession?): PreparedSubmissionVerification {
            receiptReads++
            return PreparedSubmissionVerification(prepared, status, current,
                if (status == PendingReceiptStatus.Committed) current.workspaceVersion else null,
                if (status == PendingReceiptStatus.Committed) current.lastServerSeq else null)
        }
        override suspend fun sendPreparedSubmission(session: WorkspaceSession, prepared: PreparedWorkspaceSubmission): SubmitOutcome {
            check(this.prepared == prepared && status == PendingReceiptStatus.Unknown); sends++
            val changed = assertIs<OperationResult.Applied>(operation!!.applyTo(current.workspace)).workspace
            current = current.copy(workspace = changed, workspaceVersion = changed.version, lastServerSeq = current.lastServerSeq + 1)
            status = PendingReceiptStatus.Committed; this.prepared = null
            return SubmitOutcome.Accepted(current.workspaceVersion, current.lastServerSeq)
        }
        override suspend fun confirmPendingReceipt(session: WorkspaceSession, transactionId: String): WorkspaceSession {
            check(status == PendingReceiptStatus.Committed); prepared = null; operation = null; return current
        }
        override suspend fun fencePendingChange(session: WorkspaceSession, transactionId: String): PendingSubmissionReceipt {
            check(prepared?.transactionId == transactionId); fences++
            if (commitWinsFence) {
                val changed = assertIs<OperationResult.Applied>(operation!!.applyTo(current.workspace)).workspace
                current = current.copy(workspace = changed, workspaceVersion = changed.version, lastServerSeq = current.lastServerSeq + 1)
                status = PendingReceiptStatus.Committed
            } else status = PendingReceiptStatus.Fenced
            return PendingSubmissionReceipt(status, current.lastServerSeq, current.workspaceVersion,
                if (status == PendingReceiptStatus.Committed) current.workspaceVersion else null)
        }
        override suspend fun dismissPendingChange(session: WorkspaceSession, transactionId: String): WorkspaceSession {
            check(prepared?.transactionId == transactionId && status == PendingReceiptStatus.Fenced)
            prepared = null; return current
        }
        override suspend fun reviewPendingDraft(session: WorkspaceSession, draftId: String) = WorkspaceDraftReview(
            draftId, baseline.version, 1, current.workspaceVersion, current.lastServerSeq, baseline,
            assertIs<OperationResult.Applied>(operation!!.applyTo(baseline)).workspace, current.workspace,
            listOf(operation!!), true, false)
        override suspend fun dismissPendingDraft(session: WorkspaceSession, draftId: String): WorkspaceSession {
            check(status == PendingReceiptStatus.Fenced); operation = null; return current
        }
    }
    private suspend fun setup(records: Records = Records(), includeMedia: Boolean = false, mapped: Boolean = false): Triple<AwaitedLocalWorkspaceRepository, WorkspaceSession, Server> {
        val local = AwaitedLocalWorkspaceRepository(records) { "2026-10-10T00:00:00Z" }
        val opened = local.openOrCreateWorkspace()
        val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "本機")
        val media = MediaNode(CanvasObjectId("media"), transform = node.transform, assetId = "photo", mediaKind = MediaKind.Image, thumbnailAssetId = "thumbnail")
        local.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("create", if (includeMedia && !mapped) listOf(node, media) else listOf(node)))
        val owner = local.refresh(opened)
        val binding = LocalWorkspaceSyncBinding("a".repeat(64), "server-user", WorkspaceId("server-board"))
        val remote = WorkspaceSession(binding.userId, "server-client", WorkspaceMemberRole.Owner, owner.workspaceVersion, 1,
            owner.workspace.copy(id = binding.workspaceId, objects = owner.workspace.objects + (node.id to node.copy(text = "遠端")) +
                if (includeMedia) mapOf(media.id to media) else emptyMap()))
        val bound = local.bindSyncDestination(owner, binding, remote)
        val plan = LocalWorkspaceSyncMergePlan(bound, remote)
        val proposal = plan.proposal("plan", plan.rows.associate { it.id to if (mapped && it.id == "object:media") LocalSyncMergeChoice.Remote else LocalSyncMergeChoice.Local })
        var saved = local.stageSyncProposal(owner, bound.checkpoint!!, null, proposal)
        val applied = if (mapped) {
            for ((sourceId, destination) in listOf("photo" to "00112233-4455-6677-8899-aabbccddeeff", "thumbnail" to "11223344-5566-7788-99aa-bbccddeeff00")) {
                val source = WorkspaceAsset(sourceId, binding.workspaceId, "another-creator", "image/png", 3, "sha256:" + "c".repeat(64), 1, 1, null, AssetStatus.Ready, "2026-10-11")
                saved = local.stageSyncMaterialization(saved.local, saved.checkpoint!!, newLocalAssetMaterialization(saved, source, destination, "2026-10-11"))
            }
            val target = reviewedLocalApplyTarget(saved)
            local.applyMaterializedSyncProposal(VerifiedMaterializedTarget(saved, target), LocalWorkspaceContentOperation("apply", owner.workspace, target))
        } else local.applySyncProposal(owner, proposal.id, LocalWorkspaceContentOperation("apply", owner.workspace, proposal.target))
        val server = Server(local, remote, binding.serverScope)
        if (includeMedia) server.assets = listOf("photo", "thumbnail").associateWith { id ->
            WorkspaceAsset(id, binding.workspaceId, "another-creator", "image/png", 3, "sha256:" + "c".repeat(64),
                1, 1, null, AssetStatus.Ready, "2026-10-11")
        }
        return Triple(local, applied.snapshot.local, server)
    }
    @Test fun mappedOriginalAndThumbnailSendCanonicalTransactionAndSettleWithoutLocalIdLoss() = runTest {
        // Controlled verified publication + fixture server, not a live original-byte assertion.
        val (local, owner, server) = setup(includeMedia = true, mapped = true)
        val original = local.readSyncSnapshot(owner)
        val thumbnail = server.assets.getValue("thumbnail")
        server.assets = server.assets + (thumbnail.id to thumbnail.copy(width = 2))
        assertFails { prepareLocalSyncUpload(local, owner, server, server.current, { true }) }
        assertEquals(original, local.readSyncSnapshot(owner)); assertEquals(0, server.prepares)
        server.assets = server.assets + (thumbnail.id to thumbnail)
        val ready = prepareLocalSyncUpload(local, owner, server, server.current, { true })
        val target = assertIs<OperationResult.Applied>(ready.outgoing!!.operation.applyTo(server.current.workspace)).workspace
        val wireMedia = target.objects[CanvasObjectId("media")] as MediaNode
        assertEquals("photo", wireMedia.assetId); assertEquals("thumbnail", wireMedia.thumbnailAssetId)
        val settled = sendLocalSyncUpload(local, owner, server, server.current, ready.outgoing!!, { true })
        assertEquals(1, server.sends); assertTrue(settled.proposalIsSettled); assertFalse(settled.hasPendingContent)
        assertEquals(original.local.workspace, settled.local.workspace)
        assertEquals(original.materializations, settled.materializations)
        assertEquals(original.materializations, settled.applied!!.materializations)
        assertEquals("photo", (settled.checkpoint!!.baseline.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertTrue(restoredLocalSyncHistory(local.readSyncSnapshot(settled.local)).undo().succeeded)
    }
    @Test fun mappedFenceArchivesOriginalCanonicalRequestAndLocalIntentWithoutDeletingMappings() = runTest {
        val (local, owner, server) = setup(includeMedia = true, mapped = true)
        val ready = prepareLocalSyncUpload(local, owner, server, server.current, { true })
        val stopped = fenceLocalSyncUpload(local, owner, server, server.current, ready.outgoing!!, { true })
        assertEquals(0, server.sends); assertEquals(1, server.fences)
        assertNull(stopped.outgoing); assertEquals(ready.local.workspace, stopped.local.workspace)
        assertEquals(ready.materializations, stopped.materializations)
        assertEquals(ready.applied!!.materializations, stopped.stopped.single().applied.materializations)
        assertEquals(ready.outgoing, stopped.stopped.single().outgoing)
        assertEquals(stopped, local.readSyncSnapshot(stopped.local))
    }
    @Test fun readyServerPngAndIndependentThumbnailPermitPreparationButBadThumbnailCannotPublish() = runTest {
        val (local, owner, server) = setup(includeMedia = true)
        val before = local.readSyncSnapshot(owner)
        val thumbnail = server.assets.getValue("thumbnail")
        for (bad in listOf(thumbnail.copy(status = AssetStatus.Pending), thumbnail.copy(mediaType = "video/mp4"), thumbnail.copy(workspaceId = WorkspaceId("other")), thumbnail.copy(id = "wrong"))) {
            server.assets = server.assets + (thumbnail.id to bad)
            assertFails { prepareLocalSyncUpload(local, owner, server, server.current, { true }) }
            assertEquals(before, local.readSyncSnapshot(owner)); assertEquals(0, server.prepares); assertNull(server.operation)
        }
        server.assets = server.assets + (thumbnail.id to thumbnail)
        val prepared = prepareLocalSyncUpload(local, owner, server, server.current, { true })
        assertNotNull(prepared.outgoing!!.prepared); assertEquals(1, server.prepares); assertEquals(0, server.sends)
        val target = assertIs<OperationResult.Applied>(prepared.outgoing!!.operation.applyTo(server.current.workspace)).workspace
        assertEquals("photo", (target.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertEquals("thumbnail", (target.objects[CanvasObjectId("media")] as MediaNode).thumbnailAssetId)
    }
    @Test fun rereviewRefreshesHeadAndCannotReplacePendingRequestsOrStaleAuthorization() = runTest {
        val (local, owner, server) = setup()
        val snapshot = local.readSyncSnapshot(owner)
        val plan = LocalWorkspaceSyncMergePlan(snapshot, server.current)
        val next = plan.proposal("rereview-next", plan.rows.associate { it.id to LocalSyncMergeChoice.Local })
        assertFails { stageLocalSyncReview(local, snapshot, server, next, { false }) }
        val selected = server.current
        server.current = selected.copy(lastServerSeq = selected.lastServerSeq + 1)
        assertFails { stageLocalSyncReview(local, snapshot, server, next, { true }) }
        server.current = selected.copy(role = WorkspaceMemberRole.Viewer)
        assertFails { stageLocalSyncReview(local, snapshot, server, next, { true }) }
        server.current = selected
        server.operation = CreateObjectsOperation("other-draft", listOf(owner.workspace.objects.values.first()))
        assertFails { stageLocalSyncReview(local, snapshot, server, next, { true }) }
        server.operation = null
        assertEquals(snapshot, local.readSyncSnapshot(owner))
        val staged = stageLocalSyncReview(local, snapshot, server, next, { true })
        assertEquals(snapshot.local.workspace, staged.local.workspace)
        assertEquals(snapshot.applied, staged.superseded.single().applied)
        assertEquals(0, server.sends); assertEquals(0, server.prepares); assertEquals(0, server.fences)
        local.applySyncProposal(staged.local, next.id,
            LocalWorkspaceContentOperation("rereview-apply", staged.local.workspace, next.target))
        val pending = prepareLocalSyncUpload(local, owner, server, selected, { true })
        val blockedPlan = LocalWorkspaceSyncMergePlan(pending, selected)
        val blocked = blockedPlan.proposal("pending-replacement", blockedPlan.rows.associate { it.id to LocalSyncMergeChoice.Local })
        assertFails { stageLocalSyncReview(local, pending, server, blocked, { true }) }
        assertEquals(pending.outgoing, local.readSyncSnapshot(owner).outgoing)
    }

    @Test fun explicitPreparationThenOriginalReceiptCheckAndSendSettlesWithoutNewIds() = runTest {
        val (local, owner, server) = setup(); val selected = server.current
        val ready = prepareLocalSyncUpload(local, owner, server, selected, { true })
        assertEquals(0, server.sends)
        val repeated = prepareLocalSyncUpload(local, owner, server, selected, { true })
        assertEquals(ready.outgoing, repeated.outgoing)
        val settled = sendLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true })
        assertEquals(1, server.sends); assertEquals(2, server.receiptReads)
        assertTrue(settled.proposalIsSettled); assertFalse(settled.hasPendingContent)
        assertEquals(ready.outgoing!!.prepared, settled.applied!!.committed!!.outgoing.prepared)
        val recovered = verifyAndSettleLocalSyncUpload(local, owner, server, selected, { true })
        assertTrue(recovered.proposalIsSettled); assertEquals(1, server.sends)
    }
    @Test fun fencedChangedRemoteAndClosedScopeCannotSend() = runTest {
        val (local, owner, server) = setup(); val selected = server.current
        val ready = prepareLocalSyncUpload(local, owner, server, selected, { true })
        server.status = PendingReceiptStatus.Fenced
        assertFails { sendLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true }) }
        server.status = PendingReceiptStatus.Unknown
        server.current = server.current.copy(workspace = server.current.workspace.copy(title = "Changed"))
        assertFails { sendLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true }) }
        assertFails { sendLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { false }) }
        assertEquals(0, server.sends)
    }
    @Test fun wirePreparationBeforeLocalBindingCanRecoverAfterLaterLocalEdits() = runTest {
        val (local, owner, server) = setup()
        val snapshot = local.readSyncSnapshot(owner)
        val proposal = snapshot.proposal!!
        var id = 0
        val operation = buildWorkspaceContentTransaction(proposal.remote, proposal.target.copy(id = proposal.remote.id), { "op-${++id}" })!!
        val outgoing = OutgoingLocalSyncTransaction(proposal.id, proposal.remoteVersion, proposal.remoteSeq, proposal.remoteClientId, operation)
        local.stageSyncOutgoing(owner, outgoing)
        server.retainDraftAwaited(server.current, server.current.workspace, operation)
        val identity = server.prepareSubmission(server.current, operation)
        val later = TextNode(CanvasObjectId("later"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Later")
        local.retainDraftAwaited(owner, owner.workspace, CreateObjectsOperation("later", listOf(later)))
        val restored = prepareLocalSyncUpload(local, owner, server, server.current, { true })
        assertEquals(identity, restored.outgoing!!.prepared)
        assertEquals(later, restored.local.workspace.objects[later.id])
        assertEquals(1, server.prepares); assertEquals(0, server.sends)
    }
    @Test fun confirmedFenceRetainsCanvasBaselineAndUndoThenAllowsNewReview() = runTest {
        val (local, owner, server) = setup(); val selected = server.current
        val ready = prepareLocalSyncUpload(local, owner, server, selected, { true })
        val stopped = fenceLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true })
        assertEquals(1, server.fences); assertEquals(0, server.sends)
        assertEquals(owner.workspace, stopped.local.workspace)
        assertEquals(ready.checkpoint, stopped.checkpoint)
        assertNull(stopped.outgoing); assertNull(stopped.proposal); assertNull(stopped.applied)
        assertEquals(ready.outgoing, stopped.stopped.single().outgoing)
        assertTrue(restoredLocalSyncHistory(local.readSyncSnapshot(owner)).undo().succeeded)
        val plan = LocalWorkspaceSyncMergePlan(stopped, selected)
        val proposal = plan.proposal("after-fence", plan.rows.associate { it.id to LocalSyncMergeChoice.Local })
        val staged = local.stageSyncProposal(stopped.local, stopped.checkpoint!!, null, proposal)
        assertEquals(1, staged.stopped.size)
    }
    @Test fun commitWinningFenceIsSettledNotArchivedAsCancelled() = runTest {
        val (local, owner, server) = setup(); val selected = server.current
        val ready = prepareLocalSyncUpload(local, owner, server, selected, { true })
        server.commitWinsFence = true
        val settled = fenceLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true })
        assertEquals(1, server.fences); assertEquals(0, server.sends)
        assertTrue(settled.proposalIsSettled); assertTrue(settled.stopped.isEmpty())
        assertNotNull(settled.applied!!.committed)
    }
    @Test fun unknownArchiveAckRecoversWithoutAnotherFenceAndPreservesLaterEdits() = runTest {
        val records = Records()
        val (local, owner, server) = setup(records); val selected = server.current
        val ready = prepareLocalSyncUpload(local, owner, server, selected, { true })
        val later = TextNode(CanvasObjectId("later-fence"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Later")
        local.retainDraftAwaited(owner, owner.workspace, CreateObjectsOperation("later-fence", listOf(later)))
        records.failConfirm = true
        assertFails { fenceLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true }) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val recovered = fenceLocalSyncUpload(local, owner, server, selected, ready.outgoing!!, { true })
        assertEquals(generation, records.value!!.generation)
        assertEquals(1, server.fences); assertEquals(0, server.sends)
        assertEquals(later, recovered.local.workspace.objects[later.id])
        assertEquals(1, recovered.stopped.size)
        assertEquals(ready.checkpoint, recovered.checkpoint)
    }
}
