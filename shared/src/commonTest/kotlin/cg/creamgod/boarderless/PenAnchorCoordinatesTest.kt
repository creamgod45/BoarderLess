package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class PenAnchorCoordinatesTest {
    private val anchor = PenAnchor(Vec2(80f, 70f), Vec2(50f, 60f), Vec2(110f, 90f))
    private val draft = PenPathDraft(CanvasSize(400f, 300f), listOf(anchor, PenAnchor(Vec2(200f, 150f))))
    @Test fun curveConversionAtBoundsRejectsWithoutMutation() {
        val converted = curvePenAnchor(draft, 0)
        assertEquals(anchor.point - Vec2(30f, 0f), converted.anchors[0].incoming)
        assertEquals(anchor.point + Vec2(30f, 0f), converted.anchors[0].outgoing)
        listOf(-1_000_000f, 1_000_000f).forEach { x ->
            val boundary = draft.copy(anchors = listOf(PenAnchor(Vec2(x, 0f))))
            assertFailsWith<IllegalArgumentException> { curvePenAnchor(boundary, 0) }
            assertNull(boundary.anchors.single().incoming)
            assertNull(boundary.anchors.single().outgoing)
        }
        assertEquals(anchor, draft.anchors[0])
    }
    @Test fun movingAnchorTranslatesBothControlsInOneUndoableEdit() {
        val next = editPenCoordinate(draft, 0, PenCoordinateTarget.Anchor, Vec2(100f, 40f))
        assertEquals(anchor.translated(Vec2(20f, -30f)), next.anchors.first())
        assertEquals(draft.anchors[1], next.anchors[1])
        assertEquals(draft.style, next.style)
        val history = PenPathHistory(draft).edit(next)
        assertEquals(1, history.undoStack.size)
        assertEquals(draft, history.undo().draft)
        assertEquals(next, history.undo().redo().draft)
        assertTrue(PenPathHistory(draft).edit(editPenCoordinate(draft, 0, PenCoordinateTarget.Anchor, anchor.point)).undoStack.isEmpty())
    }
    @Test fun handlesCanBeIndependentlyCreatedChangedRemovedAndSerialized() {
        val incoming = editPenCoordinate(draft, 1, PenCoordinateTarget.Incoming, Vec2(-500f, 800f))
        assertEquals(Vec2(-500f, 800f), incoming.anchors[1].incoming)
        assertNull(incoming.anchors[1].outgoing)
        assertEquals(draft.anchors[1].point, incoming.anchors[1].point)
        val both = editPenCoordinate(incoming, 1, PenCoordinateTarget.Outgoing, Vec2(1000f, -1000f))
        val removed = removePenControl(both, 1, PenCoordinateTarget.Incoming)
        assertNull(removed.anchors[1].incoming)
        assertEquals(both.anchors[1].outgoing, removed.anchors[1].outgoing)
        assertEquals(removed, Json.decodeFromString<PenPathDraft>(Json.encodeToString(removed)))
        assertEquals(removed, removePenControl(removed, 1, PenCoordinateTarget.Incoming))
        assertFailsWith<IllegalArgumentException> { removePenControl(draft, 0, PenCoordinateTarget.Anchor) }
    }
    @Test fun invalidCoordinatesIndexesAndTranslatedHandleOverflowAreAtomic() {
        assertFailsWith<IllegalArgumentException> { editPenCoordinate(draft, -1, PenCoordinateTarget.Anchor, Vec2(0f, 0f)) }
        assertFailsWith<IllegalArgumentException> { editPenCoordinate(draft, 2, PenCoordinateTarget.Incoming, Vec2(0f, 0f)) }
        assertFailsWith<IllegalArgumentException> { editPenCoordinate(draft, 0, PenCoordinateTarget.Outgoing, Vec2(1_000_001f, 0f)) }
        val nearLimit = draft.copy(anchors = listOf(PenAnchor(Vec2(0f, 0f), outgoing = Vec2(999_999f, 0f))))
        assertFailsWith<IllegalArgumentException> { editPenCoordinate(nearLimit, 0, PenCoordinateTarget.Anchor, Vec2(2f, 0f)) }
        assertEquals(Vec2(999_999f, 0f), nearLimit.anchors.single().outgoing)
        assertEquals(anchor, draft.anchors.first())
    }
    @Test fun coordinateParserAcceptsSignedFractionsButRejectsUnsafeText() {
        listOf("", "NaN", "Infinity", "-Infinity", "1e99", "1000001", "-1000001", "0".repeat(17), "<script>").forEach { assertNull(parsePenCoordinate(it)) }
        listOf(" -12.5 " to -12.5f, "0" to 0f, "1000000" to 1_000_000f, "-1000000" to -1_000_000f).forEach { (text, value) -> assertEquals(value, parsePenCoordinate(text)) }
    }
}
