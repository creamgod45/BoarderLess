package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.connectorDropTargetId
import cg.creamgod.boarderless.feature.canvas.containsLocalPoint
import cg.creamgod.boarderless.feature.canvas.polygonVertices
import cg.creamgod.boarderless.feature.canvas.rotateVector
import cg.creamgod.boarderless.feature.canvas.shapeBoundaryWorldPoint
import cg.creamgod.boarderless.feature.canvas.shapeConnectionOriginWorldPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeShapeGeometryTest {
    private val source =
        TextNode(
            id = CanvasObjectId("source"),
            transform = CanvasTransform(Vec2(-200f, 0f), CanvasSize(100f, 80f)),
            text = "Source",
        )

    @Test
    fun connectorTargetingRespectsNonRectangularSilhouettes() {
        val diamond =
            TextNode(
                id = CanvasObjectId("diamond"),
                zIndex = 1,
                transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 100f)),
                text = "Decision",
                shape = NodeShape.Diamond,
            )

        assertEquals(
            diamond.id,
            connectorDropTargetId(listOf(source, diamond), source.id, Vec2(50f, 50f)),
        )
        assertNull(connectorDropTargetId(listOf(source, diamond), source.id, Vec2(8f, 8f)))
    }

    @Test
    fun relationBoundaryFollowsDiamondEdge() {
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(200f, 100f))

        val boundary =
            shapeBoundaryWorldPoint(
                transform = transform,
                shape = NodeShape.Diamond,
                toward = Vec2(300f, 150f),
            )

        assertTrue(kotlin.math.abs(boundary.x - 150f) < 0.01f)
        assertTrue(kotlin.math.abs(boundary.y - 75f) < 0.01f)
    }

    @Test
    fun everyShapeTokenIsStableAndUnique() {
        assertEquals(
            NodeShape.entries.size,
            NodeShape.entries
                .map(NodeShape::token)
                .distinct()
                .size,
        )
        NodeShape.entries.forEach { shape -> assertEquals(shape, NodeShape.fromToken(shape.token)) }
        assertNull(NodeShape.fromToken("future-shape"))
    }

    @Test fun ownedPolygonBoundariesRemainOnVisibleSilhouetteAfterRotation() {
        NodeShape.entries.filter { it.polygonVertices() != null }.forEach { shape ->
            assertTrue(shape.containsLocalPoint(Vec2.Zero, 100f, 50f), shape.token)
            shape.polygonVertices()!!.forEach { point ->
                assertTrue(shape.containsLocalPoint(Vec2((point.x - .5f) * 200f, (point.y - .5f) * 100f), 100f, 50f), shape.token)
            }
            listOf(0f, 37f, 90f, 180f).forEach { rotation ->
                val transform = CanvasTransform(Vec2(100f, -40f), CanvasSize(200f, 100f), rotation)
                val center = transform.position + Vec2(100f, 50f)
                val origin = shapeConnectionOriginWorldPoint(transform, shape)
                val localOrigin = rotateVector(origin - center, -rotation)
                listOf(Vec2(500f, 0f), Vec2(100f, 500f), Vec2(-500f, 300f), Vec2(-300f, -500f)).forEach { ray ->
                    val boundary = shapeBoundaryWorldPoint(transform, shape, origin + rotateVector(ray, rotation))
                    val local = rotateVector(boundary - origin, -rotation)
                    assertTrue(shape.containsLocalPoint(localOrigin + local * .999f, 100f, 50f), "$shape $rotation inside")
                    assertTrue(!shape.containsLocalPoint(localOrigin + local * 1.001f, 100f, 50f), "$shape $rotation outside")
                }
            }
        }
    }

    @Test fun concaveArrowUsesNearestExitRatherThanReenteringHead() {
        val boundary =
            shapeBoundaryWorldPoint(
                CanvasTransform(Vec2.Zero, CanvasSize(200f, 100f)),
                NodeShape.ArrowRight,
                Vec2(160f, 150f),
            )
        assertEquals(115f, boundary.x, .01f)
        assertEquals(75f, boundary.y, .01f)
        assertTrue(!NodeShape.Plus.containsLocalPoint(Vec2(-90f, -40f), 100f, 50f))
        assertTrue(!NodeShape.Triangle.containsLocalPoint(Vec2(-90f, -40f), 100f, 50f))
    }

    @Test fun everyShapeRejectsOutsideBoundsAndInvalidCoordinates() {
        NodeShape.entries.forEach { shape ->
            assertTrue(!shape.containsLocalPoint(Vec2(0f, 101f), 100f, 100f), shape.token)
            assertTrue(!shape.containsLocalPoint(Vec2.Zero, Float.POSITIVE_INFINITY, 100f), shape.token)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Vec2(Float.NaN, 0f) }
        assertTrue(NodeShape.PlainText.containsLocalPoint(Vec2(99f, 49f), 100f, 50f))
    }

    @Test fun rightTriangleUsesInteriorOriginAndNonzeroExitAfterRotationAndResize() {
        for (size in listOf(CanvasSize(200f, 100f), CanvasSize(40f, 400f), CanvasSize(400f, 40f))) {
            for (rotation in listOf(0f, 37f, 90f, 180f)) {
                val transform = CanvasTransform(Vec2(15f, -20f), size, rotation)
                val center = transform.position + Vec2(size.width / 2f, size.height / 2f)
                val origin = shapeConnectionOriginWorldPoint(transform, NodeShape.RightTriangle)
                val expectedLocal = Vec2(-size.width / 6f, size.height / 6f)
                val local = rotateVector(origin - center, -rotation)
                assertEquals(expectedLocal.x, local.x, .001f)
                assertEquals(expectedLocal.y, local.y, .001f)
                val boundary =
                    shapeBoundaryWorldPoint(
                        transform,
                        NodeShape.RightTriangle,
                        origin + rotateVector(Vec2(-1000f, 0f), rotation),
                    )
                val boundaryLocal = rotateVector(boundary - center, -rotation)
                assertEquals(-size.width / 2f, boundaryLocal.x, .001f)
                assertEquals(size.height / 6f, boundaryLocal.y, .001f)
                assertEquals(origin, shapeBoundaryWorldPoint(transform, NodeShape.RightTriangle, origin))
            }
        }
    }

    @Test fun newConcaveAndFlowOutlinesRejectTheirTransparentRegions() {
        assertTrue(!NodeShape.Chevron.containsLocalPoint(Vec2(-90f, 0f), 100f, 50f))
        assertTrue(!NodeShape.DoubleArrow.containsLocalPoint(Vec2(0f, -45f), 100f, 50f))
        assertTrue(!NodeShape.Star.containsLocalPoint(Vec2(90f, 45f), 100f, 50f))
        assertTrue(!NodeShape.ManualInput.containsLocalPoint(Vec2(-90f, -45f), 100f, 50f))
        assertTrue(!NodeShape.RightTriangle.containsLocalPoint(Vec2(90f, -45f), 100f, 50f))
        assertTrue(!NodeShape.TriangleDown.containsLocalPoint(Vec2(90f, 45f), 100f, 50f))
    }

    @Test
    fun documentHitTestingFollowsTheWavyBottomEdge() {
        assertTrue(NodeShape.Document.containsLocalPoint(Vec2(-50f, 70f), 100f, 100f))
        assertTrue(!NodeShape.Document.containsLocalPoint(Vec2(50f, 70f), 100f, 100f))
    }

    @Test
    fun databaseHitTestingFollowsTheCylinderCaps() {
        assertTrue(NodeShape.Database.containsLocalPoint(Vec2(0f, -95f), 100f, 100f))
        assertTrue(NodeShape.Database.containsLocalPoint(Vec2(95f, 0f), 100f, 100f))
        assertTrue(!NodeShape.Database.containsLocalPoint(Vec2(95f, -95f), 100f, 100f))
    }
}
