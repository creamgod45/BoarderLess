package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.groupDropTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupDropTargetTest {
    @Test
    fun deepestUnlockedGroupContainingTheNodeCenterWins() {
        val outer = group("outer", 0f, 0f, 500f, 400f, z = 10)
        val inner = group("inner", 100f, 80f, 260f, 220f, parentId = outer.id, z = 1)
        val moving = CanvasTransform(Vec2(160f, 120f), CanvasSize(100f, 80f))

        assertEquals(inner.id, groupDropTargetId(listOf(outer, inner), moving))
        assertEquals(outer.id, groupDropTargetId(listOf(outer, inner.copy(locked = true)), moving))
    }

    @Test
    fun rotatedGroupUsesItsActualSilhouetteInsteadOfItsAxisAlignedBounds() {
        val rotated = group("rotated", 100f, 100f, 200f, 100f, z = 1).copy(
            transform = CanvasTransform(Vec2(100f, 100f), CanvasSize(200f, 100f), rotationDegrees = 45f),
        )

        assertEquals(
            rotated.id,
            groupDropTargetId(
                listOf(rotated),
                CanvasTransform(Vec2(175f, 125f), CanvasSize(50f, 50f)),
            ),
        )
        assertNull(
            groupDropTargetId(
                listOf(rotated),
                CanvasTransform(Vec2(105f, 55f), CanvasSize(10f, 10f)),
            ),
        )
    }

    @Test
    fun multiNodeSelectionUsesItsCombinedVisualCenter() {
        val lane = group("lane", 200f, 100f, 400f, 300f, z = 1)
        val transforms = listOf(
            CanvasTransform(Vec2(240f, 140f), CanvasSize(80f, 60f)),
            CanvasTransform(Vec2(460f, 280f), CanvasSize(120f, 80f), rotationDegrees = 20f),
        )

        assertEquals(lane.id, groupDropTargetId(listOf(lane), transforms))
        assertNull(
            groupDropTargetId(
                listOf(lane),
                transforms.map { transform ->
                    transform.copy(position = transform.position + Vec2(600f, 0f))
                },
            ),
        )
    }

    private fun group(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        parentId: CanvasObjectId? = null,
        z: Long,
    ) = GroupFrame(
        id = CanvasObjectId(id),
        parentId = parentId,
        zIndex = z,
        transform = CanvasTransform(Vec2(x, y), CanvasSize(width, height)),
        title = id,
    )
}
