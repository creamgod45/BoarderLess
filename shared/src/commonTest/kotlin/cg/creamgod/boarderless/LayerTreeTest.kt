package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.LayerObjectKind
import cg.creamgod.boarderless.feature.canvas.buildLayerTree
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerTreeTest {
    private val transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f))

    @Test
    fun rootsAndChildrenAreTopmostFirst() {
        val group = GroupFrame(
            id = CanvasObjectId("group"),
            transform = transform,
            title = "Research",
            zIndex = 4,
        )
        val childBack = TextNode(
            id = CanvasObjectId("child-back"),
            transform = transform,
            parentId = group.id,
            text = "Background",
            zIndex = 1,
        )
        val childFront = TextNode(
            id = CanvasObjectId("child-front"),
            transform = transform,
            parentId = group.id,
            text = "Foreground",
            zIndex = 9,
        )
        val root = TextNode(
            id = CanvasObjectId("root"),
            transform = transform,
            text = "Top root",
            zIndex = 12,
        )

        val entries = buildLayerTree(listOf(group, childBack, childFront, root))

        assertEquals(listOf("root", "group", "child-front", "child-back"), entries.map { it.objectId.value })
        assertEquals(listOf(0, 0, 1, 1), entries.map { it.depth })
    }

    @Test
    fun entriesExposeKindLockAndReadableFallbackTitle() {
        val group = GroupFrame(
            id = CanvasObjectId("group"),
            transform = transform,
            title = "",
            locked = true,
        )
        val node = TextNode(id = CanvasObjectId("node"), transform = transform, text = "\nsecond line")

        val entries = buildLayerTree(listOf(group, node)).associateBy { it.objectId.value }

        assertEquals(LayerObjectKind.Group, entries.getValue("group").kind)
        assertEquals("Untitled group", entries.getValue("group").title)
        assertEquals(true, entries.getValue("group").locked)
        assertEquals("Untitled thought", entries.getValue("node").title)
    }

    @Test
    fun malformedCyclesStillReturnEveryObjectExactlyOnce() {
        val firstId = CanvasObjectId("first")
        val secondId = CanvasObjectId("second")
        val first = GroupFrame(id = firstId, transform = transform, parentId = secondId, title = "First")
        val second = GroupFrame(id = secondId, transform = transform, parentId = firstId, title = "Second")

        val entries = buildLayerTree(listOf(first, second))

        assertEquals(setOf(firstId, secondId), entries.map { it.objectId }.toSet())
        assertEquals(2, entries.size)
    }
}
