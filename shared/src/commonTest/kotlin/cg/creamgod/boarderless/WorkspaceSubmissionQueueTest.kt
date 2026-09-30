package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceSubmissionQueue
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceSubmissionQueueTest {
    private val initial = Workspace(WorkspaceId("workspace-1"), "Queue test")

    @Test
    fun submissionsRemainInFirstInFirstOutOrder() {
        val first = createOperation("first", "node-1")
        val afterFirst = (first.applyTo(initial) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace
        val second = createOperation("second", "node-2")
        val afterSecond = (second.applyTo(afterFirst) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace

        var queue = WorkspaceSubmissionQueue()
            .enqueue(first, afterFirst)
            .enqueue(second, afterSecond)

        assertEquals(2, queue.size)
        assertEquals("first", queue.first?.operation?.operationId)
        queue = queue.accept(queue.first!!.token)
        assertEquals("second", queue.first?.operation?.operationId)
        assertEquals(afterSecond, queue.first?.workspaceAfter)
    }

    @Test
    fun staleAcceptanceCannotRemoveANewerSubmission() {
        val operation = createOperation("first", "node-1")
        val after = (operation.applyTo(initial) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace
        val queue = WorkspaceSubmissionQueue().enqueue(operation, after)

        assertEquals(queue, queue.accept(token = 999))
    }

    @Test
    fun clearDropsEveryUnconfirmedSubmission() {
        val operation = createOperation("first", "node-1")
        val after = (operation.applyTo(initial) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace

        assertTrue(WorkspaceSubmissionQueue().enqueue(operation, after).clear().isEmpty)
    }

    @Test
    fun rapidDependentChangesKeepTheirOwnOptimisticSnapshots() {
        val create = createOperation("create", "node-1")
        val afterCreate = (create.applyTo(initial) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace
        val edit = EditTextOperation(
            operationId = "edit",
            changes = listOf(
                TextChange(
                    objectId = CanvasObjectId("node-1"),
                    expectedVersion = 1,
                    before = "create",
                    after = "edited before save completed",
                ),
            ),
        )
        val afterEdit = (edit.applyTo(afterCreate) as cg.creamgod.boarderless.domain.history.OperationResult.Applied).workspace
        var queue = WorkspaceSubmissionQueue()
            .enqueue(create, afterCreate)
            .enqueue(edit, afterEdit)

        assertEquals("create", assertIs<TextNode>(queue.first?.workspaceAfter?.objectById(CanvasObjectId("node-1"))).text)
        queue = queue.accept(queue.first!!.token)
        assertEquals(
            "edited before save completed",
            assertIs<TextNode>(queue.first?.workspaceAfter?.objectById(CanvasObjectId("node-1"))).text,
        )
    }

    private fun createOperation(operationId: String, nodeId: String) = CreateObjectsOperation(
        operationId,
        listOf(
            TextNode(
                id = CanvasObjectId(nodeId),
                transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                text = operationId,
            ),
        ),
    )
}
