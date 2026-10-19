package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class TextPlacementAndPenAlignmentTest {
    @Test fun allAnchorsAreOutsideOrInsideAsRequestedAndLargeTextIsNotClamped() {
        val box = CanvasSize(100f, 80f); val text = CanvasSize(40f, 20f)
        assertEquals(Vec2(30f, -28f), textLayerPosition(TextAnchor.Above, box, text, 8f, 8f))
        assertEquals(Vec2(30f, 88f), textLayerPosition(TextAnchor.Below, box, text, 8f, 8f))
        assertEquals(Vec2(-48f, 30f), textLayerPosition(TextAnchor.Left, box, text, 8f, 8f))
        assertEquals(Vec2(108f, 30f), textLayerPosition(TextAnchor.Right, box, text, 8f, 8f))
        assertEquals(Vec2(-10f, -20f), textLayerPosition(TextAnchor.Center, box, CanvasSize(120f, 120f), 8f, 8f))
        assertFails { TextPlacement(offsetX = Float.NaN) }
        assertFails { TextPlacement(offsetY = 100_001f) }
        assertFails { TextAppearance(fontSize = Float.POSITIVE_INFINITY) }
        assertFails { TextAppearance(weight = 99) }
        assertFails { TextAppearance(color = "url(private)") }
    }

    @Test fun snappedAnchorPreservesHandlesAndCommitsOnlyOneUndoStep() {
        val original = PenPathDraft(CanvasSize(100f, 100f), listOf(
            PenAnchor(Vec2(10f, 20f)), PenAnchor(Vec2(40f, 60f), incoming = Vec2(35f, 55f), outgoing = Vec2(45f, 65f))))
        val history = PenPathHistory(original)
        val gesture = assertNotNull(beginPenEditorGesture(history, Vec2(42f, 61f), 5f, false, 1))
        val (moved, guide) = gesture.moveAligned(Vec2(13f, 22f), 3f, true)
        assertEquals(Vec2(10f, 20f), moved.working.anchors[1].point)
        assertEquals(Vec2(5f, 15f), moved.working.anchors[1].incoming)
        assertEquals(10f, guide.x); assertEquals(20f, guide.y)
        assertEquals(original, history.draft)
        val committed = moved.commit(); assertEquals(1, committed.undoStack.size)
        assertEquals(original, committed.undo().draft)
        val free = gesture.moveAligned(Vec2(13f, 22f), 3f, false).first
        assertEquals(Vec2(11f, 21f), free.working.anchors[1].point)
        assertNull(alignPenPoint(original, Vec2(90f, 90f), 1, 3f, true).x)
    }

    @Test fun penLabelOnlyEditKeepsGeometryAndClipboardV8PreservesPositionThroughUndoRedo() {
        val path = PenPathDraft(CanvasSize(100f, 100f), listOf(PenAnchor(Vec2(10f, 10f)), PenAnchor(Vec2(80f, 80f)))).toVectorPath()
        val node = TextNode(CanvasObjectId("pen"), transform = CanvasTransform(Vec2(20f, 30f), CanvasSize(200f, 80f), 90f), text = "old", vectorPath = path)
        val ws = Workspace(WorkspaceId("board"), "Board", objects = mapOf(node.id to node))
        val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Editor, ws.version, 0, ws)
        val placement = TextPlacement(TextAnchor.Right, 12f, -7f, TextAppearance(28f, 700, "#1976d2", TextFont.Monospace, true, true, true))
        val op = assertNotNull(PenCanvasEditing(owner, ws, node).operation(path, "label", "中文🙂", placement))
        val applied = WorkspaceHistory(ws).execute(op); assertTrue(applied.succeeded)
        val after = applied.history.workspace.objects.getValue(node.id) as TextNode
        assertEquals(node.transform, after.transform); assertEquals(path, after.vectorPath)
        assertEquals("中文🙂", after.text); assertEquals(placement, after.textPlacement)
        val undone = applied.history.undo(); assertTrue(undone.succeeded)
        val restored = undone.history.workspace.objects.getValue(node.id) as TextNode
        assertEquals(node.text, restored.text); assertNull(restored.textPlacement)
        assertTrue(undone.history.redo().succeeded)
        val payload = assertNotNull(workspaceClipboardPayload(applied.history.workspace, setOf(node.id)))
        assertEquals(8, payload.version); assertNull(validateClipboardPayload(payload))
        assertEquals(ClipboardPayloadIssue.UnsupportedVersion, validateClipboardPayload(payload.copy(version = 7)))
        val serialized = ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload)
        val decoded = ClipboardJson.decodeFromString(ClipboardPayload.serializer(), serialized)
        val insertion = prepareClipboardInsertion(decoded, Workspace(WorkspaceId("dest"), "Dest"))
        val pasted = (insertion.operation.applyTo(Workspace(WorkspaceId("dest"), "Dest")) as OperationResult.Applied).workspace
        assertEquals(placement, pasted.objects.values.filterIsInstance<TextNode>().single().textPlacement)
    }
}
