package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.CreateRelationsOperation
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.ParentChange
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.history.TransformChange
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.HistoryDirection
import cg.creamgod.boarderless.feature.canvas.describeHistoryOperation
import cg.creamgod.boarderless.feature.canvas.describeServerOperationType
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryDescriptionTest {
    private val transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f))

    @Test
    fun inverseCreateIsDescribedAsTheOriginalCreationWhenUndoing() {
        val created = CreateObjectsOperation(
            "create",
            listOf(TextNode(id = CanvasObjectId("node"), transform = transform, text = "Idea")),
        )

        assertEquals(
            "Created 1 object",
            describeHistoryOperation(created.inverse(), HistoryDirection.Undo),
        )
    }

    @Test
    fun redoUsesTheOperationDirectionDirectly() {
        val source = CanvasObjectId("source")
        val relation = Relation(
            id = RelationId("relation"),
            sourceObjectId = source,
            targetObjectId = CanvasObjectId("target"),
        )
        val operation = CreateRelationsOperation("connect", listOf(relation))

        assertEquals("Created 1 connection", describeHistoryOperation(operation, HistoryDirection.Redo))
    }

    @Test
    fun transactionsExposeTheirAtomicChangeCount() {
        val first = CreateObjectsOperation(
            "first",
            listOf(TextNode(id = CanvasObjectId("first"), transform = transform, text = "First")),
        )
        val second = CreateObjectsOperation(
            "second",
            listOf(TextNode(id = CanvasObjectId("second"), transform = transform, text = "Second")),
        )
        val transaction = TransactionOperation("pair", listOf(first, second))

        assertEquals("Changed 2 items", describeHistoryOperation(transaction, HistoryDirection.Redo))
    }

    @Test
    fun moveAndReparentTransactionHasASpecificHistoryDescription() {
        val nodeId = CanvasObjectId("node")
        val moved = transform.copy(position = Vec2(200f, 100f))
        val transaction = TransactionOperation(
            "move-reparent-node-1",
            listOf(
                TransformObjectsOperation(
                    "move",
                    listOf(TransformChange(nodeId, 1, transform, moved)),
                ),
                ReparentObjectsOperation(
                    "reparent",
                    listOf(ParentChange(nodeId, 2, null, CanvasObjectId("group"))),
                ),
            ),
        )

        assertEquals(
            "Moved object and changed group membership",
            describeHistoryOperation(transaction, HistoryDirection.Redo),
        )
        assertEquals(
            "Moved object and changed group membership",
            describeHistoryOperation(transaction.inverse(), HistoryDirection.Undo),
        )
    }

    @Test
    fun serverOperationKindsHaveReadableLabelsAndSafeFallback() {
        assertEquals("Created connection", describeServerOperationType("create_relation"))
        assertEquals("Custom action", describeServerOperationType("custom_action"))
    }
}
