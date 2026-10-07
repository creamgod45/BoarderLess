package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.SelectionAlignment
import cg.creamgod.boarderless.feature.canvas.alignNodeTransforms
import cg.creamgod.boarderless.feature.canvas.rotatedTransformCorners
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionAlignmentTest {
    private val nodes = listOf(
        node("first", 0f, 100f, 140f, 80f, 20f),
        node("second", 360f, 380f, 100f, 160f, -15f),
        node("third", 760f, 40f, 180f, 100f, 30f),
    )

    @Test
    fun horizontalModesAlignRotatedVisualBoundsAndPreserveVerticalPositions() {
        listOf(
            SelectionAlignment.Left to { bounds: VisualBounds -> bounds.left },
            SelectionAlignment.HorizontalCenter to { bounds: VisualBounds -> bounds.centerX },
            SelectionAlignment.Right to { bounds: VisualBounds -> bounds.right },
        ).forEach { (alignment, anchor) ->
            val result = alignNodeTransforms(nodes, alignment)
            val anchors = nodes.map { anchor(bounds(result.getValue(it.id))) }
            assertTrue(anchors.all { value -> abs(value - anchors.first()) < 0.01f })
            nodes.forEach { node ->
                assertEquals(node.transform.position.y, result.getValue(node.id).position.y)
                assertEquals(node.transform.rotationDegrees, result.getValue(node.id).rotationDegrees)
            }
        }
    }

    @Test
    fun verticalModesAlignRotatedVisualBoundsAndPreserveHorizontalPositions() {
        listOf(
            SelectionAlignment.Top to { bounds: VisualBounds -> bounds.top },
            SelectionAlignment.VerticalCenter to { bounds: VisualBounds -> bounds.centerY },
            SelectionAlignment.Bottom to { bounds: VisualBounds -> bounds.bottom },
        ).forEach { (alignment, anchor) ->
            val result = alignNodeTransforms(nodes, alignment)
            val anchors = nodes.map { anchor(bounds(result.getValue(it.id))) }
            assertTrue(anchors.all { value -> abs(value - anchors.first()) < 0.01f })
            nodes.forEach { node ->
                assertEquals(node.transform.position.x, result.getValue(node.id).position.x)
            }
        }
    }

    private data class VisualBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    private fun bounds(transform: CanvasTransform): VisualBounds {
        val corners = rotatedTransformCorners(transform)
        return VisualBounds(
            corners.minOf(Vec2::x),
            corners.minOf(Vec2::y),
            corners.maxOf(Vec2::x),
            corners.maxOf(Vec2::y),
        )
    }

    private fun node(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
    ) = TextNode(
        id = CanvasObjectId(id),
        transform = CanvasTransform(Vec2(x, y), CanvasSize(width, height), rotation),
        text = id,
    )
}
