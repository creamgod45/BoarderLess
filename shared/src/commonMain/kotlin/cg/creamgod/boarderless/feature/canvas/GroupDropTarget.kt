package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2

internal fun groupDropTargetId(
    groups: Collection<GroupFrame>,
    movingTransform: CanvasTransform,
): CanvasObjectId? = groupDropTargetId(groups, listOf(movingTransform))

internal fun groupDropTargetId(
    groups: Collection<GroupFrame>,
    movingTransforms: Collection<CanvasTransform>,
): CanvasObjectId? {
    if (movingTransforms.isEmpty()) return null
    val byId = groups.associateBy(GroupFrame::id)
    val corners = movingTransforms.flatMap(::rotatedTransformCorners)
    val center = Vec2(
        (corners.minOf(Vec2::x) + corners.maxOf(Vec2::x)) / 2f,
        (corners.minOf(Vec2::y) + corners.maxOf(Vec2::y)) / 2f,
    )
    fun depth(group: GroupFrame): Int {
        var parentId = group.parentId
        val visited = mutableSetOf(group.id)
        var result = 0
        while (parentId != null && visited.add(parentId)) {
            val parent = byId[parentId] ?: break
            result += 1
            parentId = parent.parentId
        }
        return result
    }

    return groups
        .asSequence()
        .filterNot(GroupFrame::locked)
        .filter { group -> group.transform.containsWorldPoint(center, NodeShape.Rectangle) }
        .maxWithOrNull(compareBy<GroupFrame>({ depth(it) }, GroupFrame::zIndex, { it.id.value }))
        ?.id
}
