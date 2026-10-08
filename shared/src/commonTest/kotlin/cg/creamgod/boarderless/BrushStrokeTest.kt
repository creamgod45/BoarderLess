package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class BrushStrokeTest {
    private val ws = Workspace(WorkspaceId("board"), "Board")
    private val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Editor, 0, 0, ws)
    @Test fun straightSamplesSimplifyButCornersAndEndpointsAreKept() {
        val line = (0..100).map { Vec2(it.toFloat(), 10f) }
        assertEquals(listOf(line.first(), line.last()), simplifyBrushStroke(line, .5f))
        val corners = listOf(Vec2.Zero, Vec2(0f, 20f), Vec2(20f, 20f), Vec2(20f, 0f))
        assertEquals(corners, simplifyBrushStroke(corners, .5f))
        assertFails { simplifyBrushStroke(line, Float.NaN) }
    }
    @Test fun worldCoordinatesBecomeOneObjectAndUndoRedoKeepsTheExactStroke() {
        val points = listOf(Vec2(-40f, 70f), Vec2(20f, 100f), Vec2(80f, 70f))
        val draft = prepareBrushStroke(owner, ws, points, 8f, "#1976d2", .5f, "stroke", "op")
        val node = draft.operation.objects.single() as TextNode
        val path = assertNotNull(node.vectorPath)
        assertNull(path.style.fillColorToken); assertEquals(8f, path.style.strokeWidth)
        val world = path.commands.map { when (it) {
            is VectorPathCommand.Move -> it.point
            is VectorPathCommand.Line -> it.point
            else -> error("Unexpected stroke command")
        } }.map { it + node.transform.position }
        assertEquals(points, world)
        val saved = WorkspaceHistory(ws).execute(draft.operation); assertTrue(saved.succeeded)
        assertEquals(1, saved.history.workspace.objects.size)
        val undone = saved.history.undo(); assertTrue(undone.history.workspace.objects.isEmpty())
        assertEquals(path, (undone.history.redo().history.workspace.objects.values.single() as TextNode).vectorPath)
        assertTrue(draft.isCurrent(PenCanvasLive(owner, ws, true)))
        assertFalse(draft.isCurrent(PenCanvasLive(owner.copy(role = WorkspaceMemberRole.Viewer), ws, true)))
        assertFalse(draft.isCurrent(PenCanvasLive(owner, saved.history.workspace, true)))
        assertFalse(draft.isCurrent(PenCanvasLive(owner, ws, false)))
    }
    @Test fun tapCreatesVisibleNonEmptyStrokeAndInvalidOrExcessivePointsDoNotTruncate() {
        val tap = prepareBrushStroke(owner, ws, listOf(Vec2(50f, 50f)), 4f, "#000000", .5f, "tap", "op")
        assertEquals(2, (tap.operation.objects.single() as TextNode).vectorPath!!.commands.size)
        assertFails { prepareBrushStroke(owner, ws, emptyList(), 4f, "#000000", .5f, "bad", "op") }
        assertFails { prepareBrushStroke(owner, ws, List(8193) { Vec2.Zero }, 4f, "#000000", .5f, "bad", "op") }
        assertFails { prepareBrushStroke(owner, ws, listOf(Vec2(-200_000f, 0f), Vec2(200_000f, 0f)), 4f, "#000000", .5f, "bad", "op") }
    }
}
