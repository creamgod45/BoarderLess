package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class PenCanvasEditingTest {
    private val draft =
        PenPathDraft(
            CanvasSize(100f, 100f),
            listOf(
                PenAnchor(Vec2(10f, 10f), outgoing = Vec2(20f, 0f)),
                PenAnchor(Vec2(70f, 60f), incoming = Vec2(80f, 20f)),
                PenAnchor(Vec2(10f, 80f)),
            ),
            closed = true,
        )
    private val group = GroupFrame(CanvasObjectId("group"), transform = CanvasTransform(Vec2.Zero, CanvasSize(600f, 600f)))
    private val node =
        TextNode(
            CanvasObjectId("vector"),
            version = 4,
            parentId = group.id,
            zIndex = 9,
            transform = CanvasTransform(Vec2(300f, 50f), CanvasSize(200f, 100f), 90f),
            text = "label🙂",
            vectorPath = draft.toVectorPath(),
        )
    private val loop =
        Relation(
            RelationId("loop"),
            sourceObjectId = node.id,
            targetObjectId = node.id,
            geometry = RelationGeometry(route = RelationRoute.Loop()),
        )
    private val baseline =
        Workspace(
            WorkspaceId("board"),
            "Board",
            version = 8,
            objects = mapOf(group.id to group, node.id to node),
            relations =
                mapOf(loop.id to loop),
        )
    private val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 8, 20, baseline)
    private val capture = PenCanvasEditing(owner, baseline, node)

    @Test fun reopeningCompleteContourPreservesCubicControlsStylesAndNoOpDoesNotRewriteBounds() {
        assertEquals(draft, capture.draft)
        assertNull(capture.operation(capture.draft.toVectorPath(), "unchanged"))
        val implicit =
            VectorPath(
                CanvasSize(100f, 100f),
                listOf(
                    VectorPathCommand.Move(Vec2.Zero),
                    VectorPathCommand.Line(Vec2(100f, 0f)),
                    VectorPathCommand.Line(Vec2(50f, 100f)),
                    VectorPathCommand.Close,
                ),
            )
        val imported = node.copy(vectorPath = implicit)
        val workspace = baseline.copy(objects = baseline.objects + (imported.id to imported))
        val editing = PenCanvasEditing(owner.copy(workspace = workspace), workspace, imported)
        assertTrue(editing.draft.closed)
        assertNull(editing.operation(editing.draft.toVectorPath(), "unchanged"))
        val quadratic =
            VectorPath(
                CanvasSize(100f, 100f),
                listOf(VectorPathCommand.Move(Vec2.Zero), VectorPathCommand.Quadratic(Vec2(50f, 100f), Vec2(100f, 0f))),
            )
        assertEquals(quadratic, assertNotNull(quadratic.toPenEditorDraft()).toVectorPath())
    }

    @Test fun strokeBoundsExpandWithoutMovingTheCurveOnARotatedNonUniformlyScaledNode() {
        val changed = capture.draft.copy(style = capture.draft.style.copy(strokeWidth = 4f)).toVectorPath()
        val operation = assertNotNull(capture.operation(changed, "edit"))
        val after = (operation.applyTo(baseline) as OperationResult.Applied).workspace
        val edited = after.objects.getValue(node.id) as TextNode
        assertEquals(2, operation.operations.size)
        assertEquals(CanvasSize(148f, 84f), edited.transform.size)
        assertEquals(336f, edited.transform.position.x, 0.001f)
        assertEquals(48f, edited.transform.position.y, 0.001f)
        assertEquals(90f, edited.transform.rotationDegrees)
        val first = (edited.vectorPath!!.commands.first() as VectorPathCommand.Move).point
        assertEquals(Vec2(2f, 12f), first)
        // New affine coordinates put the unchanged first anchor at the original world (440,20).
        val world =
            edited.transform.position + Vec2(edited.transform.size.width / 2f, edited.transform.size.height / 2f) +
                rotateVector(
                    Vec2((first.x - edited.vectorPath!!.viewBox.width / 2f) * 2f, first.y - edited.vectorPath!!.viewBox.height / 2f),
                    90f,
                )
        assertEquals(440f, world.x, 0.001f)
        assertEquals(20f, world.y, 0.001f)
        assertEquals(node.id, edited.id)
        assertEquals(node.parentId, edited.parentId)
        assertEquals(node.zIndex, edited.zIndex)
        assertEquals(node.text, edited.text)
        assertEquals(baseline.relations, after.relations)
        assertEquals(9, after.version)
        assertEquals(6, edited.version)
    }

    @Test fun entireEditIsOneHistoryEntryWithExactPathTransformAndEdgesOnUndoRedo() {
        val changed = capture.draft.replace(1, capture.draft.anchors[1].translated(Vec2(20f, 30f))).toVectorPath()
        val operation = assertNotNull(capture.operation(changed, "edit"))
        val applied = WorkspaceHistory(baseline).execute(operation)
        assertTrue(applied.succeeded)
        val undone = applied.history.undo()
        assertTrue(undone.succeeded)
        val restored =
            undone.history.workspace.objects
                .getValue(node.id) as TextNode
        assertEquals(node.transform, restored.transform)
        assertEquals(node.vectorPath, restored.vectorPath)
        assertEquals(baseline.relations, undone.history.workspace.relations)
        val redo = undone.history.redo()
        assertTrue(redo.succeeded)
        val after =
            applied.history.workspace.objects
                .getValue(node.id) as TextNode
        val redone =
            redo.history.workspace.objects
                .getValue(node.id) as TextNode
        assertEquals(after.transform, redone.transform)
        assertEquals(after.vectorPath, redone.vectorPath)
        assertEquals(11, redo.history.workspace.version)
        assertContains(draftMergeContractGaps(baseline, operation), DraftMergeContractGap.VectorPath)
        assertFails { operation.toExpandedDtos(0) }
    }

    @Test fun actorSnapshotCheckpointLockedAncestorsMissingParentAndCyclesRejectEdit() {
        assertTrue(capture.isCurrent(owner, baseline))
        for (current in listOf(
            owner.copy(userId = "other"),
            owner.copy(clientId = "other"),
            owner.copy(role = WorkspaceMemberRole.Viewer),
            owner.copy(workspaceVersion = 9),
            owner.copy(lastServerSeq = 21),
        )) {
            assertFalse(capture.isCurrent(current, baseline))
        }
        assertFalse(capture.isCurrent(owner, baseline.copy(title = "Changed")))
        assertFalse(capture.isCurrent(owner, baseline.copy(objects = baseline.objects + (node.id to node.copy(version = 5)))))
        for (workspace in listOf(
            baseline.copy(objects = baseline.objects + (group.id to group.copy(locked = true))),
            baseline.copy(
                objects =
                    mapOf(node.id to node),
            ),
            baseline.copy(objects = baseline.objects + (group.id to group.copy(parentId = group.id))),
        )) {
            assertFalse(editablePenNode(workspace, node))
            assertFails { PenCanvasEditing(owner, workspace, node) }
        }
        val locked = node.copy(locked = true)
        assertFails { PenCanvasEditing(owner, baseline.copy(objects = baseline.objects + (locked.id to locked)), locked) }
    }

    @Test fun compoundPathsAndExcessAnchorsAreNotPartiallyConverted() {
        val multi =
            VectorPath(
                CanvasSize(100f, 100f),
                listOf(
                    VectorPathCommand.Move(Vec2.Zero),
                    VectorPathCommand.Line(Vec2(20f, 20f)),
                    VectorPathCommand.Move(Vec2(40f, 40f)),
                    VectorPathCommand.Line(Vec2(50f, 50f)),
                ),
            )
        assertNull(multi.toPenEditorDraft())
        val many =
            VectorPath(
                CanvasSize(2000f, 100f),
                listOf(VectorPathCommand.Move(Vec2.Zero)) + (1..1025).map { VectorPathCommand.Line(Vec2(it.toFloat(), 10f)) },
            )
        assertNull(many.toPenEditorDraft())
        assertEquals(4, multi.commands.size)
        assertEquals(1026, many.commands.size)
    }

    @Test fun zeroWorldSizeForeignViewBoxUnsafeVersionAndBadGeometryNeverCreateAnEdit() {
        assertFails { capture.operation(draft.toVectorPath().copy(viewBox = CanvasSize(200f, 100f)), "foreign") }
        assertFails {
            capture.operation(
                draft
                    .copy(
                        style = draft.style.copy(strokeColorToken = null),
                        anchors = listOf(PenAnchor(Vec2.Zero), PenAnchor(Vec2(10f, 0f))),
                        closed = false,
                    ).toVectorPath(),
                "empty",
            )
        }
        val huge = draft.copy(anchors = listOf(PenAnchor(Vec2(-100_000f, 0f)), PenAnchor(Vec2(100_000f, 0f))), closed = false)
        assertFails { capture.operation(huge.toVectorPath(), "huge") }
        val unsafe = node.copy(version = 9_007_199_254_740_990L)
        val workspace = baseline.copy(objects = baseline.objects + (unsafe.id to unsafe))
        val captureUnsafe = PenCanvasEditing(owner.copy(workspace = workspace), workspace, unsafe)
        assertFails { captureUnsafe.operation(draft.copy(style = draft.style.copy(strokeWidth = 4f)).toVectorPath(), "limit") }
    }
}
