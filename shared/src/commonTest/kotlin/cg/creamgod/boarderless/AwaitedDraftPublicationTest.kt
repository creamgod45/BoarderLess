package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class AwaitedDraftPublicationTest {
    private class Fixture {
        var record: DurableLocalRecord? = null
        var retained = 0
        var barrier: suspend () -> Unit = {}
        val engine = PersistentLocalWorkspaceRepository({ object : DurableLocalRecordStore {
            override fun read(key: String) = record
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                check(generation == record?.generation)
                record = DurableLocalRecord((generation ?: 0L) + 1, payload.copyOf())
            }
            override fun confirmDurable(key: String, generation: Long) { check(record?.generation == generation) }
        } }, object : LocalDocumentLock { override fun <T> withLock(block: () -> T) = block() }, { "2026-10-10T00:00:00Z" })
        val repository = object : WorkspaceRepository by engine {
            override suspend fun retainDraftAwaited(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
                retained++
                barrier()
                engine.retainDraft(session, before, operation)
            }
            override suspend fun retainHistoryDraftAwaited(session: WorkspaceSession, before: Workspace,
                operation: WorkspaceOperation, intent: WorkspaceHistoryIntent, beforeHistory: WorkspaceHistory?) {
                retainDraftAwaited(session, before, operation)
            }
        }
        val operation = CreateObjectsOperation("draft", listOf(TextNode(CanvasObjectId("draft-node"),
            transform = CanvasTransform(Vec2.Zero, CanvasSize(40f, 40f)), text = "中文🙂")))
    }

    @Test fun resultCannotBecomeReadyWhileTheSaveAcknowledgementIsPending() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val result = before.execute(f.operation); val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
        val waiting = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) } }
        runCurrent(); assertEquals(1, f.retained); assertFalse(waiting.isCompleted)
        assertEquals(before.workspace, f.engine.refresh(owner).workspace)
        release.complete(Unit)
        assertEquals(DraftPublicationOutcome.Ready, waiting.await())
        assertEquals(result.history.workspace, f.engine.refresh(owner).workspace)
    }

    @Test fun anAcknowledgementAfterAHistoryChangeKeepsTheReceiptButCannotPublishTheOldResult() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val result = before.execute(f.operation); var liveHistory = before
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
        val waiting = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, liveHistory, true) } }
        runCurrent(); liveHistory = result.history; release.complete(Unit)
        assertEquals(DraftPublicationOutcome.ScopeChanged, waiting.await())
        assertEquals(result.history.workspace, f.engine.refresh(owner).workspace)
        assertIs<SubmitOutcome.Accepted>(f.engine.submit(owner, f.operation))
    }

    @Test fun changedOwnerPermissionOrDisabledScopeNeverStartsRetention() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val result = before.execute(f.operation)
        for (live in listOf(DraftPublicationLive(owner.copy(clientId = "other"), before, true),
            DraftPublicationLive(owner.copy(role = WorkspaceMemberRole.Viewer), before, true),
            DraftPublicationLive(owner, before, false))) {
            assertEquals(DraftPublicationOutcome.ScopeChanged, awaitDraftPublication(f.repository, owner, before, result) { live })
        }
        assertEquals(0, f.retained)
    }

    @Test fun permissionRevokedDuringSavePreventsPublicationAfterAcknowledgement() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }; var live = owner
        val waiting = async { awaitDraftPublication(f.repository, owner, before, before.execute(f.operation)) { DraftPublicationLive(live, before, true) } }
        runCurrent(); live = owner.copy(role = WorkspaceMemberRole.Viewer); release.complete(Unit)
        assertEquals(DraftPublicationOutcome.ScopeChanged, waiting.await())
    }

    @Test fun saveFailureAndCancellationAreNotSuccessfulPublication() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        f.barrier = { error("Storage failed") }
        assertEquals(DraftPublicationOutcome.Unconfirmed,
            awaitDraftPublication(f.repository, owner, before, before.execute(f.operation)) { DraftPublicationLive(owner, before, true) })
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
        val waiting = async { awaitDraftPublication(f.repository, owner, before, before.execute(f.operation)) { DraftPublicationLive(owner, before, true) } }
        runCurrent(); waiting.cancelAndJoin(); assertTrue(waiting.isCancelled)
        assertEquals(before.workspace, f.engine.refresh(owner).workspace)
    }
    @Test fun backgroundUndoRedoWaitsForAcknowledgementBeforeChangingTheVisibleHistoryAndSurvivesReopen() = runTest {
        val f = Fixture()
        var owner = f.engine.openOrCreateWorkspace()
        var visible = WorkspaceHistory(owner.workspace)
        val created = visible.execute(f.operation)
        f.engine.retainDraft(owner, visible.workspace, f.operation)
        visible = created.history; owner = f.engine.refresh(owner)
        val background = CanvasStyle(backgroundToken = "#204060")
        val style = UpdateCanvasStyleOperation("background", visible.workspace.canvasStyleVersion, visible.workspace.canvasStyle, background)
        val changed = visible.execute(style)
        assertEquals(DraftPublicationOutcome.Ready,
            awaitDraftPublication(f.repository, owner, visible, changed) { DraftPublicationLive(owner, visible, true) })
        visible = changed.history; owner = f.engine.refresh(owner)
        val objects = visible.workspace.objects
        val beforeUndo = visible
        val undone = beforeUndo.undo(remoteRestoration = true)
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
        val waiting = async {
            val outcome = awaitDraftPublication(f.repository, owner, beforeUndo, undone) { DraftPublicationLive(owner, visible, true) }
            if (outcome == DraftPublicationOutcome.Ready) visible = undone.history
            outcome
        }
        runCurrent()
        assertFalse(waiting.isCompleted)
        assertEquals(background, visible.workspace.canvasStyle)
        assertEquals(beforeUndo, visible)
        release.complete(Unit); assertEquals(DraftPublicationOutcome.Ready, waiting.await())
        assertEquals(CanvasStyle(), visible.workspace.canvasStyle)
        assertEquals(2L, visible.workspace.canvasStyleVersion)
        assertTrue(visible.canRedo)
        assertEquals(objects, visible.workspace.objects)
        owner = f.engine.refresh(owner)
        f.barrier = {}
        val redone = visible.redo(remoteRestoration = true)
        assertEquals(DraftPublicationOutcome.Ready,
            awaitDraftPublication(f.repository, owner, visible, redone) { DraftPublicationLive(owner, visible, true) })
        visible = redone.history
        assertEquals(background, visible.workspace.canvasStyle)
        assertEquals(3L, visible.workspace.canvasStyleVersion)
        assertEquals(visible.workspace, f.engine.openOrCreateWorkspace().workspace)
        assertEquals(objects, visible.workspace.objects)
    }

    @Test fun failedUndoLeavesVisibleHistoryAndRedoStackUnchanged() = runTest {
        val f = Fixture(); var owner = f.engine.openOrCreateWorkspace()
        val created = WorkspaceHistory(owner.workspace).execute(f.operation)
        f.engine.retainDraft(owner, owner.workspace, f.operation); owner = f.engine.refresh(owner)
        var visible = created.history
        val before = visible; val result = before.undo(remoteRestoration = true)
        f.barrier = { error("Cannot confirm undo") }
        val outcome = awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, visible, true) }
        if (outcome == DraftPublicationOutcome.Ready) visible = result.history
        assertEquals(DraftPublicationOutcome.Unconfirmed, outcome)
        assertEquals(before, visible); assertTrue(visible.canUndo); assertFalse(visible.canRedo)
        assertEquals(before.workspace, f.engine.refresh(owner).workspace)
    }

    @Test fun preparedPenAndSequenceOperationsRetainTheirIdsAcrossAFailedSaveAndAwaitedRetry() = runTest {
        for (sequence in listOf(false, true)) {
            val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
            var allocated = 0
            val operation: WorkspaceOperation = if (sequence) {
                val draft = assertIs<cg.creamgod.boarderless.domain.sequence.SequenceParseResult.Parsed>(
                    cg.creamgod.boarderless.domain.sequence.MermaidSequenceAdapter.parse("sequenceDiagram\nparticipant A\nparticipant B\nA->>B: 中文🙂"))
                    .draft
                SequenceCanvasCreation(owner, before.workspace, Vec2(40f, 50f)).operation(draft, "Sequence") { "sequence-${++allocated}" }
            } else {
                val path = VectorPath(CanvasSize(400f, 300f), listOf(
                    VectorPathCommand.Move(Vec2(10f, 10f)), VectorPathCommand.Line(Vec2(100f, 100f))))
                PenCanvasCreation(owner, before.workspace, Vec2(40f, 50f)).operation(path, "pen-node", "pen-operation")
            }
            val result = before.execute(operation); assertTrue(result.succeeded)
            val ids = result.history.workspace.objects.keys
            val allocatedBeforeRetry = allocated
            f.barrier = { error("First save failed") }
            assertEquals(DraftPublicationOutcome.Unconfirmed,
                awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) })
            assertEquals(before.workspace, f.engine.refresh(owner).workspace)
            val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
            val waiting = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) } }
            runCurrent(); assertFalse(waiting.isCompleted)
            release.complete(Unit); assertEquals(DraftPublicationOutcome.Ready, waiting.await())
            val restored = f.engine.refresh(owner).workspace
            assertEquals(ids, restored.objects.keys)
            assertEquals(result.history.workspace, restored)
            assertEquals(allocatedBeforeRetry, allocated)
            assertEquals(2, f.retained)
        }
    }

    @Test fun completeBrushStrokeStaysPrivateUntilAckAndRetryUsesTheSameObjectAndStyle() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val samples = listOf(Vec2(10f, 20f), Vec2(40f, 50f), Vec2(80f, 30f))
        val draft = prepareBrushStroke(owner, before.workspace, samples, 8f, "#1976d2", .1f, "brush-object", "brush-operation")
        val result = before.execute(draft.operation)
        assertTrue(result.succeeded)
        val node = assertIs<TextNode>(result.history.workspace.objects[draft.objectId])
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await(); error("Save unavailable") }
        val first = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) } }
        runCurrent(); assertFalse(first.isCompleted)
        assertEquals(before.workspace, f.engine.refresh(owner).workspace)
        assertTrue(draft.isCurrent(PenCanvasLive(owner, before.workspace, true)))
        assertEquals(8f, assertNotNull(node.vectorPath).style.strokeWidth)
        release.complete(Unit); assertEquals(DraftPublicationOutcome.Unconfirmed, first.await())
        assertEquals(node, draft.operation.objects.single())
        f.barrier = {}
        assertEquals(DraftPublicationOutcome.Ready,
            awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) })
        val saved = f.engine.refresh(owner).workspace
        assertEquals(node, saved.objects[draft.objectId])
        assertEquals(1, saved.objects.size)
        assertEquals("#1976d2", assertNotNull(assertIs<TextNode>(saved.objects[draft.objectId]).vectorPath).style.strokeColorToken)
        assertEquals(2, f.retained)
    }

    @Test fun aUiOperationCapturedBeforeANewerEditCannotRetainAgainstTheNewHistory() = runTest {
        val f = Fixture(); var owner = f.engine.openOrCreateWorkspace()
        val created = WorkspaceHistory(owner.workspace).execute(f.operation)
        f.engine.retainDraft(owner, owner.workspace, f.operation); owner = f.engine.refresh(owner)
        val before = created.history; val node = before.workspace.objects.values.single()
        val captured = before.execute(TransformObjectsOperation("captured-move", listOf(
            TransformChange(node.id, node.version, node.transform, node.transform.copy(position = Vec2(100f, 200f))))))
        val newer = before.execute(TransformObjectsOperation("newer-move", listOf(
            TransformChange(node.id, node.version, node.transform, node.transform.copy(position = Vec2(200f, 300f)))))).history
        assertEquals(DraftPublicationOutcome.ScopeChanged,
            awaitDraftPublication(f.repository, owner, before, captured) { DraftPublicationLive(owner, newer, true) })
        assertEquals(0, f.retained)
        assertEquals(before.workspace, f.engine.refresh(owner).workspace)
    }

    @Test fun groupingCannotExposeAGroupOrNewParentsBeforeTheAcknowledgement() = runTest {
        val f = Fixture(); var owner = f.engine.openOrCreateWorkspace()
        val one = TextNode(CanvasObjectId("one"), transform = CanvasTransform(Vec2.Zero, CanvasSize(40f, 40f)), text = "One")
        val two = one.copy(id = CanvasObjectId("two"), text = "Two", transform = one.transform.copy(position = Vec2(100f, 0f)))
        val create = CreateObjectsOperation("create-pair", listOf(one, two))
        val created = WorkspaceHistory(owner.workspace).execute(create)
        f.engine.retainDraft(owner, owner.workspace, create); owner = f.engine.refresh(owner)
        val before = created.history
        val group = GroupFrame(CanvasObjectId("group"), transform = CanvasTransform(Vec2(-10f, -10f), CanvasSize(180f, 80f)), title = "Group")
        val operation = TransactionOperation("group", listOf(CreateObjectsOperation("create-group", listOf(group)),
            ReparentObjectsOperation("parent", listOf(ParentChange(one.id, one.version, null, group.id), ParentChange(two.id, two.version, null, group.id)))))
        val result = before.execute(operation); assertTrue(result.succeeded)
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await() }
        val waiting = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) } }
        runCurrent(); assertFalse(waiting.isCompleted)
        val unchanged = f.engine.refresh(owner).workspace
        assertNull(unchanged.objects[group.id]); assertTrue(unchanged.objects.values.all { it.parentId == null })
        release.complete(Unit); assertEquals(DraftPublicationOutcome.Ready, waiting.await())
        val saved = f.engine.refresh(owner).workspace
        assertEquals(group, saved.objects[group.id])
        assertEquals(group.id, saved.objects.getValue(one.id).parentId)
        assertEquals(group.id, saved.objects.getValue(two.id).parentId)
        assertEquals(result.history.workspace, saved)
    }

    @Test fun aNewTextDraftCanRetryItsOriginalContentAndIdAfterFailedPublication() = runTest {
        val f = Fixture(); val owner = f.engine.openOrCreateWorkspace(); val before = WorkspaceHistory(owner.workspace)
        val draft = TextNode(CanvasObjectId("new-text"), transform = CanvasTransform(Vec2(40f, 60f), CanvasSize(100f, 80f)), text = "")
        val committed = assertNotNull(commitNewNodeDraft(draft, "中文🙂 retained draft"))
        val operation = CreateObjectsOperation("create-${draft.id.value}", listOf(committed))
        val result = before.execute(operation)
        f.barrier = { error("Saving text failed") }
        assertEquals(DraftPublicationOutcome.Unconfirmed,
            awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) })
        assertTrue(f.engine.refresh(owner).workspace.objects.isEmpty())
        assertEquals(draft.id, committed.id)
        assertEquals("中文🙂 retained draft", committed.text)
        f.barrier = {}
        assertEquals(DraftPublicationOutcome.Ready,
            awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) })
        assertEquals(committed, f.engine.refresh(owner).workspace.objects[committed.id])
        assertEquals(2, f.retained)
    }

    @Test fun insertingIntoARelationKeepsTheOriginalGraphUntilAckAndRetriesTheSameSplitIds() = runTest {
        val f = Fixture(); var owner = f.engine.openOrCreateWorkspace()
        val one = TextNode(CanvasObjectId("source"), transform = CanvasTransform(Vec2.Zero, CanvasSize(80f, 60f)), text = "A")
        val two = one.copy(id = CanvasObjectId("target"), text = "B", transform = one.transform.copy(position = Vec2(400f, 0f)))
        var history = WorkspaceHistory(owner.workspace)
        val objects = CreateObjectsOperation("pair", listOf(one, two))
        f.engine.retainDraft(owner, history.workspace, objects); history = history.execute(objects).history; owner = f.engine.refresh(owner)
        val relation = Relation(RelationId("original"), sourceObjectId = one.id, targetObjectId = two.id,
            direction = RelationDirection.Backward, label = "condition", intent = "supports")
        val connect = CreateRelationsOperation("connect", listOf(relation))
        f.engine.retainDraft(owner, history.workspace, connect); history = history.execute(connect).history; owner = f.engine.refresh(owner)
        val before = history
        val plan = RelationInsertionDraft(before.workspace, relation.id).plan("Middle")
        val result = before.execute(plan.operation); assertTrue(result.succeeded)
        val release = CompletableDeferred<Unit>(); f.barrier = { release.await(); error("Cannot confirm split") }
        val waiting = async { awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) } }
        runCurrent(); assertFalse(waiting.isCompleted)
        assertEquals(relation, f.engine.refresh(owner).workspace.relations[relation.id])
        assertNull(f.engine.refresh(owner).workspace.objects[plan.node.id])
        release.complete(Unit); assertEquals(DraftPublicationOutcome.Unconfirmed, waiting.await())
        f.barrier = {}
        assertEquals(DraftPublicationOutcome.Ready,
            awaitDraftPublication(f.repository, owner, before, result) { DraftPublicationLive(owner, before, true) })
        val saved = f.engine.refresh(owner).workspace
        assertNull(saved.relations[relation.id])
        assertEquals(plan.node, saved.objects[plan.node.id])
        assertEquals(setOf(plan.first.id, plan.second.id), saved.relations.keys)
        assertEquals(two.id, saved.relations.getValue(plan.first.id).sourceObjectId)
        assertEquals(one.id, saved.relations.getValue(plan.second.id).targetObjectId)
        assertEquals("condition", saved.relations.getValue(plan.first.id).label)
        assertNull(saved.relations.getValue(plan.second.id).label)
        assertEquals(result.history.workspace, saved)
    }

}
