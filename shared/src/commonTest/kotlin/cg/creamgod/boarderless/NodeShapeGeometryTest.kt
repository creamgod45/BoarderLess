package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.connectorDropTargetId
import cg.creamgod.boarderless.feature.canvas.containsLocalPoint
import cg.creamgod.boarderless.feature.canvas.shapeBoundaryWorldPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeShapeGeometryTest {
    private val source = TextNode(
        id = CanvasObjectId("source"),
        transform = CanvasTransform(Vec2(-200f, 0f), CanvasSize(100f, 80f)),
        text = "Source",
    )

    @Test
    fun connectorTargetingRespectsNonRectangularSilhouettes() {
        val diamond = TextNode(
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

        val boundary = shapeBoundaryWorldPoint(
            transform = transform,
            shape = NodeShape.Diamond,
            toward = Vec2(300f, 150f),
        )

        assertTrue(kotlin.math.abs(boundary.x - 150f) < 0.01f)
        assertTrue(kotlin.math.abs(boundary.y - 75f) < 0.01f)
    }

    @Test
    fun everyShapeTokenIsStableAndUnique() {
        assertEquals(NodeShape.entries.size, NodeShape.entries.map(NodeShape::token).distinct().size)
        NodeShape.entries.forEach { shape -> assertEquals(shape, NodeShape.fromToken(shape.token)) }
        assertNull(NodeShape.fromToken("future-shape"))
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
