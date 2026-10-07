package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.connectorDropTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectorTargetingTest {
    @Test
    fun sourceNodeCannotTargetItself() {
        val source = node("source", 0, CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)))

        assertNull(connectorDropTargetId(listOf(source), source.id, Vec2(50f, 40f)))
    }

    @Test
    fun rotatedTargetUsesItsVisibleShape() {
        val source = node("source", 0, CanvasTransform(Vec2(-200f, 0f), CanvasSize(100f, 80f)))
        val target =
            node(
                "target",
                1,
                CanvasTransform(Vec2(100f, 100f), CanvasSize(200f, 100f), rotationDegrees = 90f),
            )

        assertEquals(
            target.id,
            connectorDropTargetId(listOf(source, target), source.id, Vec2(160f, 60f)),
        )
        assertNull(connectorDropTargetId(listOf(source, target), source.id, Vec2(110f, 110f)))
    }

    @Test
    fun overlappingTargetsChooseTheFrontmostNode() {
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val source = node("source", 0, CanvasTransform(Vec2(-200f, 0f), CanvasSize(100f, 80f)))
        val back = node("back", 2, transform)
        val front = node("front", 9, transform)

        assertEquals(
            front.id,
            connectorDropTargetId(listOf(source, front, back), source.id, Vec2(50f, 40f)),
        )
    }

    private fun node(
        id: String,
        zIndex: Long,
        transform: CanvasTransform,
    ) = TextNode(
        id = CanvasObjectId(id),
        zIndex = zIndex,
        transform = transform,
        text = id,
    )
}
