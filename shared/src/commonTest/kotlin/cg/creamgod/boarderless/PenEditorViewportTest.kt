package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class PenEditorViewportTest {
    private val viewBox = CanvasSize(400f, 300f)
    @Test fun resetLetterboxesAndInverseSurvivesZoomAndPan() {
        val viewport = PenEditorViewport.reset(viewBox).zoomBy(2f).copy(center = Vec2(180f, 90f))
        val transform = penEditorTransform(viewBox, viewport, 848f, 648f, 24f)!!
        assertEquals(4f, transform.scale)
        val point = Vec2(67f, 201f)
        assertEquals(point, transform.local(transform.screen(point)))
        assertEquals(Vec2(424f, 324f), transform.screen(viewport.center))
        val reset = penEditorTransform(viewBox, PenEditorViewport.reset(viewBox), 848f, 348f, 24f)!!
        assertEquals(1f, reset.scale)
        assertEquals(Vec2(224f, 24f), reset.origin)
    }
    @Test fun panUsesOriginalCameraAndScreenDeltaWithoutEditingHistory() {
        val viewport = PenEditorViewport.reset(viewBox).zoomBy(2f)
        val transform = penEditorTransform(viewBox, viewport, 448f, 348f, 24f)!!
        val history = PenPathHistory(PenPathDraft(viewBox))
        repeat(100) { transform.panFrom(viewport, Vec2(it.toFloat(), 0f)) }
        assertEquals(Vec2(150f, 125f), transform.panFrom(viewport, Vec2(100f, 50f)).center)
        assertEquals(PenPathDraft(viewBox), history.draft)
        assertTrue(history.undoStack.isEmpty())
        assertEquals(1_000_000f, transform.panFrom(viewport, Vec2(-10_000_000f, 0f)).center.x)
    }
    @Test fun fitIncludesUnselectedOutsideHandlesAndExtremeLegalCoordinates() {
        val draft = PenPathDraft(viewBox, listOf(PenAnchor(Vec2(10f, 20f), Vec2(-1_000_000f, -1_000_000f), Vec2(1_000_000f, 1_000_000f))))
        val viewport = PenEditorViewport.fit(draft)
        val transform = penEditorTransform(viewBox, viewport, 448f, 348f, 24f)!!
        (draft.anchors.flatMap { listOfNotNull(it.point, it.incoming, it.outgoing) } + listOf(Vec2(0f, 0f), Vec2(400f, 300f))).forEach {
            val screen = transform.screen(it)
            assertTrue(screen.x >= 24f && screen.x <= 424f)
            assertTrue(screen.y >= 23.99f && screen.y <= 324.01f)
        }
        assertEquals(PenEditorViewport.reset(viewBox), PenEditorViewport.fit(PenPathDraft(viewBox)))
    }
    @Test fun zoomAndInvalidSizesAreBounded() {
        val viewport = PenEditorViewport.reset(viewBox)
        assertEquals(PenEditorViewport.MAX_ZOOM, viewport.zoomBy(Float.MAX_VALUE).zoom)
        assertEquals(PenEditorViewport.MIN_ZOOM, viewport.zoomBy(Float.MIN_VALUE).zoom)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { assertFailsWith<IllegalArgumentException> { viewport.zoomBy(it) } }
        assertNull(penEditorTransform(viewBox, viewport, 48f, 300f, 24f))
        assertNull(penEditorTransform(viewBox, viewport, 300f, Float.NaN, 24f))
        assertNull(penEditorTransform(viewBox, viewport, 300f, 300f, -1f))
    }
    @Test fun editingAfterPanAndZoomKeepsScreenOffsetAndOneHistoryStep() {
        val anchor = PenAnchor(Vec2(80f, 70f), Vec2(50f, 70f), Vec2(110f, 70f))
        val history = PenPathHistory(PenPathDraft(viewBox, listOf(anchor)))
        val camera = PenEditorViewport(Vec2(90f, 80f), 4f)
        val transform = penEditorTransform(viewBox, camera, 448f, 348f, 24f)!!
        val down = transform.screen(anchor.point) + Vec2(8f, 4f)
        val gesture = beginPenEditorGesture(history, transform.local(down), 14f / transform.scale, false, null)!!
        val moved = gesture.move(transform.local(down + Vec2(40f, -20f))).commit()
        assertEquals(anchor.translated(Vec2(10f, -5f)), moved.draft.anchors.single())
        assertEquals(1, moved.undoStack.size)
        assertEquals(history.draft, moved.undo().draft)
        assertEquals(camera.center, transform.center)
    }
}
