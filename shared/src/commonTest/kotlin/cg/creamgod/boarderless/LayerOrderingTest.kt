package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.LayerMove
import cg.creamgod.boarderless.feature.canvas.layerZIndexUpdates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerOrderingTest {
    @Test
    fun forwardMovesASelectionBlockPastOneUnselectedObject() {
        val objects = nodes("a", "b", "c", "d")

        val updates =
            layerZIndexUpdates(
                objects = objects,
                selectedIds = setOf(id("b"), id("c")),
                move = LayerMove.Forward,
            )

        assertEquals(listOf("a", "d", "b", "c"), orderAfter(objects, updates))
    }

    @Test
    fun backwardMovesASelectionBlockPastOneUnselectedObject() {
        val objects = nodes("a", "b", "c", "d")

        val updates =
            layerZIndexUpdates(
                objects = objects,
                selectedIds = setOf(id("b"), id("c")),
                move = LayerMove.Backward,
            )

        assertEquals(listOf("b", "c", "a", "d"), orderAfter(objects, updates))
    }

    @Test
    fun frontAndBackPreserveRelativeSelectionOrder() {
        val objects = nodes("a", "b", "c", "d")
        val selected = setOf(id("a"), id("c"))

        assertEquals(
            listOf("b", "d", "a", "c"),
            orderAfter(objects, layerZIndexUpdates(objects, selected, LayerMove.Front)),
        )
        assertEquals(
            listOf("a", "c", "b", "d"),
            orderAfter(objects, layerZIndexUpdates(objects, selected, LayerMove.Back)),
        )
    }

    @Test
    fun movementAtTheBoundaryDoesNotCreateAnOperation() {
        val objects = nodes("a", "b", "c")

        assertTrue(layerZIndexUpdates(objects, setOf(id("c")), LayerMove.Forward).isEmpty())
        assertTrue(layerZIndexUpdates(objects, setOf(id("a")), LayerMove.Backward).isEmpty())
        assertTrue(layerZIndexUpdates(objects, setOf(id("b"), id("c")), LayerMove.Front).isEmpty())
    }

    @Test
    fun equalZIndicesUseStableIdsAndAreNormalizedAfterMovement() {
        val objects = listOf(node("c", 0), node("a", 0), node("b", 0))
        val updates = layerZIndexUpdates(objects, setOf(id("a")), LayerMove.Forward)

        assertEquals(listOf("b", "a", "c"), orderAfter(objects, updates))
        assertEquals(setOf(0L, 1L, 2L), objects.map { updates[it.id] ?: it.zIndex }.toSet())
    }

    private fun nodes(vararg ids: String): List<TextNode> =
        ids.mapIndexed { index, value ->
            node(value, index.toLong())
        }

    private fun node(
        value: String,
        zIndex: Long,
    ) = TextNode(
        id = id(value),
        zIndex = zIndex,
        transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)),
        text = value,
    )

    private fun id(value: String) = CanvasObjectId(value)

    private fun orderAfter(
        objects: List<TextNode>,
        updates: Map<CanvasObjectId, Long>,
    ): List<String> =
        objects
            .sortedWith(compareBy({ updates[it.id] ?: it.zIndex }, { it.id.value }))
            .map { it.id.value }
}
