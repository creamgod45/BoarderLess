package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class SelfLoopRelationRoutingTest {
    private val transform = CanvasTransform(Vec2(100f, 200f), CanvasSize(160f, 100f))

    private fun node(shape: NodeShape = NodeShape.Rectangle) =
        TextNode(id = CanvasObjectId("loop"), transform = transform, text = "loop", shape = shape)

    private fun near(
        expected: Vec2,
        actual: Vec2,
    ) {
        assertEquals(expected.x, actual.x, 0.002f)
        assertEquals(expected.y, actual.y, 0.002f)
    }

    @Test fun allShapesAndSidesProduceSeparateBoundaryPortsAndAnOutsideRun() {
        for (shape in NodeShape.entries) {
            for (side in RelationLoopSide.entries) {
                val route = selfLoopRelationRoute(node(shape), transform, side)
                assertEquals(4, route.size)
                assertNotEquals(route.first(), route.last(), "$shape/$side")
                assertTrue(route.zipWithNext().all { (a, b) -> a != b })
                val outerMid = (route[1] + route[2]) / 2f
                assertFalse(transform.containsWorldPoint(outerMid, NodeShape.Rectangle))
                assertEquals(0f, distanceToPolyline(outerMid, route), 0.002f)
                assertTrue(distanceToPolyline(transform.position + Vec2(80f, 50f), route) > 1f)
                route.zipWithNext().forEach { (a, b) ->
                    for (step in 1..19) {
                        assertFalse(
                            transform.containsWorldPoint(a + (b - a) * (step / 20f), shape),
                            "Loop segment enters $shape/$side at $step",
                        )
                    }
                }
                for (port in listOf(route.first(), route.last())) {
                    // Use the actual shape predicate, not the centre-ray port chooser: concave
                    // shapes can have an outside-facing boundary invisible from the centre.
                    assertTrue(transform.containsWorldPoint(port - side.outward * 0.1f, shape), "$shape/$side inner port")
                    assertFalse(transform.containsWorldPoint(port + side.outward * 0.1f, shape), "$shape/$side outer port")
                }
            }
        }
    }

    @Test fun movingAndRotatingRecomputesTheWholeLoopAboutNodeCentre() {
        val original = selfLoopRelationRoute(node(), transform)
        for (angle in listOf(-90f, 30f, 90f, 180f, 270f)) {
            val moved = transform.copy(position = Vec2(-500f, 620f), rotationDegrees = angle)
            val route = selfLoopRelationRoute(node(), moved)
            val originalCenter = transform.position + Vec2(80f, 50f)
            val newCenter = moved.position + Vec2(80f, 50f)
            original.zip(route).forEach { (old, current) ->
                near(newCenter + rotateVector(old - originalCenter, angle), current)
            }
            assertEquals(0f, distanceToPolyline((route[1] + route[2]) / 2f, route), 0.002f)
        }
    }

    @Test fun resizingMovesPortsAndHonoursExtentRatherThanKeepingStaleCoordinates() {
        val resized = transform.copy(size = CanvasSize(320f, 200f))
        val route = selfLoopRelationRoute(node(), resized, extent = 120f)
        assertEquals(420f, route.first().x, 0.002f)
        assertEquals(540f, route[1].x, 0.002f)
        assertNotEquals(selfLoopRelationRoute(node(), transform), route)
        val label = polylineMidpoint(route)
        assertEquals(0f, distanceToPolyline(label, route), 0.002f)
    }

    @Test fun segmentHitTestingAndArrowTangentsRemainNonzero() {
        val route = selfLoopRelationRoute(node(), transform)
        assertTrue(distanceToPolyline((route[0] + route[1]) / 2f, route) < 0.002f)
        assertTrue(distanceToPolyline((route[2] + route[3]) / 2f, route) < 0.002f)
        assertNotEquals(Vec2.Zero, route[1] - route[0])
        assertNotEquals(Vec2.Zero, route.last() - route[route.lastIndex - 1])
    }

    @Test fun openRetracingAndFilledVectorPathsHaveVisibleLoopsOnEverySide() {
        val paths =
            listOf(
                VectorPath(
                    viewBox = CanvasSize(38f, 8f),
                    commands =
                        listOf(
                            VectorPathCommand.Move(Vec2(4f, 4f)),
                            VectorPathCommand.Quadratic(Vec2(64f, 4f), Vec2(4f, 4f)),
                        ),
                    style = VectorPathStyle(fillColorToken = null, strokeColorToken = "paper", strokeWidth = 8f),
                ),
                VectorPath(
                    viewBox = CanvasSize(100f, 100f),
                    commands =
                        listOf(
                            VectorPathCommand.Move(Vec2(0f, 0f)),
                            VectorPathCommand.Line(Vec2(100f, 0f)),
                            VectorPathCommand.Line(Vec2(0f, 100f)),
                            VectorPathCommand.Close,
                        ),
                ),
            )
        for (path in paths) {
            for (side in RelationLoopSide.entries) {
                val n = node().copy(vectorPath = path)
                val route = selfLoopRelationRoute(n, transform, side)
                assertNotEquals(route.first(), route.last(), "$side")
                assertTrue(route.zipWithNext().all { (a, b) -> a != b }, "$side")
                assertTrue((route[1] - route[2]).let { it.x * it.x + it.y * it.y } > 1f)
                assertFalse(transform.containsWorldPoint((route[1] + route[2]) / 2f, NodeShape.Rectangle))
                for (port in listOf(route.first(), route.last())) {
                    assertTrue(transform.containsVectorWorldPoint(path, port), "$side contour port")
                }
                val rotated = transform.copy(rotationDegrees = 90f, position = Vec2(-400f, 600f))
                val actual = selfLoopRelationRoute(n, rotated, side)
                route.zip(actual).forEach { (a, b) ->
                    near(rotated.position + Vec2(80f, 50f) + rotateVector(a - transform.position - Vec2(80f, 50f), 90f), b)
                }
            }
        }
    }

    @Test fun invalidExtentAndDegenerateNodeFailBeforeRendering() {
        for (extent in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 15f, 4097f)) {
            assertFailsWith<IllegalArgumentException> { selfLoopRelationRoute(node(), transform, extent = extent) }
        }
        for (size in listOf(CanvasSize(0f, 100f), CanvasSize(100f, 0f))) {
            assertFailsWith<IllegalArgumentException> { selfLoopRelationRoute(node(), transform.copy(size = size)) }
        }
    }
}
