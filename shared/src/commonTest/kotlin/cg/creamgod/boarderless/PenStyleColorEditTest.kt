package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class PenStyleColorEditTest {
    private val draft = PenPathDraft(CanvasSize(400f, 300f), listOf(PenAnchor(Vec2(10f, 20f)), PenAnchor(Vec2(30f, 40f))))

    @Test fun previewDoesNotCommitAndApplyMakesOneUndoableEdit() {
        val history = PenPathHistory(draft)
        var edit = PenStyleColorEdit(draft, PenPaint.Fill, "lilac")
        repeat(100) {
            edit = edit.copy(token = "#112233")
            edit.preview()
        }
        assertEquals(draft, history.draft)
        assertTrue(history.undoStack.isEmpty())
        val applied = edit.commit(history)
        assertEquals("#112233", applied.draft.style.fillColorToken)
        assertEquals(draft.style.strokeColorToken, applied.draft.style.strokeColorToken)
        assertEquals(draft.anchors, applied.draft.anchors)
        assertEquals(1, applied.undoStack.size)
        assertEquals(draft, applied.undo().draft)
        assertEquals(applied.draft, applied.undo().redo().draft)
        assertEquals(applied.draft, Json.decodeFromString<PenPathDraft>(Json.encodeToString(applied.draft)))
    }

    @Test fun cancelLeavesHistoryAndSameColorIsNoOpWhileStaleDraftIsRejected() {
        val history = PenPathHistory(draft)
        val edit = PenStyleColorEdit(draft, PenPaint.Stroke, "#abcdef")
        edit.preview() // Cancellation discards this value; original history is untouched.
        assertEquals(draft, history.draft)
        assertEquals(history, PenStyleColorEdit(draft, PenPaint.Stroke, "ink").commit(history))
        assertFailsWith<IllegalArgumentException> { edit.commit(history.edit(draft.copy(closed = true))) }
        assertFailsWith<IllegalArgumentException> { edit.copy(token = "<script>").preview() }
    }

    @Test fun paintRemovalCannotHideBothAndFillRuleAndWidthRemainIndependent() {
        val filled = draft.style.withPaint(PenPaint.Fill, "mint").copy(fillRule = VectorFillRule.EvenOdd, strokeWidth = 12.5f)
        val fillOnly = filled.withPaint(PenPaint.Stroke, null)
        assertNull(fillOnly.strokeColorToken)
        assertEquals("mint", fillOnly.fillColorToken)
        assertEquals(VectorFillRule.EvenOdd, fillOnly.fillRule)
        assertEquals(12.5f, fillOnly.strokeWidth)
        assertFailsWith<IllegalArgumentException> { fillOnly.withPaint(PenPaint.Fill, null) }
        assertFailsWith<IllegalArgumentException> { draft.style.withPaint(PenPaint.Stroke, null) }
    }

    @Test fun customStrokeWidthRejectsInvalidAndUnboundedText() {
        listOf("", "0", "-1", "128.01", "NaN", "Infinity", "1e99", "x", "1".repeat(17)).forEach { assertNull(parsePenStrokeWidth(it)) }
        listOf("0.25" to .25f, " 2.5 " to 2.5f, "128" to 128f).forEach { (text, value) -> assertEquals(value, parsePenStrokeWidth(text)) }
    }
}
