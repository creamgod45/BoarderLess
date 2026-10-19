package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode

internal enum class SelectionAlignment {
    Left,
    HorizontalCenter,
    Right,
    Top,
    VerticalCenter,
    Bottom,
}

private data class AlignmentBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal fun alignNodeTransforms(
    nodes: Collection<TextNode>,
    alignment: SelectionAlignment,
): Map<CanvasObjectId, CanvasTransform> {
    if (nodes.size < 2) return nodes.associate { it.id to it.transform }
    val boundsById =
        nodes.associate { node ->
            val corners = rotatedTransformCorners(node.transform)
            node.id to
                AlignmentBounds(
                    left = corners.minOf { it.x },
                    top = corners.minOf { it.y },
                    right = corners.maxOf { it.x },
                    bottom = corners.maxOf { it.y },
                )
        }
    val target =
        when (alignment) {
            SelectionAlignment.Left -> {
                boundsById.values.minOf(AlignmentBounds::left)
            }

            SelectionAlignment.HorizontalCenter -> {
                (
                    boundsById.values.minOf(AlignmentBounds::left) +
                        boundsById.values.maxOf(AlignmentBounds::right)
                ) / 2f
            }

            SelectionAlignment.Right -> {
                boundsById.values.maxOf(AlignmentBounds::right)
            }

            SelectionAlignment.Top -> {
                boundsById.values.minOf(AlignmentBounds::top)
            }

            SelectionAlignment.VerticalCenter -> {
                (
                    boundsById.values.minOf(AlignmentBounds::top) +
                        boundsById.values.maxOf(AlignmentBounds::bottom)
                ) / 2f
            }

            SelectionAlignment.Bottom -> {
                boundsById.values.maxOf(AlignmentBounds::bottom)
            }
        }
    return nodes.associate { node ->
        val bounds = boundsById.getValue(node.id)
        val delta =
            when (alignment) {
                SelectionAlignment.Left -> target - bounds.left
                SelectionAlignment.HorizontalCenter -> target - bounds.centerX
                SelectionAlignment.Right -> target - bounds.right
                SelectionAlignment.Top -> target - bounds.top
                SelectionAlignment.VerticalCenter -> target - bounds.centerY
                SelectionAlignment.Bottom -> target - bounds.bottom
            }
        node.id to
            node.transform.copy(
                position =
                    when (alignment) {
                        SelectionAlignment.Left,
                        SelectionAlignment.HorizontalCenter,
                        SelectionAlignment.Right,
                        -> node.transform.position.copy(x = node.transform.position.x + delta)

                        SelectionAlignment.Top,
                        SelectionAlignment.VerticalCenter,
                        SelectionAlignment.Bottom,
                        -> node.transform.position.copy(y = node.transform.position.y + delta)
                    },
            )
    }
}
