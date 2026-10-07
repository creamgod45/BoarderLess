package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.math.abs
import kotlin.test.*

class RelationGeometryGestureTest {
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)), text = "A")
    private val b = a.copy(id = CanvasObjectId("b"), transform = a.transform.copy(position = Vec2(400f, 0f)), text = "B")
    private val edge = Relation(RelationId("edge"), sourceObjectId = a.id, targetObjectId = b.id, label = "標籤🙂")
    private val workspace =
        Workspace(WorkspaceId("board"), "Board", objects = mapOf(a.id to a, b.id to b), relations = mapOf(edge.id to edge))
    private val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 0, 0, workspace)
    private val view = Viewport(pan = Vec2(30f, 60f), zoom = 2f)

    private fun begin(handle: RelationGeometryHandle = RelationGeometryHandle.NewWaypoint) =
        RelationGeometryGesture.begin(owner, workspace, edge, view, handle, "gesture")

    @Test fun autoHandleDragHasNoFormalChangesUntilOneCommitAndUsesFrozenZoom() {
        val initial = begin()
        assertNull(initial.operation(owner, workspace, view))
        val middle = polylineMidpoint(relationWorldRoute(edge, workspace.objects)!!)
        val moved = initial.move(Vec2(40f, 60f)).move(Vec2(80f, 100f))
        assertEquals(listOf(middle + Vec2(40f, 50f)), (moved.working.route as RelationRoute.Manual).waypoints)
        assertEquals(workspace, owner.workspace)
        val operation = assertNotNull(moved.operation(owner, workspace, view))
        assertEquals("gesture", operation.operationId)
        assertEquals(1, operation.changes.size)
        val changed = WorkspaceHistory(workspace).execute(operation)
        assertTrue(changed.succeeded)
        assertEquals(1, changed.history.undoOperations.size)
        assertEquals(
            moved.working,
            changed.history.workspace.relations
                .getValue(edge.id)
                .geometry,
        )
        assertNull(
            changed.history
                .undo()
                .history.workspace.relations
                .getValue(edge.id)
                .geometry,
        )
        assertNull(moved.move(Vec2.Zero).operation(owner, workspace, view))
    }

    @Test fun manualHandleMovesOnlyChosenWorldPointAndBoundsRejectInvalidPreview() {
        val geometry = RelationGeometry(route = RelationRoute.Manual(listOf(Vec2(150f, 180f), Vec2(300f, 180f))))
        val manual = edge.copy(geometry = geometry)
        val live = workspace.copy(relations = mapOf(manual.id to manual))
        val gesture = RelationGeometryGesture.begin(owner, live, manual, view, RelationGeometryHandle.Waypoint(1), "point")
        val moved = gesture.move(Vec2(40f, -20f))
        assertEquals(listOf(Vec2(150f, 180f), Vec2(320f, 170f)), (moved.working.route as RelationRoute.Manual).waypoints)
        assertFails { gesture.move(Vec2(30_000_000f, 0f)) }
        assertFails { RelationGeometryGesture.begin(owner, live, manual, view, RelationGeometryHandle.Waypoint(2)) }
    }

    @Test fun loopExtentUsesRotatedLocalSideAndClampsWithoutZeroLoopOrEndpointChange() {
        val node = a.copy(transform = a.transform.copy(rotationDegrees = 90f))
        val loop = edge.copy(targetObjectId = a.id, geometry = RelationGeometry(route = RelationRoute.Loop("right", 80f)))
        val live = workspace.copy(objects = workspace.objects + (a.id to node), relations = mapOf(loop.id to loop))
        val gesture = RelationGeometryGesture.begin(owner, live, loop, view, RelationGeometryHandle.LoopExtent)
        val moved = gesture.move(Vec2(0f, 80f))
        assertTrue(abs((moved.working.route as RelationRoute.Loop).extent - 120f) < 0.01f)
        assertEquals(16f, (gesture.move(Vec2(0f, -10000f)).working.route as RelationRoute.Loop).extent)
        assertEquals(4096f, (gesture.move(Vec2(0f, 10000f)).working.route as RelationRoute.Loop).extent)
        val operation = assertNotNull(moved.operation(owner, live, view))
        val after = (operation.applyTo(live) as OperationResult.Applied).workspace.relations.getValue(loop.id)
        assertEquals(a.id, after.sourceObjectId)
        assertEquals(a.id, after.targetObjectId)
    }

    @Test fun labelProjectionAndDragKeepActualWorldAnchorIncludingLongRoutesAndDegenerateSegments() {
        val path = listOf(Vec2.Zero, Vec2.Zero, Vec2(10_000f, 0f), Vec2(10_000f, 10_000f))
        val anchor = Vec2(10_024f, 9_000f)
        val placement = assertNotNull(labelPlacementAtWorldPoint(path, anchor))
        val actual = assertNotNull(manualRelationLabelAnchor(path, placement))
        assertTrue(abs(actual.x - anchor.x) < 0.01f && abs(actual.y - anchor.y) < 0.01f)
        assertNull(labelPlacementAtWorldPoint(listOf(Vec2.Zero, Vec2.Zero), Vec2.Zero))
        val route = relationWorldRoute(edge, workspace.objects)!!
        val center = polylineMidpoint(route) + Vec2(0f, 30f)
        val gesture = begin(RelationGeometryHandle.Label(center)).move(Vec2(40f, 20f))
        val result = manualRelationLabelAnchor(route, gesture.working.labelPlacement as RelationLabelPlacement.Manual)!!
        assertTrue(abs(result.x - (center.x + 20f)) < 0.01f && abs(result.y - (center.y + 10f)) < 0.01f)
        assertNotNull(gesture.operation(owner, workspace, view))
    }

    @Test fun scopePermissionContentCheckpointAndViewChangesCannotCommitOrRebaseGesture() {
        val moved = begin().move(Vec2(30f, 40f))
        for (current in listOf(
            owner.copy(userId = "other"),
            owner.copy(clientId = "other"),
            owner.copy(role = WorkspaceMemberRole.Viewer),
            owner.copy(workspace = workspace.copy(id = WorkspaceId("other"))),
        )) {
            assertFalse(moved.matches(current, workspace, view))
            assertFails { moved.operation(current, workspace, view) }
        }
        assertFails { moved.operation(null, workspace, view) }
        assertFails { moved.operation(owner, workspace.copy(version = 1), view) }
        assertFails { moved.operation(owner, workspace, view.copy(zoom = 1f)) }
        assertFails { moved.operation(owner, workspace, view.copy(pan = Vec2.Zero)) }
        val changed = workspace.copy(relations = mapOf(edge.id to edge.copy(version = 2, label = "Other")))
        assertFails { moved.operation(owner, changed, view) }
        assertEquals(workspace, owner.workspace)
    }

    @Test fun pointerSlopUsesScreenUnitsAndClickOrCancelledPreviewHasNoOperation() {
        val gesture = begin()
        val motion = ObjectSelectionGesture(false, 10f)
        assertFalse(motion.move(Vec2(3f, 4f)))
        assertFalse(motion.move(Vec2(1f, 1f)))
        assertNull(gesture.operation(owner, workspace, view))
        assertTrue(motion.move(Vec2(10f, 0f)))
        val preview = gesture.move(motion.displacement)
        assertNotNull(preview.operation(owner, workspace, view))
        // Discarding a preview is cancellation: no history entry or domain mutation happened.
        assertFalse(WorkspaceHistory(workspace).canUndo)
        assertNull(workspace.relations.getValue(edge.id).geometry)
    }
}
