package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.DistributionAxis
import cg.creamgod.boarderless.feature.canvas.distributeNodeTransforms
import cg.creamgod.boarderless.feature.canvas.rotatedTransformCorners
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionDistributionTest {
    @Test
    fun horizontalDistributionKeepsVisualEndpointsAndEqualizesRotatedGaps() {
        val first = node("first", 0f, 100f, 120f, 80f, 20f, 0)
        val middle = node("middle", 420f, 40f, 180f, 100f, -15f, 1)
        val last = node("last", 900f, 200f, 100f, 140f, 30f, 2)
        val result = distributeNodeTransforms(listOf(last, middle, first), DistributionAxis.Horizontal)

        val beforeFirst = horizontalBounds(first.transform)
        val beforeLast = horizontalBounds(last.transform)
        val after = listOf(first, middle, last).map { horizontalBounds(result.getValue(it.id)) }

        assertClose(beforeFirst.first, after.first().first)
        assertClose(beforeLast.second, after.last().second)
        assertClose(after[1].first - after[0].second, after[2].first - after[1].second)
        assertEquals(middle.transform.rotationDegrees, result.getValue(middle.id).rotationDegrees)
    }

    @Test
    fun verticalDistributionNeedsAtLeastThreeNodes() {
        val first = node("first", 0f, 0f, 100f, 80f, 0f, 0)
        val second = node("second", 200f, 200f, 100f, 80f, 0f, 1)

        assertEquals(
            mapOf(first.id to first.transform, second.id to second.transform),
            distributeNodeTransforms(listOf(first, second), DistributionAxis.Vertical),
        )
    }

    @Test
    fun verticalDistributionEqualizesVisualGapsWithoutChangingHorizontalPositions() {
        val first = node("first", 80f, 0f, 140f, 80f, -10f, 0)
        val middle = node("middle", 320f, 420f, 100f, 160f, 25f, 1)
        val last = node("last", 40f, 920f, 180f, 100f, 15f, 2)

        val result = distributeNodeTransforms(listOf(middle, last, first), DistributionAxis.Vertical)
        val after = listOf(first, middle, last).map { verticalBounds(result.getValue(it.id)) }

        assertClose(after[1].first - after[0].second, after[2].first - after[1].second)
        assertEquals(middle.transform.position.x, result.getValue(middle.id).position.x)
    }

    private fun horizontalBounds(transform: CanvasTransform): Pair<Float, Float> {
        val corners = rotatedTransformCorners(transform)
        return corners.minOf(Vec2::x) to corners.maxOf(Vec2::x)
    }

    private fun verticalBounds(transform: CanvasTransform): Pair<Float, Float> {
        val corners = rotatedTransformCorners(transform)
        return corners.minOf(Vec2::y) to corners.maxOf(Vec2::y)
    }

    private fun node(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
        z: Long,
    ) = TextNode(
        id = CanvasObjectId(id),
        zIndex = z,
        transform = CanvasTransform(Vec2(x, y), CanvasSize(width, height), rotation),
        text = id,
    )

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "Expected $expected, got $actual")
    }
}
