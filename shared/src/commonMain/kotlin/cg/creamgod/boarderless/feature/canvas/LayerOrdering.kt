package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId

internal enum class LayerMove {
    Forward,
    Backward,
    Front,
    Back,
}

/**
 * Returns only the z-index changes needed to perform [move]. Objects with equal
 * z-indices are ordered by their stable IDs before the move is applied.
 */
internal fun layerZIndexUpdates(
    objects: Collection<CanvasObject>,
    selectedIds: Set<CanvasObjectId>,
    move: LayerMove,
): Map<CanvasObjectId, Long> {
    val ordered = objects
        .sortedWith(compareBy<CanvasObject>({ it.zIndex }, { it.id.value }))
        .map { it.id }
    val selected = selectedIds.intersect(ordered.toSet())
    if (selected.isEmpty()) return emptyMap()

    val reordered = ordered.toMutableList()
    when (move) {
        LayerMove.Forward -> {
            for (index in reordered.lastIndex - 1 downTo 0) {
                if (reordered[index] in selected && reordered[index + 1] !in selected) {
                    val displaced = reordered[index + 1]
                    reordered[index + 1] = reordered[index]
                    reordered[index] = displaced
                }
            }
        }

        LayerMove.Backward -> {
            for (index in 1..reordered.lastIndex) {
                if (reordered[index] in selected && reordered[index - 1] !in selected) {
                    val displaced = reordered[index - 1]
                    reordered[index - 1] = reordered[index]
                    reordered[index] = displaced
                }
            }
        }

        LayerMove.Front -> {
            reordered.clear()
            reordered += ordered.filterNot(selected::contains)
            reordered += ordered.filter(selected::contains)
        }

        LayerMove.Back -> {
            reordered.clear()
            reordered += ordered.filter(selected::contains)
            reordered += ordered.filterNot(selected::contains)
        }
    }

    if (reordered == ordered) return emptyMap()

    val currentById = objects.associateBy(CanvasObject::id)
    return reordered.mapIndexedNotNull { index, id ->
        val nextZIndex = index.toLong()
        if (currentById.getValue(id).zIndex == nextZIndex) null else id to nextZIndex
    }.toMap()
}
