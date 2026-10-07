package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class RelationLabelLayoutTest {
    private val route = listOf(Vec2.Zero, Vec2(500f, 0f))
    private val size = CanvasSize(120f, 30f)

    private fun input(id: String) = RelationLabelInput(RelationId(id), route, size)

    private fun bounds(p: Vec2) = LabelBounds(p.x, p.y, p.x + size.width, p.y + size.height)

    @Test fun labelsAvoidTheirOwnLineAndEachOtherInStableOrder() {
        val labels = listOf(input("b"), input("a"))
        val result = layoutRelationLabels(labels, listOf(route), emptyList(), 6f)
        assertEquals(result, layoutRelationLabels(labels.reversed(), listOf(route), emptyList(), 6f))
        assertEquals(2, result.size)
        val boxes = result.values.map(::bounds)
        assertFalse(boxes[0].overlaps(boxes[1]))
        boxes.forEach { assertFalse(it.crosses(route[0], route[1])) }
    }

    @Test fun crossingLinesAndNodeObstaclesAreAvoidedWhenSpaceExists() {
        val cross = listOf(Vec2(250f, -300f), Vec2(250f, 300f))
        val obstacle = LabelBounds(150f, 5f, 350f, 80f)
        val result = layoutRelationLabels(listOf(input("a")), listOf(route, cross), listOf(obstacle), 6f)
        val box = bounds(result.getValue(RelationId("a")))
        assertFalse(box.overlaps(obstacle))
        assertFalse(box.crosses(cross[0], cross[1]))
        assertFalse(box.crosses(route[0], route[1]))
    }

    @Test fun emptyAndDegenerateRoutesNeverCreateInvalidPlacement() {
        assertTrue(layoutRelationLabels(listOf(input("a").copy(route = emptyList())), emptyList(), emptyList(), 6f).isEmpty())
        assertTrue(
            layoutRelationLabels(listOf(input("a").copy(route = listOf(Vec2.Zero, Vec2.Zero))), emptyList(), emptyList(), 6f).isEmpty(),
        )
        assertFails { layoutRelationLabels(emptyList(), emptyList(), emptyList(), Float.NaN) }
    }

    @Test fun segmentIntersectionHandlesDiagonalParallelAndPointSegments() {
        val box = LabelBounds(10f, 10f, 20f, 20f)
        assertTrue(box.crosses(Vec2.Zero, Vec2(30f, 30f)))
        assertFalse(box.crosses(Vec2.Zero, Vec2(0f, 30f)))
        assertTrue(box.crosses(Vec2(15f, 15f), Vec2(15f, 15f)))
        assertFalse(box.crosses(Vec2.Zero, Vec2.Zero))
    }
}
