package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.DeleteObjectsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.OperationError
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.TransformChange
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.history.TextNodeAttributes
import cg.creamgod.boarderless.domain.history.TextNodeAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateTextNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.RelationAttributes
import cg.creamgod.boarderless.domain.history.RelationAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateRelationAttributesOperation
import cg.creamgod.boarderless.domain.history.GroupFrameAttributes
import cg.creamgod.boarderless.domain.history.GroupFrameAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateGroupFrameAttributesOperation
import cg.creamgod.boarderless.domain.history.CreateRelationsOperation
import cg.creamgod.boarderless.domain.history.ParentChange
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.NodeShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceHistoryTest {
    private val nodeId = CanvasObjectId("node-1")
    private val initialTransform = CanvasTransform(
        position = Vec2(120f, 80f),
        size = CanvasSize(240f, 120f),
    )

    private fun emptyHistory(): WorkspaceHistory = WorkspaceHistory(
        Workspace(
            id = WorkspaceId("workspace-1"),
            title = "Test workspace",
        ),
    )

    @Test
    fun createMoveEditCanBeUndoneAndRedoneInOrder() {
        val node = TextNode(
            id = nodeId,
            transform = initialTransform,
            text = "First thought",
        )
        val afterMove = initialTransform.copy(position = Vec2(340f, 210f))

        var history = emptyHistory()
        history = history.execute(CreateObjectsOperation("create", listOf(node))).history
        history = history.execute(
            TransformObjectsOperation(
                operationId = "move",
                changes = listOf(
                    TransformChange(
                        objectId = nodeId,
                        expectedVersion = 1,
                        before = initialTransform,
                        after = afterMove,
                    ),
                ),
            ),
        ).history
        history = history.execute(
            EditTextOperation(
                operationId = "edit",
                changes = listOf(
                    TextChange(
                        objectId = nodeId,
                        expectedVersion = 2,
                        before = "First thought",
                        after = "Develop the first thought",
                    ),
                ),
            ),
        ).history

        assertEquals("Develop the first thought", (history.workspace.objectById(nodeId) as TextNode).text)
        assertEquals(afterMove, history.workspace.objectById(nodeId)?.transform)

        history = history.undo().history
        assertEquals("First thought", (history.workspace.objectById(nodeId) as TextNode).text)

        history = history.undo().history
        assertEquals(initialTransform, history.workspace.objectById(nodeId)?.transform)

        history = history.undo().history
        assertFalse(history.workspace.objects.containsKey(nodeId))
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        history = history.redo().history
        history = history.redo().history
        history = history.redo().history

        val restored = assertIs<TextNode>(history.workspace.objectById(nodeId))
        assertEquals("Develop the first thought", restored.text)
        assertEquals(afterMove, restored.transform)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun rejectedTransactionDoesNotPartiallyChangeWorkspace() {
        val node = TextNode(
            id = nodeId,
            transform = initialTransform,
            text = "Keep me",
        )
        val history = emptyHistory()
            .execute(CreateObjectsOperation("create", listOf(node)))
            .history

        val transaction = TransactionOperation(
            operationId = "invalid-transaction",
            operations = listOf(
                DeleteObjectsOperation("delete", listOf(node)),
                DeleteObjectsOperation("delete-again", listOf(node)),
            ),
        )
        val result = history.execute(transaction)

        assertFalse(result.succeeded)
        assertIs<OperationError.ChildOperationRejected>(result.error)
        assertEquals(node, result.history.workspace.objectById(nodeId))
    }

    @Test
    fun nodeAttributesCanBeUndoneAndRedone() {
        val node = TextNode(
            id = nodeId,
            transform = initialTransform,
            text = "Style me",
        )
        var history = emptyHistory().execute(CreateObjectsOperation("create", listOf(node))).history
        val before = TextNodeAttributes(node.zIndex, node.locked, node.colorToken)
        val after = before.copy(zIndex = 10, locked = true, colorToken = "lilac", shape = NodeShape.Diamond)

        history = history.execute(
            UpdateTextNodeAttributesOperation(
                operationId = "style",
                changes = listOf(TextNodeAttributesChange(nodeId, 1, before, after)),
            ),
        ).history

        val styled = assertIs<TextNode>(history.workspace.objectById(nodeId))
        assertEquals(10, styled.zIndex)
        assertTrue(styled.locked)
        assertEquals("lilac", styled.colorToken)
        assertEquals(NodeShape.Diamond, styled.shape)

        history = history.undo().history
        val restored = assertIs<TextNode>(history.workspace.objectById(nodeId))
        assertEquals(before.zIndex, restored.zIndex)
        assertFalse(restored.locked)
        assertEquals("paper", restored.colorToken)
        assertEquals(NodeShape.RoundedRectangle, restored.shape)

        history = history.redo().history
        val redone = assertIs<TextNode>(history.workspace.objectById(nodeId))
        assertTrue(redone.locked)
        assertEquals(NodeShape.Diamond, redone.shape)
    }

    @Test
    fun deletingNodeAndRelationCanBeUndoneTogether() {
        val targetId = CanvasObjectId("node-2")
        val source = TextNode(nodeId, transform = initialTransform, text = "Source")
        val target = TextNode(
            targetId,
            transform = initialTransform.copy(position = Vec2(480f, 80f)),
            text = "Target",
        )
        val relation = Relation(
            id = RelationId("relation-1"),
            sourceObjectId = nodeId,
            targetObjectId = targetId,
            intent = "supports",
        )
        var history = emptyHistory()
            .execute(CreateObjectsOperation("create-nodes", listOf(source, target)))
            .history
            .execute(CreateRelationsOperation("connect", listOf(relation)))
            .history

        history = history.execute(
            DeleteObjectsOperation(
                operationId = "delete-source",
                objects = listOf(source),
                relations = listOf(relation),
            ),
        ).history
        assertFalse(history.workspace.objects.containsKey(nodeId))
        assertFalse(history.workspace.relations.containsKey(relation.id))

        history = history.undo().history
        assertEquals(source, history.workspace.objectById(nodeId))
        assertEquals(relation, history.workspace.relationById(relation.id))
    }

    @Test
    fun deletingObjectRejectsIncompleteOrUnrelatedRelationCascade() {
        val targetId = CanvasObjectId("node-2")
        val thirdId = CanvasObjectId("node-3")
        val source = TextNode(nodeId, transform = initialTransform, text = "Source")
        val target = TextNode(targetId, transform = initialTransform, text = "Target")
        val third = TextNode(thirdId, transform = initialTransform, text = "Third")
        val touchingRelation = Relation(
            id = RelationId("relation-touching"),
            sourceObjectId = nodeId,
            targetObjectId = targetId,
        )
        val unrelatedRelation = Relation(
            id = RelationId("relation-unrelated"),
            sourceObjectId = targetId,
            targetObjectId = thirdId,
        )
        val history = emptyHistory()
            .execute(CreateObjectsOperation("create-nodes", listOf(source, target, third)))
            .history
            .execute(CreateRelationsOperation("connect", listOf(touchingRelation, unrelatedRelation)))
            .history

        val missingCascade = history.execute(
            DeleteObjectsOperation("missing-cascade", listOf(source)),
        )
        val missingError = assertIs<OperationError.RelationCascadeMismatch>(missingCascade.error)
        assertEquals(setOf(touchingRelation.id), missingError.missingRelationIds)
        assertTrue(missingError.unexpectedRelationIds.isEmpty())
        assertEquals(history.workspace, missingCascade.history.workspace)

        val extraCascade = history.execute(
            DeleteObjectsOperation(
                operationId = "extra-cascade",
                objects = listOf(source),
                relations = listOf(touchingRelation, unrelatedRelation),
            ),
        )
        val extraError = assertIs<OperationError.RelationCascadeMismatch>(extraCascade.error)
        assertTrue(extraError.missingRelationIds.isEmpty())
        assertEquals(setOf(unrelatedRelation.id), extraError.unexpectedRelationIds)
        assertEquals(history.workspace, extraCascade.history.workspace)
    }

    @Test
    fun deletingGroupRejectsMissingDescendants() {
        val groupId = CanvasObjectId("group-1")
        val group = GroupFrame(
            id = groupId,
            transform = initialTransform,
            title = "Parent",
        )
        val child = TextNode(
            id = nodeId,
            transform = initialTransform,
            parentId = groupId,
            text = "Child",
        )
        val history = emptyHistory()
            .execute(CreateObjectsOperation("create-group", listOf(group, child)))
            .history

        val incompleteDelete = history.execute(
            DeleteObjectsOperation("delete-group-only", listOf(group)),
        )
        val error = assertIs<OperationError.HierarchyCascadeMismatch>(incompleteDelete.error)
        assertEquals(setOf(child.id), error.missingObjectIds)
        assertEquals(history.workspace, incompleteDelete.history.workspace)

        val completeDelete = history.execute(
            DeleteObjectsOperation("delete-tree", listOf(group, child)),
        )
        assertTrue(completeDelete.succeeded)
        assertTrue(completeDelete.history.workspace.objects.isEmpty())
    }

    @Test
    fun deletingRelationsRejectsDuplicateSnapshotsBeforeHistoryCanCrash() {
        val targetId = CanvasObjectId("node-2")
        val relation = Relation(
            id = RelationId("relation-1"),
            sourceObjectId = nodeId,
            targetObjectId = targetId,
        )

        assertFailsWith<IllegalArgumentException> {
            cg.creamgod.boarderless.domain.history.DeleteRelationsOperation(
                operationId = "delete-duplicates",
                relations = listOf(relation, relation),
            )
        }
    }

    @Test
    fun relationAttributesCanBeUndoneAndRedone() {
        val targetId = CanvasObjectId("node-2")
        val source = TextNode(nodeId, transform = initialTransform, text = "Source")
        val target = TextNode(targetId, transform = initialTransform, text = "Target")
        val relation = Relation(
            id = RelationId("relation-1"),
            sourceObjectId = nodeId,
            targetObjectId = targetId,
            intent = "relates",
        )
        var history = emptyHistory()
            .execute(CreateObjectsOperation("create-nodes", listOf(source, target)))
            .history
            .execute(CreateRelationsOperation("connect", listOf(relation)))
            .history
        val before = RelationAttributes(
            direction = relation.direction,
            intent = relation.intent,
            label = relation.label,
            colorToken = relation.colorToken,
        )
        val after = before.copy(
            direction = RelationDirection.Both,
            intent = "supports",
            label = "because",
        )

        history = history.execute(
            UpdateRelationAttributesOperation(
                operationId = "update-relation",
                changes = listOf(RelationAttributesChange(relation.id, 1, before, after)),
            ),
        ).history
        assertEquals(RelationDirection.Both, history.workspace.relationById(relation.id)?.direction)
        assertEquals("supports", history.workspace.relationById(relation.id)?.intent)
        assertEquals("because", history.workspace.relationById(relation.id)?.label)

        history = history.undo().history
        val restored = history.workspace.relationById(relation.id)
        assertEquals(relation.direction, restored?.direction)
        assertEquals(relation.intent, restored?.intent)
        assertEquals(relation.label, restored?.label)

        history = history.redo().history
        assertEquals("because", history.workspace.relationById(relation.id)?.label)
    }

    @Test
    fun groupTransactionCreatesFrameAndParentsNodesAtomically() {
        val targetId = CanvasObjectId("node-2")
        val groupId = CanvasObjectId("group-1")
        val source = TextNode(nodeId, transform = initialTransform, text = "Source")
        val target = TextNode(targetId, transform = initialTransform, text = "Target")
        val group = GroupFrame(
            id = groupId,
            transform = CanvasTransform(Vec2(80f, 40f), CanvasSize(600f, 240f)),
        )
        var history = emptyHistory()
            .execute(CreateObjectsOperation("nodes", listOf(source, target)))
            .history

        history = history.execute(
            TransactionOperation(
                operationId = "group",
                operations = listOf(
                    CreateObjectsOperation("frame", listOf(group)),
                    ReparentObjectsOperation(
                        operationId = "parents",
                        changes = listOf(
                            ParentChange(nodeId, 1, null, groupId),
                            ParentChange(targetId, 1, null, groupId),
                        ),
                    ),
                ),
            ),
        ).history

        assertEquals(2L, history.workspace.version)
        assertEquals(groupId, history.workspace.objectById(nodeId)?.parentId)
        assertEquals(groupId, history.workspace.objectById(targetId)?.parentId)
        assertEquals(group, history.workspace.objectById(groupId))

        history = history.undo().history
        assertEquals(3L, history.workspace.version)
        assertFalse(history.workspace.objects.containsKey(groupId))
        assertEquals(null, history.workspace.objectById(nodeId)?.parentId)
        assertEquals(null, history.workspace.objectById(targetId)?.parentId)
    }

    @Test
    fun moveAndReparentTransactionUndoesAndRedoesAsOneHistoryStep() {
        val groupId = CanvasObjectId("lane")
        val node = TextNode(nodeId, transform = initialTransform, text = "Task")
        val group = GroupFrame(
            id = groupId,
            transform = CanvasTransform(Vec2(300f, 200f), CanvasSize(600f, 300f)),
            title = "Lane",
        )
        val afterMove = initialTransform.copy(position = Vec2(420f, 260f))
        var history = emptyHistory()
            .execute(CreateObjectsOperation("seed", listOf(group, node)))
            .history

        history = history.execute(
            TransactionOperation(
                operationId = "move-reparent",
                operations = listOf(
                    TransformObjectsOperation(
                        operationId = "move",
                        changes = listOf(TransformChange(nodeId, 1, initialTransform, afterMove)),
                    ),
                    ReparentObjectsOperation(
                        operationId = "reparent",
                        changes = listOf(ParentChange(nodeId, 2, null, groupId)),
                    ),
                ),
            ),
        ).history

        assertEquals(afterMove, history.workspace.objectById(nodeId)?.transform)
        assertEquals(groupId, history.workspace.objectById(nodeId)?.parentId)

        history = history.undo().history
        assertEquals(initialTransform, history.workspace.objectById(nodeId)?.transform)
        assertEquals(null, history.workspace.objectById(nodeId)?.parentId)

        history = history.redo().history
        assertEquals(afterMove, history.workspace.objectById(nodeId)?.transform)
        assertEquals(groupId, history.workspace.objectById(nodeId)?.parentId)
    }

    @Test
    fun transactionAdvancesWorkspaceVersionOnceWhileChildObjectVersionsStillCompose() {
        val workspace = emptyHistory().workspace
        val node = TextNode(nodeId, transform = initialTransform, text = "Draft")
        val transaction = TransactionOperation(
            operationId = "create-and-edit",
            operations = listOf(
                CreateObjectsOperation("create", listOf(node)),
                EditTextOperation(
                    operationId = "edit",
                    changes = listOf(TextChange(nodeId, 1, "Draft", "Ready")),
                ),
            ),
        )

        val applied = assertIs<cg.creamgod.boarderless.domain.history.OperationResult.Applied>(
            transaction.applyTo(workspace),
        ).workspace

        assertEquals(1L, applied.version)
        assertEquals(2L, assertIs<TextNode>(applied.objectById(nodeId)).version)
        assertEquals("Ready", assertIs<TextNode>(applied.objectById(nodeId)).text)
    }

    @Test
    fun groupAttributesCanBeLockedLayeredAndUndone() {
        val groupId = CanvasObjectId("group-1")
        val group = GroupFrame(
            id = groupId,
            zIndex = 2,
            transform = CanvasTransform(Vec2(80f, 40f), CanvasSize(600f, 240f)),
        )
        var history = emptyHistory().execute(CreateObjectsOperation("group", listOf(group))).history
        val before = GroupFrameAttributes(group.zIndex, group.locked, group.colorToken, group.title)
        val after = before.copy(zIndex = 20, locked = true, colorToken = "amber", title = "Key themes")

        history = history.execute(
            UpdateGroupFrameAttributesOperation(
                operationId = "style-group",
                changes = listOf(GroupFrameAttributesChange(groupId, 1, before, after)),
            ),
        ).history

        val updated = assertIs<GroupFrame>(history.workspace.objectById(groupId))
        assertEquals(20, updated.zIndex)
        assertTrue(updated.locked)
        assertEquals("amber", updated.colorToken)
        assertEquals("Key themes", updated.title)

        history = history.undo().history
        val restored = assertIs<GroupFrame>(history.workspace.objectById(groupId))
        assertEquals(2, restored.zIndex)
        assertFalse(restored.locked)
        assertEquals("group", restored.colorToken)
        assertEquals("Group", restored.title)
    }

    @Test
    fun reparentRejectsNestedGroupCycle() {
        val outerId = CanvasObjectId("outer")
        val innerId = CanvasObjectId("inner")
        val outer = GroupFrame(
            outerId,
            transform = CanvasTransform(Vec2.Zero, CanvasSize(600f, 400f)),
        )
        val inner = GroupFrame(
            innerId,
            parentId = outerId,
            transform = CanvasTransform(Vec2(40f, 40f), CanvasSize(300f, 200f)),
        )
        val history = emptyHistory().execute(CreateObjectsOperation("groups", listOf(outer, inner))).history

        val result = history.execute(
            ReparentObjectsOperation(
                operationId = "cycle",
                changes = listOf(ParentChange(outerId, 1, null, innerId)),
            ),
        )

        assertFalse(result.succeeded)
        assertIs<OperationError.HierarchyCycle>(result.error)
        assertEquals(null, result.history.workspace.objectById(outerId)?.parentId)
    }

    @Test
    fun createAndReparentRejectMissingNonGroupAndCyclicParentsAtomically() {
        val missingParentId = CanvasObjectId("missing")
        val orphan = TextNode(
            id = nodeId,
            parentId = missingParentId,
            transform = initialTransform,
            text = "Orphan",
        )
        val missingParent = emptyHistory().execute(CreateObjectsOperation("orphan", listOf(orphan)))
        assertFalse(missingParent.succeeded)
        assertIs<OperationError.MissingObject>(missingParent.error)
        assertTrue(missingParent.history.workspace.objects.isEmpty())

        val parentText = TextNode(nodeId, transform = initialTransform, text = "Not a group")
        val childId = CanvasObjectId("child")
        val child = TextNode(
            id = childId,
            parentId = nodeId,
            transform = initialTransform,
            text = "Child",
        )
        val invalidCreate = emptyHistory().execute(
            CreateObjectsOperation("invalid-parent", listOf(parentText, child)),
        )
        assertFalse(invalidCreate.succeeded)
        assertIs<OperationError.UnsupportedObject>(invalidCreate.error)
        assertTrue(invalidCreate.history.workspace.objects.isEmpty())

        val firstGroupId = CanvasObjectId("first-group")
        val secondGroupId = CanvasObjectId("second-group")
        val firstGroup = GroupFrame(
            id = firstGroupId,
            parentId = secondGroupId,
            transform = initialTransform,
        )
        val secondGroup = GroupFrame(
            id = secondGroupId,
            parentId = firstGroupId,
            transform = initialTransform,
        )
        val cyclicCreate = emptyHistory().execute(
            CreateObjectsOperation("cycle", listOf(firstGroup, secondGroup)),
        )
        assertFalse(cyclicCreate.succeeded)
        assertIs<OperationError.HierarchyCycle>(cyclicCreate.error)
        assertTrue(cyclicCreate.history.workspace.objects.isEmpty())

        val valid = emptyHistory().execute(
            CreateObjectsOperation("texts", listOf(parentText, child.copy(parentId = null))),
        ).history
        val invalidReparent = valid.execute(
            ReparentObjectsOperation(
                operationId = "parent-to-text",
                changes = listOf(ParentChange(childId, 1, null, nodeId)),
            ),
        )
        assertFalse(invalidReparent.succeeded)
        assertIs<OperationError.UnsupportedObject>(invalidReparent.error)
        assertEquals(null, invalidReparent.history.workspace.objectById(childId)?.parentId)
    }
}
