package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.PenEditorViewport
import cg.creamgod.boarderless.feature.canvas.penEditorTransform
import cg.creamgod.boarderless.feature.canvas.pinchFrom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PenEditorPinchTest {
    private val box = CanvasSize(200f, 100f)
    private fun transform(viewport: PenEditorViewport = PenEditorViewport.reset(box)) =
        requireNotNull(penEditorTransform(box, viewport, 400f, 300f, 24f))

    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 1e-3f, "$a != $b")

    @Test fun unchangedFingersLeaveTheCameraAlone() {
        val start = PenEditorViewport.reset(box)
        val result = transform().pinchFrom(start, Vec2(100f, 100f), Vec2(200f, 100f), Vec2(100f, 100f), Vec2(200f, 100f))
        near(result.zoom, start.zoom); near(result.center.x, start.center.x); near(result.center.y, start.center.y)
    }

    @Test fun spreadingFingersZoomsAndKeepsThePointUnderTheMidpoint() {
        val start = PenEditorViewport.reset(box)
        val before = transform(start)
        val a = Vec2(120f, 140f); val b = Vec2(220f, 140f)
        val nowA = Vec2(100f, 140f); val nowB = Vec2(240f, 140f)
        val result = before.pinchFrom(start, a, b, nowA, nowB)
        near(result.zoom, 1.4f)
        val after = transform(result)
        val anchor = before.local(Vec2(170f, 140f))
        val shown = after.screen(anchor)
        near(shown.x, 170f); near(shown.y, 140f)
    }

    @Test fun movingBothFingersTogetherOnlyPans() {
        val start = PenEditorViewport.reset(box)
        val before = transform(start)
        val result = before.pinchFrom(start, Vec2(100f, 100f), Vec2(200f, 100f), Vec2(130f, 120f), Vec2(230f, 120f))
        near(result.zoom, 1f)
        val anchor = before.local(Vec2(150f, 100f))
        val shown = transform(result).screen(anchor)
        near(shown.x, 180f); near(shown.y, 120f)
    }

    @Test fun zoomIsClampedAndDegenerateFingersAreIgnored() {
        val start = PenEditorViewport.reset(box)
        val base = transform(start)
        assertEquals(PenEditorViewport.MAX_ZOOM, base.pinchFrom(start, Vec2(0f, 0f), Vec2(1f, 0f), Vec2(0f, 0f), Vec2(10_000f, 0f)).zoom)
        assertEquals(start, base.pinchFrom(start, Vec2(5f, 5f), Vec2(5f, 5f), Vec2(0f, 0f), Vec2(100f, 0f)))
    }
}
