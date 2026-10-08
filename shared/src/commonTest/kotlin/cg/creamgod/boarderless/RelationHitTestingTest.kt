package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class RelationHitTestingTest {
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f)), text = "A")
    private val b = TextNode(CanvasObjectId("b"), transform = CanvasTransform(Vec2(300f, 0f), CanvasSize(100f, 60f)), text = "B")
    private val edge = Relation(RelationId("e"), sourceObjectId = a.id, targetObjectId = b.id)
    private val nodes = mapOf(a.id to a, b.id to b)
    private val viewport = Viewport()
    @Test fun movedEndpointsHitTheRenderedRouteAndNotItsFormerPosition() {
        val old = assertNotNull(relationSegment(edge, nodes, viewport)).points
        val oldMid = (old.first() + old.last()) * .5f
        val moved = mapOf(a.id to a.copy(transform = a.transform.copy(position = Vec2(0f, 200f))),
            b.id to b.copy(transform = b.transform.copy(position = Vec2(300f, 200f))))
        val current = assertNotNull(relationSegment(edge, moved, viewport)).points
        val mid = (current.first() + current.last()) * .5f
        assertEquals(edge, findRelationAt(mid, listOf(edge), moved, viewport, 14f))
        assertNull(findRelationAt(oldMid, listOf(edge), moved, viewport, 14f))
    }
    @Test fun dragAndResizePreviewUseTheExactSameProjectionAsDrawing() {
        val drag = mapOf(a.id to Vec2(0f, 150f), b.id to Vec2(0f, 150f))
        val transforms = mapOf(b.id to b.transform.copy(size = CanvasSize(140f, 80f), rotationDegrees = 45f))
        val zoom = viewport.copy(zoom = 2f, pan = Vec2(17f, -8f))
        val route = assertNotNull(relationSegment(edge, nodes, zoom, drag, transforms)).points
        val mid = (route.first() + route.last()) * .5f
        assertEquals(edge, findRelationAt(mid, listOf(edge), nodes, zoom, 14f, drag, transforms))
        assertNull(findRelationAt(mid, listOf(edge), nodes, zoom, 14f))
        assertEquals(edge, findRelationAt(mid + Vec2(0f, 8f), listOf(edge), nodes, zoom, 14f, drag, transforms))
        assertNull(findRelationAt(mid + Vec2(0f, 40f), listOf(edge), nodes, zoom, 14f, drag, transforms))
    }
}
