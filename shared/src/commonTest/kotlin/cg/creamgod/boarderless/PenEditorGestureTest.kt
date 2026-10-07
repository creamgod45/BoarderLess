package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class PenEditorGestureTest {
    private val empty = PenPathHistory(PenPathDraft(CanvasSize(400f, 300f)))

    @Test fun clickAndDraggedNewAnchorCommitOnlyOneHistoryStep() {
        val click = checkNotNull(beginPenEditorGesture(empty, Vec2(50f, 50f), 10f, true, null)).commit()
        assertEquals(PenAnchor(Vec2(50f, 50f)), click.draft.anchors.single())
        assertEquals(1, click.undoStack.size)
        var drag = checkNotNull(beginPenEditorGesture(click, Vec2(150f, 50f), 10f, true, null))
        repeat(100) { drag = drag.move(Vec2(150f + it, 50f)) }
        val result = drag.commit()
        assertEquals(2, result.undoStack.size)
        assertEquals(Vec2(249f, 50f), result.draft.anchors.last().outgoing)
        assertEquals(Vec2(51f, 50f), result.draft.anchors.last().incoming)
        assertEquals(click.draft, result.undo().draft)
        assertEquals(result.draft, result.undo().redo().draft)
    }

    @Test fun anchorDragUsesOriginalOffsetAndMovesBothHandlesNotAccumulatedDeltas() {
        val anchor = PenAnchor(Vec2(50f, 50f), Vec2(30f, 50f), Vec2(70f, 50f))
        val before = empty.copy(draft = empty.draft.append(anchor))
        val gesture = checkNotNull(beginPenEditorGesture(before, Vec2(52f, 50f), 10f, false, 0))
        assertEquals(PenHandle.Anchor, gesture.handle)
        val result = gesture.move(Vec2(62f, 50f)).move(Vec2(72f, 60f)).commit()
        assertEquals(anchor.translated(Vec2(20f, 10f)), result.draft.anchors.single())
        assertEquals(before.draft, result.undo().draft)
    }

    @Test fun selectedHandlesAreIndependentlyDraggableWithoutChangingAnchor() {
        val anchor = PenAnchor(Vec2(50f, 50f), Vec2(20f, 50f), Vec2(80f, 50f))
        val before = empty.copy(draft = empty.draft.append(anchor))
        val gesture = checkNotNull(beginPenEditorGesture(before, Vec2(81f, 50f), 10f, false, 0))
        assertEquals(PenHandle.Outgoing, gesture.handle)
        val result = gesture.move(Vec2(91f, 60f)).commit()
        assertEquals(anchor.copy(outgoing = Vec2(90f, 60f)), result.draft.anchors.single())
        assertNull(beginPenEditorGesture(before, Vec2(81f, 50f), 10f, false, null))
    }

    @Test fun cancellationLeavesHistoryUnchangedAndClosedOrOutsideDraftDoesNotAppend() {
        val gesture = checkNotNull(beginPenEditorGesture(empty, Vec2(50f, 50f), 10f, true, null)).move(Vec2(80f, 90f))
        assertTrue(empty.draft.anchors.isEmpty()) // Only commit publishes a draft.
        assertEquals(empty, gesture.before)
        assertNull(beginPenEditorGesture(empty, Vec2(-1f, 0f), 10f, true, null))
        assertNull(beginPenEditorGesture(empty, Vec2(450f, 50f), 10f, true, null))
        assertNull(beginPenEditorGesture(empty, Vec2(50f, 50f), 10f, false, null))
        val closed = empty.copy(draft = empty.draft.append(PenAnchor(Vec2(10f, 10f))).append(PenAnchor(Vec2(20f, 20f))).copy(closed = true))
        assertNull(beginPenEditorGesture(closed, Vec2(100f, 100f), 10f, true, null))
        assertFails { beginPenEditorGesture(empty, Vec2.Zero, Float.NaN, true, null) }
    }
}
