package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.DiagramLayoutMode
import cg.creamgod.boarderless.feature.canvas.autoLayoutNodeTransforms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.math.sqrt

class DiagramAutoLayoutTest {
    @Test
    fun horizontalFlowFollowsRelationDirectionInsteadOfOriginalPosition() {
        val end = node("end", x = 20f, y = 400f, z = 0)
        val start = node("start", x = 500f, y = 0f, z = 2)
        val process = node("process", x = 200f, y = 300f, z = 1)
        val relations = listOf(
            relation("start", "process"),
            relation("process", "end"),
        )

        val result = autoLayoutNodeTransforms(
            nodes = listOf(end, start, process),
            relations = relations,
            mode = DiagramLayoutMode.HorizontalFlow,
            horizontalGap = 40f,
            verticalGap = 60f,
        )

        assertEquals(20f, result.getValue(start.id).position.x)
        assertEquals(160f, result.getValue(process.id).position.x)
        assertEquals(300f, result.getValue(end.id).position.x)
        assertEquals(start.transform.size, result.getValue(start.id).size)
    }

    @Test
    fun treeLayoutPlacesBranchesOnTheSameLevel() {
        val root = node("root", 300f, 200f, 0)
        val left = node("left", 0f, 0f, 1)
        val right = node("right", 600f, 500f, 2)

        val result = autoLayoutNodeTransforms(
            nodes = listOf(root, left, right),
            relations = listOf(relation("root", "left"), relation("root", "right")),
            mode = DiagramLayoutMode.VerticalTree,
            horizontalGap = 40f,
            verticalGap = 60f,
        )

        assertEquals(result.getValue(left.id).position.y, result.getValue(right.id).position.y)
        assertTrue(result.getValue(root.id).position.y < result.getValue(left.id).position.y)
        assertTrue(result.getValue(root.id).position.x > result.getValue(left.id).position.x)
        assertTrue(result.getValue(root.id).position.x < result.getValue(right.id).position.x)
    }

    @Test
    fun backwardRelationReversesTheHierarchyEdge() {
        val first = node("first", 0f, 0f, 0)
        val second = node("second", 200f, 200f, 1)
        val backward = relation("first", "second").copy(direction = RelationDirection.Backward)

        val result = autoLayoutNodeTransforms(
            nodes = listOf(first, second),
            relations = listOf(backward),
            mode = DiagramLayoutMode.VerticalTree,
            horizontalGap = 40f,
            verticalGap = 60f,
        )

        assertTrue(result.getValue(second.id).position.y < result.getValue(first.id).position.y)
    }

    @Test
    fun radialLayoutCentersTheMostConnectedNodeAndSpacesItsNeighborsEvenly() {
        val core = node("core", 800f, 400f, 3)
        val first = node("first", 0f, 0f, 0)
        val second = node("second", 200f, 300f, 1)
        val third = node("third", 500f, 100f, 2)
        val result = autoLayoutNodeTransforms(
            nodes = listOf(first, second, third, core),
            relations = listOf(
                relation("core", "first"),
                relation("core", "second"),
                relation("core", "third"),
            ),
            mode = DiagramLayoutMode.RadialRelationship,
            horizontalGap = 40f,
            verticalGap = 60f,
        )

        val coreCenter = result.getValue(core.id).center()
        val radii = listOf(first, second, third).map { node ->
            val neighborCenter = result.getValue(node.id).center()
            val dx = neighborCenter.x - coreCenter.x
            val dy = neighborCenter.y - coreCenter.y
            sqrt(dx * dx + dy * dy)
        }

        assertTrue(radii.all { radius -> abs(radius - radii.first()) < 0.01f })
        assertEquals(core.transform.size, result.getValue(core.id).size)
    }

    @Test
    fun gridLayoutUsesTheLargestCellDimensionsWithoutOverlappingMixedSizes() {
        val first = node("first", 500f, 500f, 0).withSize(180f, 80f)
        val second = node("second", 0f, 0f, 1).withSize(100f, 120f)
        val third = node("third", 700f, 300f, 2).withSize(140f, 60f)
        val fourth = node("fourth", 200f, 800f, 3).withSize(80f, 160f)
        val fifth = node("fifth", 900f, 100f, 4).withSize(160f, 90f)

        val result = autoLayoutNodeTransforms(
            nodes = listOf(first, second, third, fourth, fifth),
            relations = emptyList(),
            mode = DiagramLayoutMode.Grid,
            horizontalGap = 40f,
            verticalGap = 60f,
        )

        val firstResult = result.getValue(first.id)
        val secondResult = result.getValue(second.id)
        val fourthResult = result.getValue(fourth.id)
        assertTrue(firstResult.position.x + firstResult.size.width + 40f <= secondResult.position.x)
        assertTrue(firstResult.position.y + firstResult.size.height + 60f <= fourthResult.position.y)
        assertEquals(fourth.transform.size, fourthResult.size)
    }

    private fun node(id: String, x: Float, y: Float, z: Long) = TextNode(
        id = CanvasObjectId(id),
        zIndex = z,
        transform = CanvasTransform(Vec2(x, y), CanvasSize(100f, 80f)),
        text = id,
    )

    private fun relation(source: String, target: String) = Relation(
        id = RelationId("$source-$target"),
        sourceObjectId = CanvasObjectId(source),
        targetObjectId = CanvasObjectId(target),
        direction = RelationDirection.Forward,
    )

    private fun CanvasTransform.center() = Vec2(
        position.x + size.width / 2f,
        position.y + size.height / 2f,
    )

    private fun TextNode.withSize(width: Float, height: Float) = copy(
        transform = transform.copy(size = CanvasSize(width, height)),
    )
}
