package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import cg.creamgod.boarderless.feature.canvas.CanvasObjectBounds
import cg.creamgod.boarderless.feature.canvas.createMiniMapProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniMapProjectionTest {
    @Test
    fun contentBoundsExposeTheirCenterForAccessibleNavigation() {
        val bounds = CanvasObjectBounds(Vec2(-200f, 100f), Vec2(600f, 500f))

        assertEquals(Vec2(200f, 300f), bounds.center)
    }

    @Test
    fun projectionIncludesBothContentAndVisibleViewport() {
        val projection = createMiniMapProjection(
            contentBounds = CanvasObjectBounds(Vec2(200f, 100f), Vec2(400f, 300f)),
            viewport = Viewport(pan = Vec2.Zero, zoom = 1f),
            canvasSize = CanvasSize(100f, 80f),
            miniMapSize = CanvasSize(200f, 120f),
            padding = 10f,
        )

        assertEquals(Vec2.Zero, projection.worldBounds.topLeft)
        assertEquals(Vec2(400f, 300f), projection.worldBounds.bottomRight)
        assertTrue(projection.viewportRect.topLeft.x >= 0f)
        assertTrue(projection.viewportRect.topLeft.y >= 0f)
        assertTrue(projection.viewportRect.bottomRight.x <= 200f)
        assertTrue(projection.viewportRect.bottomRight.y <= 120f)
    }

    @Test
    fun miniMapAndWorldCoordinatesRoundTrip() {
        val projection = createMiniMapProjection(
            contentBounds = CanvasObjectBounds(Vec2(-500f, -100f), Vec2(800f, 600f)),
            viewport = Viewport(pan = Vec2(90f, 40f), zoom = 1.5f),
            canvasSize = CanvasSize(1200f, 700f),
            miniMapSize = CanvasSize(180f, 112f),
            padding = 8f,
        )
        val worldPoint = Vec2(375f, 240f)

        val restored = projection.miniMapToWorld(projection.worldToMiniMap(worldPoint))

        assertClose(worldPoint.x, restored.x)
        assertClose(worldPoint.y, restored.y)
    }

    @Test
    fun viewportRectangleReflectsZoom() {
        val content = CanvasObjectBounds(Vec2.Zero, Vec2(1000f, 1000f))
        val canvas = CanvasSize(500f, 500f)
        val miniMap = CanvasSize(120f, 120f)
        val zoomedOut = createMiniMapProjection(content, Viewport(zoom = 1f), canvas, miniMap, 10f)
        val zoomedIn = createMiniMapProjection(content, Viewport(zoom = 2f), canvas, miniMap, 10f)

        val zoomedOutWidth = zoomedOut.viewportRect.bottomRight.x - zoomedOut.viewportRect.topLeft.x
        val zoomedInWidth = zoomedIn.viewportRect.bottomRight.x - zoomedIn.viewportRect.topLeft.x

        assertTrue(zoomedInWidth < zoomedOutWidth)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.001f, "Expected $expected, got $actual")
    }
}
