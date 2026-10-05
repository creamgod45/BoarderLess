package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.viewportImageRequest
import kotlin.test.*

class ViewportImageRegionTest {
    @Test fun pannedViewportRequestsOnlyVisibleImageHalf() {
        val request = assertNotNull(viewportImageRequest(CanvasTransform(Vec2.Zero, CanvasSize(1000f, 500f)),
            Viewport(pan = Vec2(-500f, 0f)), 500, 500, 4000, 2000))
        assertEquals(.5f, request.left)
        assertEquals(1f, request.right)
        assertEquals(1000, request.displayWidth)
    }
    @Test fun letterboxSpaceDoesNotRequestPixels() {
        assertNull(viewportImageRequest(CanvasTransform(Vec2(0f, 100f), CanvasSize(1000f, 1000f)),
            Viewport(), 1000, 200, 4000, 2000))
    }
    @Test fun rotatedVisibleNodeIsRequestedButOffscreenNodeIsNot() {
        val node = CanvasTransform(Vec2(100f, 100f), CanvasSize(200f, 100f), rotationDegrees = 90f)
        val request = assertNotNull(viewportImageRequest(node, Viewport(), 500, 500, 4000, 2000))
        assertEquals(0f, request.left)
        assertEquals(1f, request.right)
        assertNull(viewportImageRequest(node.copy(position = Vec2(2000f, 2000f)), Viewport(), 500, 500, 4000, 2000))
    }
    @Test fun zoomChangesDecodeResolutionWithoutChangingFullVisibleRegion() {
        val node = CanvasTransform(Vec2.Zero, CanvasSize(200f, 100f))
        val request = assertNotNull(viewportImageRequest(node, Viewport(zoom = 2f), 500, 500, 4000, 2000))
        assertEquals(400, request.displayWidth)
        assertEquals(200, request.displayHeight)
    }
}
