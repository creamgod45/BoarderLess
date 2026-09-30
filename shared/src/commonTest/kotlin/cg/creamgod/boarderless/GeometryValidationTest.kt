package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GeometryValidationTest {
    @Test
    fun vectorsRejectNonFiniteCoordinates() {
        assertFailsWith<IllegalArgumentException> { Vec2(Float.NaN, 0f) }
        assertFailsWith<IllegalArgumentException> { Vec2(0f, Float.POSITIVE_INFINITY) }
    }

    @Test
    fun sizesRejectNonFiniteOrNegativeDimensions() {
        assertFailsWith<IllegalArgumentException> { CanvasSize(Float.POSITIVE_INFINITY, 10f) }
        assertFailsWith<IllegalArgumentException> { CanvasSize(10f, Float.NaN) }
        assertFailsWith<IllegalArgumentException> { CanvasSize(-1f, 10f) }
    }

    @Test
    fun transformsRejectNonFiniteRotation() {
        assertFailsWith<IllegalArgumentException> {
            CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f), Float.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun viewportRejectsInvalidZoom() {
        assertFailsWith<IllegalArgumentException> { Viewport(zoom = 0f) }
        assertFailsWith<IllegalArgumentException> { Viewport(zoom = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { Viewport(zoom = Float.POSITIVE_INFINITY) }
    }
}
