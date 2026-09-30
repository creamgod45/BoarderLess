package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import cg.creamgod.boarderless.feature.canvas.canvasObjectBounds
import cg.creamgod.boarderless.feature.canvas.containsWorldPoint
import cg.creamgod.boarderless.feature.canvas.groupFrameTransform
import cg.creamgod.boarderless.feature.canvas.fittedGroupFrameTransform
import cg.creamgod.boarderless.feature.canvas.rotatedTransformCorners
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewportTest {
    @Test
    fun worldAndScreenCoordinatesRoundTrip() {
        val viewport = Viewport(pan = Vec2(180f, -40f), zoom = 1.75f)
        val world = Vec2(-320f, 890f)

        val restored = viewport.screenToWorld(viewport.worldToScreen(world))

        assertClose(world.x, restored.x)
        assertClose(world.y, restored.y)
    }

    @Test
    fun zoomKeepsThePointerOverTheSameWorldPoint() {
        val viewport = Viewport(pan = Vec2(90f, 120f), zoom = 0.8f)
        val pointer = Vec2(640f, 360f)
        val worldBefore = viewport.screenToWorld(pointer)

        val zoomed = viewport.zoomAt(pointer, factor = 1.6f)
        val worldAfter = zoomed.screenToWorld(pointer)

        assertClose(worldBefore.x, worldAfter.x)
        assertClose(worldBefore.y, worldAfter.y)
    }

    @Test
    fun fitCentersContentInsidePaddedScreen() {
        val fitted = Viewport().fit(
            worldTopLeft = Vec2(100f, 50f),
            worldBottomRight = Vec2(500f, 250f),
            screenSize = CanvasSize(1000f, 600f),
            padding = 100f,
        )

        assertClose(2f, fitted.zoom)
        assertClose(100f, fitted.worldToScreen(Vec2(100f, 50f)).x)
        assertClose(100f, fitted.worldToScreen(Vec2(100f, 50f)).y)
        assertClose(900f, fitted.worldToScreen(Vec2(500f, 250f)).x)
        assertClose(500f, fitted.worldToScreen(Vec2(500f, 250f)).y)
    }

    @Test
    fun canvasBoundsIncludeRotatedCorners() {
        val rotated = TextNode(
            id = CanvasObjectId("rotated"),
            transform = CanvasTransform(
                position = Vec2(100f, 100f),
                size = CanvasSize(200f, 100f),
                rotationDegrees = 90f,
            ),
            text = "Rotated",
        )

        val bounds = canvasObjectBounds(listOf(rotated))

        requireNotNull(bounds)
        assertClose(150f, bounds.topLeft.x)
        assertClose(50f, bounds.topLeft.y)
        assertClose(250f, bounds.bottomRight.x)
        assertClose(250f, bounds.bottomRight.y)
    }

    @Test
    fun canvasBoundsIncludeGroupFramesAndTextNodes() {
        val group = GroupFrame(
            id = CanvasObjectId("group"),
            transform = CanvasTransform(Vec2(-400f, -200f), CanvasSize(300f, 250f)),
        )
        val node = TextNode(
            id = CanvasObjectId("node"),
            transform = CanvasTransform(Vec2(600f, 450f), CanvasSize(200f, 100f)),
            text = "Far away",
        )

        val bounds = canvasObjectBounds(listOf(group, node))

        assertEquals(Vec2(-400f, -200f), bounds?.topLeft)
        assertEquals(Vec2(800f, 550f), bounds?.bottomRight)
    }

    @Test
    fun emptyCanvasHasNoContentBounds() {
        assertNull(canvasObjectBounds(emptyList()))
    }

    @Test
    fun groupFrameContainsRotatedContentWithPadding() {
        val rotated = TextNode(
            id = CanvasObjectId("rotated"),
            transform = CanvasTransform(
                position = Vec2(100f, 100f),
                size = CanvasSize(200f, 100f),
                rotationDegrees = 90f,
            ),
            text = "Rotated",
        )

        val frame = groupFrameTransform(listOf(rotated), padding = 40f)

        assertEquals(Vec2(110f, 10f), frame?.position)
        assertEquals(CanvasSize(180f, 280f), frame?.size)
    }

    @Test
    fun emptySelectionCannotCreateAGroupFrame() {
        assertNull(groupFrameTransform(emptyList(), padding = 40f))
    }

    @Test
    fun fittingARotatedGroupPreservesItsAngleAndContainsRotatedDescendants() {
        val group = GroupFrame(
            id = CanvasObjectId("group"),
            transform = CanvasTransform(
                position = Vec2(0f, 0f),
                size = CanvasSize(100f, 100f),
                rotationDegrees = 30f,
            ),
        )
        val first = TextNode(
            id = CanvasObjectId("first"),
            transform = CanvasTransform(Vec2(200f, 120f), CanvasSize(180f, 80f), rotationDegrees = 20f),
            text = "First",
        )
        val second = TextNode(
            id = CanvasObjectId("second"),
            transform = CanvasTransform(Vec2(460f, 300f), CanvasSize(100f, 160f), rotationDegrees = -15f),
            text = "Second",
        )

        val fitted = fittedGroupFrameTransform(group, listOf(first, second), padding = 40f)!!

        assertEquals(30f, fitted.rotationDegrees)
        listOf(first, second).flatMap { rotatedTransformCorners(it.transform) }.forEach { corner ->
            assertTrue(fitted.containsWorldPoint(corner, cg.creamgod.boarderless.domain.model.NodeShape.Rectangle))
        }
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.001f, "Expected $expected, got $actual")
    }
}
