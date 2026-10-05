package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.containsVectorWorldPoint
import cg.creamgod.boarderless.feature.canvas.vectorBoundaryWorldPoint
import kotlin.math.sqrt
import kotlin.test.*

class VectorPathGeometryTest {
    private val size = CanvasSize(100f, 100f)
    private fun rectangle(low: Float, high: Float) = listOf(
        VectorPathCommand.Move(Vec2(low, low)), VectorPathCommand.Line(Vec2(high, low)),
        VectorPathCommand.Line(Vec2(high, high)), VectorPathCommand.Line(Vec2(low, high)), VectorPathCommand.Close)

    @Test fun fillRulesDistinguishHolesAndNeverUseRectangularBoundingBox() {
        val path = VectorPath(size, rectangle(0f, 100f) + rectangle(30f, 70f), VectorPathStyle(fillRule = VectorFillRule.EvenOdd))
        assertTrue(path.containsFill(Vec2(10f, 10f)))
        assertFalse(path.containsFill(Vec2(50f, 50f)))
        assertTrue(path.copy(style = path.style.copy(fillRule = VectorFillRule.NonZero)).containsFill(Vec2(50f, 50f)))
        val reversed = listOf(VectorPathCommand.Move(Vec2(30f, 30f)), VectorPathCommand.Line(Vec2(30f, 70f)),
            VectorPathCommand.Line(Vec2(70f, 70f)), VectorPathCommand.Line(Vec2(70f, 30f)), VectorPathCommand.Close)
        assertFalse(VectorPath(size, rectangle(0f, 100f) + reversed).containsFill(Vec2(50f, 50f)))
        assertFalse(path.containsFill(Vec2(110f, 50f)))
        val triangle = VectorPath(size, listOf(VectorPathCommand.Move(Vec2(0f, 0f)),
            VectorPathCommand.Line(Vec2(100f, 0f)), VectorPathCommand.Line(Vec2(0f, 100f)), VectorPathCommand.Close))
        assertFalse(triangle.containsFill(Vec2(90f, 90f)))
        assertTrue(triangle.containsFill(Vec2(20f, 20f)))
    }

    @Test fun openFillClosesImplicitlyButStrokeDoesNotAndRoundCapsArePickable() {
        val path = VectorPath(size, listOf(VectorPathCommand.Move(Vec2(10f, 10f)),
            VectorPathCommand.Line(Vec2(90f, 10f)), VectorPathCommand.Line(Vec2(90f, 90f))), VectorPathStyle(strokeWidth = 4f))
        assertTrue(path.containsFill(Vec2(70f, 30f)))
        assertFalse(path.containsStroke(Vec2(50f, 50f))) // No implicit diagonal stroke.
        assertTrue(path.containsStroke(Vec2(8f, 10f)))
        assertFalse(path.containsStroke(Vec2(5f, 10f)))
        assertFalse(path.copy(style = path.style.copy(fillColorToken = null)).containsFill(Vec2(70f, 30f)))
    }

    @Test fun adaptiveCurvesStayWithinErrorAndPreserveCollinearBacktracking() {
        val a = Vec2.Zero; val b = Vec2(0f, 100f); val c = Vec2(100f, 100f); val d = Vec2(100f, 0f)
        val path = VectorPath(size, listOf(VectorPathCommand.Move(a), VectorPathCommand.Cubic(b, c, d)))
        val points = path.flatten(0.25f).single().points
        assertTrue(points.size > 2)
        repeat(101) { index ->
            val t = index / 100f; val u = 1f - t
            val sample = a * (u * u * u) + b * (3 * u * u * t) + c * (3 * u * t * t) + d * (t * t * t)
            assertTrue(points.zipWithNext().minOf { (from, to) -> distance(sample, from, to) } <= .251)
        }
        val quadratic = VectorPath(size, listOf(VectorPathCommand.Move(a), VectorPathCommand.Quadratic(Vec2(50f, 100f), d)))
        assertTrue(quadratic.flatten().single().points.any { it.y >= 49.75f })
        val backtracking = VectorPath(size, listOf(VectorPathCommand.Move(a), VectorPathCommand.Cubic(Vec2(200f, 0f), Vec2(-100f, 0f), d)))
        assertTrue(backtracking.flatten().single().points.size > 2)
    }

    @Test fun rayFindsFirstBoundaryFromHoleOutsideAndCollinearOrigin() {
        val path = VectorPath(size, rectangle(0f, 100f) + rectangle(30f, 70f), VectorPathStyle(fillRule = VectorFillRule.EvenOdd))
        assertEquals(Vec2(70f, 50f), path.firstBoundaryAlongRay(Vec2(50f, 50f), Vec2(1f, 0f)))
        assertEquals(Vec2(0f, 50f), path.firstBoundaryAlongRay(Vec2(-10f, 50f), Vec2(1f, 0f)))
        assertEquals(Vec2(20f, 0f), path.firstBoundaryAlongRay(Vec2(20f, 0f), Vec2(1f, 0f)))
        assertNull(path.firstBoundaryAlongRay(Vec2(-10f, 50f), Vec2(-1f, 0f)))
        assertNull(path.firstBoundaryAlongRay(Vec2(50f, 50f), Vec2.Zero))
    }

    @Test fun flattenPreservesContoursAndDegenerateSegmentsWithoutInventedClosingStroke() {
        val path = VectorPath(size, rectangle(0f, 100f) + listOf(VectorPathCommand.Move(Vec2(40f, 40f)),
            VectorPathCommand.Line(Vec2(40f, 40f))))
        val contours = path.flatten()
        assertEquals(2, contours.size)
        assertTrue(contours[0].closed)
        assertFalse(contours[1].closed)
        assertEquals(contours[0].points.first(), contours[0].points.last())
        assertTrue(path.containsStroke(Vec2(40f, 40f)))
    }

    @Test fun rejectsInvalidToleranceAndExcessiveApproximationInsteadOfUsingABox() {
        val path = VectorPath(size, rectangle(0f, 100f))
        listOf(Float.NaN, 0f, -1f, 1001f).forEach { assertFails { path.flatten(it) } }
        val crowded = VectorPath(size, listOf(VectorPathCommand.Move(Vec2.Zero)) + List(4095) {
            VectorPathCommand.Cubic(Vec2(100f, 100f), Vec2(-100f, 100f), Vec2.Zero)
        })
        assertFails { crowded.flatten(.01f) }
    }

    @Test fun worldHitAndRayRespectRotationAndNonUniformScaling() {
        val triangle = VectorPath(size, listOf(VectorPathCommand.Move(Vec2.Zero), VectorPathCommand.Line(Vec2(100f, 0f)),
            VectorPathCommand.Line(Vec2(0f, 100f)), VectorPathCommand.Close))
        val transform = CanvasTransform(Vec2(10f, 20f), CanvasSize(200f, 100f), rotationDegrees = 90f)
        assertTrue(transform.containsVectorWorldPoint(triangle, Vec2(140f, 10f)))
        assertFalse(transform.containsVectorWorldPoint(triangle, Vec2(70f, 150f)))
        val rectangle = VectorPath(size, rectangle(0f, 100f))
        assertEquals(Vec2(110f, 170f), vectorBoundaryWorldPoint(transform, rectangle, Vec2(110f, 270f)))
        val collapsed = transform.copy(size = CanvasSize(0f, 100f))
        assertFalse(collapsed.containsVectorWorldPoint(rectangle, Vec2(10f, 20f)))
        assertNull(vectorBoundaryWorldPoint(collapsed, rectangle, Vec2(10f, 20f)))
    }

    private fun distance(p: Vec2, a: Vec2, b: Vec2): Double {
        val dx = b.x.toDouble() - a.x; val dy = b.y.toDouble() - a.y
        val square = dx * dx + dy * dy
        val t = if (square == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / square).coerceIn(0.0, 1.0)
        val x = p.x - a.x - dx * t; val y = p.y - a.y - dy * t
        return sqrt(x * x + y * y)
    }
}
