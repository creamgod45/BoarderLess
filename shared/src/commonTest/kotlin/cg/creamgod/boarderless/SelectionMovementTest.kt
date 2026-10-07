package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.feature.canvas.movableSelectionObjectIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionMovementTest {
    @Test
    fun selectingAnOuterGroupMovesItsWholeNestedTree() {
        val fixture = fixture()

        assertEquals(
            setOf(fixture.outer.id, fixture.inner.id, fixture.child.id),
            movableSelectionObjectIds(fixture.workspace, setOf(fixture.outer.id)),
        )
    }

    @Test
    fun lockedDescendantBlocksTheEntireSelectedGroupTree() {
        val fixture = fixture(childLocked = true)

        assertTrue(movableSelectionObjectIds(fixture.workspace, setOf(fixture.outer.id)).isEmpty())
    }

    @Test
    fun otherSelectedObjectsCanMoveWithoutBreakingABlockedGroup() {
        val fixture = fixture(childLocked = true)

        assertEquals(
            setOf(fixture.standalone.id),
            movableSelectionObjectIds(
                fixture.workspace,
                setOf(fixture.outer.id, fixture.standalone.id),
            ),
        )
    }

    @Test
    fun lockedDirectSelectionDoesNotMove() {
        val fixture = fixture(standaloneLocked = true)

        assertTrue(movableSelectionObjectIds(fixture.workspace, setOf(fixture.standalone.id)).isEmpty())
    }

    private fun fixture(
        childLocked: Boolean = false,
        standaloneLocked: Boolean = false,
    ): Fixture {
        val outer = GroupFrame(id("outer"), transform = transform(0f))
        val inner = GroupFrame(id("inner"), parentId = outer.id, transform = transform(20f))
        val child = TextNode(
            id = id("child"),
            parentId = inner.id,
            locked = childLocked,
            transform = transform(40f),
            text = "Child",
        )
        val standalone = TextNode(
            id = id("standalone"),
            locked = standaloneLocked,
            transform = transform(500f),
            text = "Standalone",
        )
        val workspace = Workspace(
            id = WorkspaceId("workspace"),
            title = "Movement",
            objects = listOf(outer, inner, child, standalone).associateBy { it.id },
        )
        return Fixture(workspace, outer, inner, child, standalone)
    }

    private fun id(value: String) = CanvasObjectId(value)

    private fun transform(x: Float) = CanvasTransform(Vec2(x, 0f), CanvasSize(160f, 100f))

    private data class Fixture(
        val workspace: Workspace,
        val outer: GroupFrame,
        val inner: GroupFrame,
        val child: TextNode,
        val standalone: TextNode,
    )
}
