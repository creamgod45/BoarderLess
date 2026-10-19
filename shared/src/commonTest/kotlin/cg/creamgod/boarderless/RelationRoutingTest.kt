package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.distanceToPolyline
import cg.creamgod.boarderless.feature.canvas.orthogonalRelationRoute
import cg.creamgod.boarderless.feature.canvas.polylineMidpoint
import cg.creamgod.boarderless.feature.canvas.routeIntersectsTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelationRoutingTest {
    @Test
    fun routeUsesShapePortsAndOnlyOrthogonalSegments() {
        val source = node("source", NodeShape.Rectangle)
        val target = node("target", NodeShape.Diamond)
        val sourceTransform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val targetTransform = CanvasTransform(Vec2(300f, 200f), CanvasSize(100f, 100f))

        val route = orthogonalRelationRoute(source, sourceTransform, target, targetTransform)

        // Kotlin/JS retains intermediate Number precision instead of rounding to Float32.
        assertEquals(100f, route.first().x, 0.00001f)
        assertEquals(40f, route.first().y, 0.00001f)
        assertEquals(300f, route.last().x, 0.00001f)
        assertEquals(250f, route.last().y, 0.00001f)
        assertTrue(route.zipWithNext().all { (start, end) -> start.x == end.x || start.y == end.y })
    }

    @Test
    fun hitTestingAndLabelPositionFollowTheWholePolyline() {
        val route = listOf(Vec2(0f, 0f), Vec2(100f, 0f), Vec2(100f, 100f))

        assertEquals(Vec2(100f, 0f), polylineMidpoint(route))
        assertTrue(distanceToPolyline(Vec2(98f, 70f), route) < 3f)
        assertTrue(distanceToPolyline(Vec2(50f, 50f), route) > 40f)
    }

    @Test
    fun routeDetoursAroundAnIntermediateNode() {
        val source = node("source", NodeShape.Rectangle)
        val target = node("target", NodeShape.Rectangle)
        val sourceTransform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val targetTransform = CanvasTransform(Vec2(400f, 0f), CanvasSize(100f, 80f))
        val obstacle = CanvasTransform(Vec2(200f, -20f), CanvasSize(100f, 120f))

        val route =
            orthogonalRelationRoute(
                source = source,
                sourceTransform = sourceTransform,
                target = target,
                targetTransform = targetTransform,
                obstacles = listOf(obstacle),
                clearance = 20f,
            )

        assertTrue(route.size >= 4)
        assertTrue(route.zipWithNext().all { (start, end) -> start.x == end.x || start.y == end.y })
        assertTrue(!routeIntersectsTransform(route, obstacle, clearance = 20f))
    }

    @Test
    fun routeUsesTheRotatedObstacleBounds() {
        val source = node("source", NodeShape.Ellipse)
        val target = node("target", NodeShape.Hexagon)
        val sourceTransform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val targetTransform = CanvasTransform(Vec2(400f, 0f), CanvasSize(100f, 80f))
        val rotatedObstacle =
            CanvasTransform(
                position = Vec2(210f, -20f),
                size = CanvasSize(80f, 120f),
                rotationDegrees = 45f,
            )

        val route =
            orthogonalRelationRoute(
                source = source,
                sourceTransform = sourceTransform,
                target = target,
                targetTransform = targetTransform,
                obstacles = listOf(rotatedObstacle),
                clearance = 16f,
            )

        assertTrue(!routeIntersectsTransform(route, rotatedObstacle, clearance = 16f))
    }

    private fun node(
        id: String,
        shape: NodeShape,
    ) = TextNode(
        id = CanvasObjectId(id),
        transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)),
        text = id,
        shape = shape,
    )
}
