package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.coroutines.resume

class BrowserLocalSyncProposalTest {
    @Test fun reviewedRebaseReopensFromIndexedDbWithoutReplacingCanvasOrOriginalRequest() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repository = browserLocalWorkspaceRepository()
            val opened = repository.openOrCreateWorkspace()
            val node = TextNode(CanvasObjectId("rebase-node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Local")
            repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("rebase-create", listOf(node)))
            val owner = repository.refresh(opened)
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-user", WorkspaceId("remote-board"))
            val remote = WorkspaceSession(binding.userId, "remote-client", WorkspaceMemberRole.Owner, owner.workspaceVersion, 1,
                owner.workspace.copy(id = binding.workspaceId, objects = mapOf(node.id to node.copy(text = "Remote old"))))
            val store = repository.localSyncStore!!
            val bound = store.bindSyncDestination(owner, binding, remote)
            val originalPlan = LocalWorkspaceSyncMergePlan(bound, remote)
            val original = originalPlan.proposal("rebase-original", originalPlan.rows.associate { it.id to LocalSyncMergeChoice.Local })
            store.stageSyncProposal(owner, bound.checkpoint!!, null, original)
            val applied = store.applySyncProposal(owner, original.id, LocalWorkspaceContentOperation("rebase-apply", owner.workspace, original.target)).snapshot
            var id = 0
            val operation = buildWorkspaceContentTransaction(remote.workspace, original.target.copy(id = remote.workspace.id), { "rebase-out-${++id}" })!!
            val outgoing = OutgoingLocalSyncTransaction(original.id, remote.workspaceVersion, remote.lastServerSeq, remote.clientId, operation)
            store.stageSyncOutgoing(applied.local, outgoing)
            val identity = PreparedWorkspaceSubmission(binding.serverScope, binding.userId, remote.clientId, binding.workspaceId,
                operation.operationId, "rebase-wire", remote.workspaceVersion, listOf("rebase-wire-op"), listOf(1L), "b".repeat(64))
            val snapshot = store.bindSyncOutgoingSubmission(applied.local, outgoing, identity)
            val committed = assertIs<OperationResult.Applied>(operation.applyTo(remote.workspace)).workspace
            val latest = committed.copy(version = committed.version + 1,
                objects = mapOf(node.id to (committed.objects[node.id] as TextNode).copy(text = "Remote later", version = 3)))
            val verified = PreparedSubmissionVerification(identity, PendingReceiptStatus.Committed,
                remote.copy(workspace = latest, workspaceVersion = latest.version, lastServerSeq = 3), committed.version, 2, committed)
            val context = localSyncRebaseContext(snapshot, verified)
            val reviewed = LocalSyncRebasePlan(snapshot.local, context).proposal("rebase-reviewed",
                syncCheckpointDigest(snapshot.checkpoint!!), emptyMap(), emptyMap())
            store.stageSyncRebaseProposal(snapshot.local, null, reviewed, verified)
            repository.close(); browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            try {
                val current = reopened.openOrCreateWorkspace()
                val saved = reopened.localSyncStore!!.readSyncSnapshot(current)
                assertEquals(reviewed, saved.rebaseProposal); assertTrue(saved.rebaseIsCurrent)
                assertEquals(snapshot.local.workspace, saved.local.workspace)
                assertEquals(snapshot.outgoing, saved.outgoing); assertEquals(snapshot.checkpoint, saved.checkpoint)
                assertEquals("Remote later", (reviewed.target.objects[node.id] as TextNode).text)
                val appliedRebase = reopened.localSyncStore!!.applySyncRebaseProposal(current, reviewed.id,
                    LocalWorkspaceContentOperation("browser-rebase-apply", current.workspace, reviewed.target), verified)
                reopened.close(); browserRecordFixtureReopen()
                val afterRestart = browserLocalWorkspaceRepository()
                try {
                    val restored = afterRestart.localSyncStore!!.readSyncSnapshot(afterRestart.openOrCreateWorkspace())
                    assertEquals(appliedRebase.snapshot.local.workspace, restored.local.workspace)
                    assertNull(restored.outgoing); assertNull(restored.rebaseProposal)
                    assertEquals(reviewed, restored.rebases.single().reviewed)
                    assertEquals(snapshot.outgoing, restored.rebases.single().reviewed.context.outgoing)
                    assertEquals(committed.copy(id = current.workspace.id), restored.checkpoint!!.baseline)
                    assertEquals(committed.version, restored.checkpoint!!.serverVersion)
                    assertTrue(restored.appliedIsCurrent)
                    val undo = restoredLocalSyncHistory(restored).undo()
                    assertTrue(undo.succeeded)
                    assertTrue(sameSyncContent(reviewed.localBefore, undo.history.workspace))
                    val redo = undo.history.redo()
                    assertTrue(redo.succeeded)
                    assertTrue(sameSyncContent(reviewed.target, redo.history.workspace))
                    val peer = node.copy(id = CanvasObjectId("peer-after-rebase"))
                    val newHead = verified.current.copy(workspace = latest.copy(version = latest.version + 1,
                        objects = latest.objects + (peer.id to peer)), workspaceVersion = latest.version + 1, lastServerSeq = 4)
                    val nextPlan = LocalWorkspaceSyncMergePlan(restored, newHead)
                    val nextReview = nextPlan.proposal("browser-next-review", emptyMap())
                    afterRestart.localSyncStore!!.stageSyncProposal(restored.local, restored.checkpoint!!, restored.proposal!!.id, nextReview)
                    afterRestart.close(); browserRecordFixtureReopen()
                    val reviewedRepository = browserLocalWorkspaceRepository()
                    try {
                        val again = reviewedRepository.localSyncStore!!.readSyncSnapshot(reviewedRepository.openOrCreateWorkspace())
                        assertEquals(restored.local.workspace, again.local.workspace)
                        assertEquals(restored.checkpoint, again.checkpoint)
                        assertEquals(restored.rebases, again.rebases)
                        assertEquals(restored.applied, again.superseded.single().applied)
                        assertEquals(peer, again.proposal!!.target.objects[peer.id])
                        assertTrue(restoredLocalSyncHistory(again).undo().succeeded)
                        browserBackupFixtureInstall()
                        try {
                            assertEquals(DraftBackupResult.DownloadRequested,
                                exportLocalSyncEvidence(browserDraftBackupRuntime(), reviewedRepository, again, { true }))
                            val downloaded = kotlinx.coroutines.suspendCancellableCoroutine<String> { pending ->
                                browserBackupFixtureText { if (pending.isActive) pending.resume(it) }
                            }
                            val exported = LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), downloaded)
                            assertEquals(again.rebases, exported.rebases)
                            assertEquals(again.superseded, exported.superseded)
                            assertEquals(again.local.workspace, exported.workspace)
                            assertEquals("1", browserBackupFixtureValue("clicks"))
                            assertEquals(again, reviewedRepository.localSyncStore!!.readSyncSnapshot(again.local))
                            browserBackupFixtureExpire()
                        } finally { browserBackupFixtureRestore() }
                    } finally { reviewedRepository.close() }
                } finally { afterRestart.close() }
            } finally { reopened.close() }
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun outgoingCanvasTransactionReopensWithExactIdsFromIndexedDb() = runTest {
        verifyTerminalOutgoing(fenced = false)
        verifyTerminalOutgoing(fenced = true)
    }

    private suspend fun verifyTerminalOutgoing(fenced: Boolean) {
        browserRecordFixtureInstall("allow")
        try {
            val repository = browserLocalWorkspaceRepository()
            val opened = repository.openOrCreateWorkspace()
            val node = TextNode(CanvasObjectId("outgoing-node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "本機")
            repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("create-outgoing", listOf(node)))
            val owner = repository.refresh(opened)
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "server-owner", WorkspaceId("server-board"))
            val remote = WorkspaceSession(binding.userId, "server-client", WorkspaceMemberRole.Owner, owner.workspaceVersion, 1,
                owner.workspace.copy(id = binding.workspaceId, objects = mapOf(node.id to node.copy(text = "遠端"))))
            val store = repository.localSyncStore!!
            val bound = store.bindSyncDestination(owner, binding, remote)
            val plan = LocalWorkspaceSyncMergePlan(bound, remote)
            val proposal = plan.proposal("outgoing-plan", plan.rows.associate { it.id to LocalSyncMergeChoice.Local })
            store.stageSyncProposal(owner, bound.checkpoint!!, null, proposal)
            val applied = store.applySyncProposal(owner, proposal.id,
                LocalWorkspaceContentOperation("outgoing-local-apply", owner.workspace, proposal.target)).snapshot
            var serial = 0
            val operation = assertNotNull(buildWorkspaceContentTransaction(proposal.remote,
                proposal.target.copy(id = proposal.remote.id), { "pending-${++serial}" }))
            val outgoing = OutgoingLocalSyncTransaction(proposal.id, proposal.remoteVersion, proposal.remoteSeq, proposal.remoteClientId, operation)
            store.stageSyncOutgoing(applied.local, outgoing)
            val prepared = PreparedWorkspaceSubmission(binding.serverScope, binding.userId, proposal.remoteClientId,
                binding.workspaceId, operation.operationId, "browser-wire-transaction", proposal.remoteVersion,
                listOf("browser-wire-operation"), listOf(1L), "b".repeat(64))
            val boundOutgoing = store.bindSyncOutgoingSubmission(applied.local, outgoing, prepared).outgoing!!
            repository.close(); browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            try {
                val current = reopened.openOrCreateWorkspace()
                assertEquals(boundOutgoing, reopened.localSyncStore!!.readSyncSnapshot(current).outgoing)
                assertEquals(boundOutgoing, reopened.localSyncStore!!.bindSyncOutgoingSubmission(current, outgoing, prepared).outgoing)
                val committedWorkspace = assertIs<OperationResult.Applied>(operation.applyTo(proposal.remote)).workspace
                val committedRemote = remote.copy(workspace = committedWorkspace, workspaceVersion = committedWorkspace.version,
                    lastServerSeq = remote.lastServerSeq + 1)
                if (fenced) reopened.localSyncStore!!.archiveFencedSyncSubmission(current, PreparedSubmissionVerification(prepared,
                    PendingReceiptStatus.Fenced, remote, null, null))
                else reopened.localSyncStore!!.settleCommittedSyncSubmission(current, PreparedSubmissionVerification(prepared,
                    PendingReceiptStatus.Committed, committedRemote, committedRemote.workspaceVersion, committedRemote.lastServerSeq))
                reopened.close(); browserRecordFixtureReopen()
                val settledRepository = browserLocalWorkspaceRepository()
                try {
                    val settledSession = settledRepository.openOrCreateWorkspace()
                    val settled = settledRepository.localSyncStore!!.readSyncSnapshot(settledSession)
                    assertNull(settled.outgoing)
                    if (fenced) {
                        assertNull(settled.proposal); assertFalse(settled.proposalIsSettled)
                        assertEquals(bound.checkpoint, settled.checkpoint)
                        assertEquals(prepared, settled.stopped.single().outgoing.prepared)
                        assertEquals(applied.local.workspace, settled.local.workspace)
                    } else {
                        assertTrue(settled.proposalIsSettled); assertFalse(settled.hasPendingContent)
                        assertEquals(prepared, settled.applied!!.committed!!.outgoing.prepared)
                    }
                    assertTrue(restoredLocalSyncHistory(settled).undo().succeeded)
                } finally { settledRepository.close() }
            } finally { reopened.close() }
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun appliedPlanReopensWithUndoFromActualIndexedDb() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repository = browserLocalWorkspaceRepository()
            val opened = repository.openOrCreateWorkspace()
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "server-owner", WorkspaceId("server-board"))
            val remote = WorkspaceSession(binding.userId, "server-client", WorkspaceMemberRole.Owner, 0, 0,
                opened.workspace.copy(id = binding.workspaceId, title = "遠端標題🙂"))
            val store = repository.localSyncStore!!
            val paired = store.bindSyncDestination(opened, binding, remote)
            val plan = LocalWorkspaceSyncMergePlan(paired, remote)
            val proposal = plan.proposal("browser-apply", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote })
            store.stageSyncProposal(opened, paired.checkpoint!!, null, proposal)
            val applied = store.applySyncProposal(opened, proposal.id,
                LocalWorkspaceContentOperation("browser-apply-operation", opened.workspace, proposal.target))
            store.settleReadOnlySyncProposal(applied.snapshot.local, proposal.id, remote)
            repository.close(); browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            try {
                val current = reopened.openOrCreateWorkspace()
                val saved = reopened.localSyncStore!!.readSyncSnapshot(current)
                assertTrue(saved.appliedIsCurrent)
                assertTrue(saved.proposalIsSettled)
                assertFalse(saved.hasPendingContent)
                assertFalse(saved.checkpoint!!.requiresInitialReview)
                assertEquals("遠端標題🙂", current.workspace.title)
                val undone = restoredLocalSyncHistory(saved).undo()
                assertTrue(undone.succeeded)
                assertEquals(opened.workspace.title, undone.history.workspace.title)
                assertTrue(undone.history.redo().succeeded)
            } finally { reopened.close() }
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun confirmedPlanAndSelectionsReopenFromActualIndexedDbAndLaterEditsRemainSeparate() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repository = browserLocalWorkspaceRepository()
            val opened = repository.openOrCreateWorkspace()
            val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "離線🙂")
            repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("local", listOf(node)))
            val owner = repository.refresh(opened)
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "server-owner", WorkspaceId("server-board"))
            val remote = WorkspaceSession(binding.userId, "server-client", WorkspaceMemberRole.Owner, 1, 1,
                owner.workspace.copy(id = binding.workspaceId, objects = mapOf(node.id to node.copy(text = "遠端🙂"))))
            val store = assertNotNull(repository.localSyncStore)
            val paired = store.bindSyncDestination(owner, binding, remote)
            val plan = LocalWorkspaceSyncMergePlan(paired, remote)
            val proposal = plan.proposal("durable-proposal", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote })
            store.stageSyncProposal(owner, paired.checkpoint!!, null, proposal)
            assertEquals("離線🙂", (repository.refresh(owner).workspace.objects[node.id] as TextNode).text)
            repository.close(); browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            val current = reopened.openOrCreateWorkspace()
            val saved = assertNotNull(reopened.localSyncStore).readSyncSnapshot(current)
            assertEquals(proposal, saved.proposal); assertTrue(saved.proposalIsCurrent)
            assertEquals("遠端🙂", (saved.proposal!!.target.objects[node.id] as TextNode).text)
            reopened.retainDraftAwaited(current, current.workspace,
                EditTextOperation("continue-offline", listOf(TextChange(node.id, node.version, node.text, "继续編輯🙂"))))
            browserRecordFixtureReopen()
            val later = browserLocalWorkspaceRepository()
            val latest = assertNotNull(later.localSyncStore).readSyncSnapshot(later.openOrCreateWorkspace())
            assertFalse(latest.proposalIsCurrent); assertEquals(proposal, latest.proposal)
            assertEquals("继续編輯🙂", (latest.local.workspace.objects[node.id] as TextNode).text)
            reopened.close(); later.close()
        } finally { browserRecordFixtureRestore() }
    }
}
