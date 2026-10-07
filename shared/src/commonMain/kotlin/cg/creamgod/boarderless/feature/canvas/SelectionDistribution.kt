package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode

internal enum class DistributionAxis { Horizontal, Vertical }

private data class DistributionBounds(
    val start: Float,
    val end: Float,
) {
    val size: Float get() = end - start
    val center: Float get() = (start + end) / 2f
}

internal fun distributeNodeTransforms(
    nodes: Collection<TextNode>,
    axis: DistributionAxis,
): Map<CanvasObjectId, CanvasTransform> {
    if (nodes.size < 3) return nodes.associate { it.id to it.transform }

    fun bounds(node: TextNode): DistributionBounds {
        val corners = rotatedTransformCorners(node.transform)
        return when (axis) {
            DistributionAxis.Horizontal -> {
                DistributionBounds(
                    corners.minOf { it.x },
                    corners.maxOf { it.x },
                )
            }

            DistributionAxis.Vertical -> {
                DistributionBounds(
                    corners.minOf { it.y },
                    corners.maxOf { it.y },
                )
            }
        }
    }

    val boundsById = nodes.associate { it.id to bounds(it) }
    val ordered =
        nodes.sortedWith(
            compareBy<TextNode>({ boundsById.getValue(it.id).center }, TextNode::zIndex, { it.id.value }),
        )
    val selectionStart = boundsById.getValue(ordered.first().id).start
    val selectionEnd = boundsById.getValue(ordered.last().id).end
    val totalSize = ordered.sumOf { boundsById.getValue(it.id).size.toDouble() }.toFloat()
    val gap = (selectionEnd - selectionStart - totalSize) / (ordered.size - 1)
    var cursor = selectionStart
    return ordered.associate { node ->
        val visualBounds = boundsById.getValue(node.id)
        val delta = cursor - visualBounds.start
        cursor += visualBounds.size + gap
        node.id to
            node.transform.copy(
                position =
                    when (axis) {
                        DistributionAxis.Horizontal -> {
                            node.transform.position.copy(
                                x = node.transform.position.x + delta,
                            )
                        }

                        DistributionAxis.Vertical -> {
                            node.transform.position.copy(
                                y = node.transform.position.y + delta,
                            )
                        }
                    },
            )
    }
}
