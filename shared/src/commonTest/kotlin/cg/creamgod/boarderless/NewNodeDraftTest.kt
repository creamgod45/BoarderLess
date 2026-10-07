package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.feature.canvas.commitNewNodeDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewNodeDraftTest {
    private val draft = TextNode(
        id = CanvasObjectId("draft"),
        transform = CanvasTransform(Vec2(42f, 84f), CanvasSize(260f, 132f)),
        text = "",
        zIndex = 7,
    )

    @Test
    fun blankDraftIsDiscarded() {
        assertNull(commitNewNodeDraft(draft, ""))
        assertNull(commitNewNodeDraft(draft, "  \n "))
    }

    @Test
    fun meaningfulDraftBecomesTheCreatedNode() {
        val committed = commitNewNodeDraft(draft, "A useful thought")

        assertEquals("A useful thought", committed?.text)
        assertEquals(draft.id, committed?.id)
        assertEquals(draft.transform, committed?.transform)
        assertEquals(draft.zIndex, committed?.zIndex)
    }

    @Test
    fun meaningfulWhitespaceIsPreserved() {
        assertEquals("  indented idea", commitNewNodeDraft(draft, "  indented idea")?.text)
    }

    @Test
    fun diagramShapeDraftKeepsItsShapeWhenCommitted() {
        val committed = commitNewNodeDraft(draft.copy(shape = NodeShape.Diamond), "Decision?")

        assertEquals(NodeShape.Diamond, committed?.shape)
    }
}
