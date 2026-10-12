package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.SequenceCanvasCreation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalWorkspaceSyncMergeTest {
    private val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-user", WorkspaceId("remote-board"))
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "基準")
    private val base = Workspace(WorkspaceId("local-board"), "基準", objects = mapOf(node.id to node))
    private fun session(workspace: Workspace) = WorkspaceSession("local", "client", WorkspaceMemberRole.Owner, workspace.version, 0, workspace)
    private fun remote(workspace: Workspace) = WorkspaceSession(binding.userId, "remote-client", WorkspaceMemberRole.Owner,
        workspace.version, workspace.version, workspace.copy(id = binding.workspaceId))
    private fun plan(local: Workspace, current: Workspace, initial: Boolean = false, baseline: Workspace = base) =
        LocalWorkspaceSyncMergePlan(LocalWorkspaceSyncSnapshot(session(local), LocalWorkspaceSyncCheckpoint(binding, baseline, 0, 0, initial)), remote(current))

    @Test fun uncontestedChangesFromBothSidesSurviveWhileEveryActualConflictNeedsAChoice() {
        val local = base.copy(title = "本機標題", canvasStyle = CanvasStyle(backgroundToken = "cool"),
            objects = mapOf(node.id to node.copy(text = "本機")))
        val other = node.copy(id = CanvasObjectId("remote-added"), text = "遠端新增")
        val current = base.copy(title = "遠端標題", canvasStyle = CanvasStyle(backgroundToken = "warm"), objects = base.objects + (other.id to other))
        val plan = plan(local, current)
        assertEquals(setOf("document:title", "document:canvasStyle"), plan.rows.filter { it.requiresChoice }.map { it.id }.toSet())
        assertFails { plan.resolve(emptyMap()) }
        val target = plan.resolve(mapOf("document:title" to LocalSyncMergeChoice.Local, "document:canvasStyle" to LocalSyncMergeChoice.Remote))
        assertEquals("本機標題", target.title); assertEquals(current.canvasStyle, target.canvasStyle)
        assertEquals("本機", (target.objects[node.id] as TextNode).text)
        assertEquals(other, target.objects[other.id])
        assertFails { plan.resolve(mapOf("foreign" to LocalSyncMergeChoice.Local)) }
    }

    @Test fun initialPairingRequiresExplicitDecisionsForAllDifferentEntities() {
        val current = base.copy(objects = emptyMap())
        val plan = plan(base, current, initial = true, baseline = current)
        assertTrue(plan.rows.all { it.requiresChoice })
        assertFails { plan.resolve(emptyMap()) }
        assertEquals(base.objects, plan.resolve(plan.rows.associate { it.id to LocalSyncMergeChoice.Local }).objects)
        assertTrue(plan.resolve(plan.rows.associate { it.id to LocalSyncMergeChoice.Remote }).objects.isEmpty())
    }

    @Test fun mixedChoicesCannotLeaveDanglingEdgesMissingGroupsOrEditLockedObjects() {
        val other = node.copy(id = CanvasObjectId("other"))
        val relation = Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = other.id)
        val current = base.copy(objects = base.objects + (other.id to other), relations = mapOf(relation.id to relation))
        val local = base.copy(objects = emptyMap())
        assertFails { plan(local, current).resolve(emptyMap()) } // Local deletion with a new remote relation.
        val locked = base.copy(objects = mapOf(node.id to node.copy(locked = true)))
        val changed = locked.copy(objects = mapOf(node.id to node.copy(text = "修改", locked = true)))
        val lockPlan = plan(locked, changed)
        assertFails { lockPlan.resolve(lockPlan.rows.associate { it.id to LocalSyncMergeChoice.Remote }) }
        val group = GroupFrame(CanvasObjectId("group"), transform = node.transform)
        val child = node.copy(parentId = group.id)
        val grouped = base.copy(objects = mapOf(group.id to group, child.id to child))
        val groupPlan = plan(grouped, base, initial = true, baseline = base)
        val mixed = groupPlan.rows.associate { it.id to if (it.id == "object:${group.id.value}") LocalSyncMergeChoice.Remote else LocalSyncMergeChoice.Local }
        assertFails { groupPlan.resolve(mixed) }
    }

    @Test fun completeSequenceSemanticsArePreservedAndIncoherentMixesAreRejected() {
        fun sequence(label: String): Workspace {
            val empty = base.copy(objects = emptyMap())
            val draft = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse(
                "sequenceDiagram\nparticipant A as $label\nparticipant B as Target\nA->>B: message")).draft
            var id = 0
            val operation = SequenceCanvasCreation(session(empty), empty, Vec2.Zero).operation(draft, "sequence") { "seq-${++id}" }
            return assertIs<OperationResult.Applied>(operation.applyTo(empty)).workspace
        }
        val local = sequence("Local"); val current = sequence("Remote")
        val plan = plan(local, current, initial = true, baseline = base.copy(objects = emptyMap()))
        val allLocal = plan.rows.associate { it.id to LocalSyncMergeChoice.Local }
        assertTrue(plan.resolve(allLocal).hasValidSequenceBindings())
        assertEquals(local.sequenceDiagrams, plan.resolve(allLocal).sequenceDiagrams)
        val mixed = plan.rows.associate { it.id to if (it.id == "document:sequenceDiagrams") LocalSyncMergeChoice.Local else LocalSyncMergeChoice.Remote }
        assertFails { plan.resolve(mixed) }
    }

    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null
        var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    private fun repository(records: Records) = AwaitedLocalWorkspaceRepository(records) { "2026-10-10T00:00:00Z" }

    @Test fun outgoingTransactionSurvivesUnknownAckWithFixedIdsAndCannotBeReplaced() = runTest {
        val records = Records(); val repo = repository(records); val opened = repo.openOrCreateWorkspace()
        repo.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("local-node", listOf(node)))
        val owner = repo.refresh(opened)
        val server = remote(owner.workspace.copy(objects = mapOf(node.id to node.copy(text = "遠端"))))
        val bound = repo.bindSyncDestination(owner, binding, server)
        val plan = LocalWorkspaceSyncMergePlan(bound, server)
        val proposal = plan.proposal("upload-plan", plan.rows.associate { it.id to LocalSyncMergeChoice.Local })
        repo.stageSyncProposal(owner, bound.checkpoint!!, null, proposal)
        val applied = repo.applySyncProposal(owner, proposal.id,
            LocalWorkspaceContentOperation("local-merge", owner.workspace, proposal.target)).snapshot
        var serial = 0
        val operation = assertNotNull(buildWorkspaceContentTransaction(proposal.remote,
            proposal.target.copy(id = proposal.remote.id), { "outgoing-${++serial}" }))
        val outgoing = OutgoingLocalSyncTransaction(proposal.id, proposal.remoteVersion, proposal.remoteSeq,
            proposal.remoteClientId, operation)
        records.failConfirm = true
        assertFails { repo.stageSyncOutgoing(applied.local, outgoing) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = repository(records)
        val saved = reopened.stageSyncOutgoing(applied.local, outgoing)
        assertEquals(outgoing, saved.outgoing); assertEquals(generation, records.value!!.generation)
        assertFails { reopened.stageSyncOutgoing(applied.local, outgoing.copy(operation = operation.copy(operationId = "changed"))) }
        assertFails { reopened.settleReadOnlySyncProposal(applied.local, proposal.id, server) }
        val another = node.copy(id = CanvasObjectId("later"))
        reopened.retainDraftAwaited(applied.local, applied.local.workspace, CreateObjectsOperation("later-create", listOf(another)))
        val latest = reopened.readSyncSnapshot(applied.local)
        assertEquals(outgoing, latest.outgoing); assertEquals(another, latest.local.workspace.objects[another.id])
        assertEquals(outgoing, reopened.stageSyncOutgoing(applied.local, outgoing).outgoing)
        assertFails { reopened.stageSyncProposal(latest.local, latest.checkpoint!!, proposal.id, proposal.copy(id = "replacement")) }
        val prepared = PreparedWorkspaceSubmission(binding.serverScope, binding.userId, proposal.remoteClientId,
            binding.workspaceId, operation.operationId, "wire-transaction", proposal.remoteVersion,
            listOf("wire-operation"), listOf(1L), "b".repeat(64))
        assertFails { reopened.bindSyncOutgoingSubmission(latest.local, outgoing, prepared.copy(userId = "wrong-user")) }
        records.failConfirm = true
        assertFails { reopened.bindSyncOutgoingSubmission(latest.local, outgoing, prepared) }
        val boundGeneration = records.value!!.generation
        records.failConfirm = false
        val rebound = repository(records).bindSyncOutgoingSubmission(latest.local, outgoing, prepared)
        assertEquals(prepared, rebound.outgoing!!.prepared)
        assertEquals(boundGeneration, records.value!!.generation)
        assertEquals(another, rebound.local.workspace.objects[another.id])
        assertFails { reopened.bindSyncOutgoingSubmission(latest.local, outgoing, prepared.copy(transactionId = "replacement-wire")) }
        val committedWorkspace = assertIs<OperationResult.Applied>(operation.applyTo(proposal.remote)).workspace
        val committedRemote = server.copy(workspaceVersion = committedWorkspace.version,
            lastServerSeq = server.lastServerSeq + 1, workspace = committedWorkspace)
        val verification = PreparedSubmissionVerification(prepared, PendingReceiptStatus.Committed, committedRemote,
            committedRemote.workspaceVersion, committedRemote.lastServerSeq)
        assertFails { reopened.settleCommittedSyncSubmission(latest.local, verification.copy(status = PendingReceiptStatus.Unknown)) }
        assertFails { reopened.settleCommittedSyncSubmission(latest.local,
            verification.copy(current = committedRemote.copy(lastServerSeq = committedRemote.lastServerSeq + 1))) }
        records.failConfirm = true
        assertFails { reopened.settleCommittedSyncSubmission(latest.local, verification) }
        val settledGeneration = records.value!!.generation
        records.failConfirm = false
        val settled = repository(records).settleCommittedSyncSubmission(latest.local, verification)
        assertEquals(settledGeneration, records.value!!.generation)
        assertNull(settled.outgoing); assertTrue(settled.proposalIsSettled)
        assertEquals(prepared, settled.applied!!.committed!!.outgoing.prepared)
        assertTrue(settled.hasPendingContent)
        assertEquals(another, settled.local.workspace.objects[another.id])
    }

    @Test fun downloadedBaselineSettlementIsDurableIdempotentAndPreservesLaterEdits() = runTest {
        val records = Records(); val repo = repository(records); val opened = repo.openOrCreateWorkspace()
        val server = remote(opened.workspace.copy(title = "採用遠端"))
        val bound = repo.bindSyncDestination(opened, binding, server)
        val plan = LocalWorkspaceSyncMergePlan(bound, server)
        val proposal = plan.proposal("download-plan", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote })
        repo.stageSyncProposal(opened, bound.checkpoint!!, null, proposal)
        val applied = repo.applySyncProposal(opened, proposal.id,
            LocalWorkspaceContentOperation("download-apply", opened.workspace, proposal.target)).snapshot
        assertFails { repo.settleReadOnlySyncProposal(applied.local, proposal.id,
            server.copy(workspace = server.workspace.copy(title = "new-remote"))) }
        assertFails { repo.settleReadOnlySyncProposal(applied.local, proposal.id, server.copy(userId = "different")) }
        assertFails { repo.settleReadOnlySyncProposal(applied.local, proposal.id, server.copy(lastServerSeq = server.lastServerSeq + 1)) }
        records.failConfirm = true
        assertFails { repo.settleReadOnlySyncProposal(applied.local, proposal.id, server) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = repository(records)
        val settled = reopened.settleReadOnlySyncProposal(applied.local, proposal.id, server)
        assertEquals(generation, records.value!!.generation)
        assertTrue(settled.proposalIsSettled); assertTrue(settled.appliedIsCurrent)
        assertFalse(settled.hasPendingContent)
        assertTrue(restoredLocalSyncHistory(settled).undo().succeeded)
        reopened.retainDraftAwaited(settled.local, settled.local.workspace, CreateObjectsOperation("post-settlement", listOf(node)))
        val later = reopened.settleReadOnlySyncProposal(settled.local, proposal.id, server)
        assertTrue(later.proposalIsSettled); assertTrue(later.hasPendingContent)
        assertEquals(node, later.local.workspace.objects[node.id])
        val nextPlan = LocalWorkspaceSyncMergePlan(later, server)
        val nextProposal = nextPlan.proposal("next-review", emptyMap())
        val staged = reopened.stageSyncProposal(later.local, later.checkpoint!!, proposal.id, nextProposal)
        assertEquals(nextProposal, staged.proposal); assertNull(staged.applied)
        reopened.applySyncProposal(staged.local, nextProposal.id,
            LocalWorkspaceContentOperation("upload-needed", staged.local.workspace, nextProposal.target))
        assertFails { reopened.settleReadOnlySyncProposal(staged.local, nextProposal.id, server) }
    }

    @Test fun atomicApplySurvivesUnknownAckAndReopenWithUndoRedoWithoutAdvancingBaseline() = runTest {
        val records = Records(); val repo = repository(records); val opened = repo.openOrCreateWorkspace()
        val server = remote(opened.workspace.copy(title = "遠端標題"))
        val bound = repo.bindSyncDestination(opened, binding, server)
        val plan = LocalWorkspaceSyncMergePlan(bound, server)
        val proposal = plan.proposal("apply-proposal", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote })
        repo.stageSyncProposal(opened, bound.checkpoint!!, null, proposal)
        val operation = LocalWorkspaceContentOperation("apply-operation", opened.workspace, proposal.target)
        records.failConfirm = true
        assertFails { repo.applySyncProposal(opened, proposal.id, operation) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = repository(records)
        val applied = reopened.applySyncProposal(opened, proposal.id, operation).snapshot
        assertEquals(generation, records.value!!.generation)
        assertTrue(applied.appliedIsCurrent)
        assertEquals(bound.checkpoint, applied.checkpoint)
        assertEquals("遠端標題", applied.local.workspace.title)
        val history = restoredLocalSyncHistory(applied)
        assertTrue(history.canUndo)
        val undone = history.undo()
        assertTrue(undone.succeeded)
        assertEquals(opened.workspace.title, undone.history.workspace.title)
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        assertTrue(sameSyncContent(applied.local.workspace, redone.history.workspace))
        assertTrue(redone.history.workspace.version > applied.local.workspace.version)
        reopened.retainDraftAwaited(applied.local, applied.local.workspace,
            CreateObjectsOperation("later-edit", listOf(node)))
        val latest = reopened.readSyncSnapshot(applied.local)
        assertFalse(latest.appliedIsCurrent)
        assertFails { reopened.applySyncProposal(opened, proposal.id, operation) }
        assertEquals(node, reopened.refresh(applied.local).workspace.objects[node.id])
    }

    @Test fun confirmedProposalReopensAfterUnknownAckWithoutApplyingOrAllocatingAnotherGeneration() = runTest {
        val records = Records(); val repo = repository(records); val opened = repo.openOrCreateWorkspace()
        val server = remote(opened.workspace)
        val bound = repo.bindSyncDestination(opened, binding, server)
        val plan = LocalWorkspaceSyncMergePlan(bound, server)
        val proposal = plan.proposal("proposal-one", emptyMap())
        records.failConfirm = true
        assertFails { repo.stageSyncProposal(opened, bound.checkpoint!!, null, proposal) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = repository(records)
        val snapshot = reopened.readSyncSnapshot(opened)
        assertEquals(proposal, snapshot.proposal); assertTrue(snapshot.proposalIsCurrent)
        assertEquals(opened.workspace, snapshot.local.workspace)
        reopened.stageSyncProposal(opened, bound.checkpoint!!, null, proposal)
        assertEquals(generation, records.value!!.generation)
        assertFails { reopened.acknowledgeSyncCheckpoint(opened, bound.checkpoint!!, server) }
        assertEquals(generation, records.value!!.generation)
    }

    @Test fun staleEditsWrongTargetsAndChangedProposalIdsCannotPublishAReplacement() = runTest {
        val records = Records(); val repo = repository(records); val opened = repo.openOrCreateWorkspace()
        val server = remote(opened.workspace)
        val bound = repo.bindSyncDestination(opened, binding, server)
        val plan = LocalWorkspaceSyncMergePlan(bound, server)
        val proposal = plan.proposal("immutable-id", emptyMap())
        assertFails { repo.stageSyncProposal(opened, bound.checkpoint!!, null, proposal.copy(target = proposal.target.copy(title = "forged"))) }
        repo.stageSyncProposal(opened, bound.checkpoint!!, null, proposal)
        assertFails { repo.stageSyncProposal(opened, bound.checkpoint!!, proposal.id, proposal.copy(target = proposal.target.copy(title = "changed"))) }
        repo.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("new-offline-edit", listOf(node)))
        val latest = repo.readSyncSnapshot(opened)
        assertFalse(latest.proposalIsCurrent); assertEquals(proposal, latest.proposal)
        assertFails { repo.stageSyncProposal(opened, bound.checkpoint!!, proposal.id, proposal.copy(id = "stale-new")) }
        assertEquals(node, repo.refresh(opened).workspace.objects[node.id])
    }
}
