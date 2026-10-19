package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class DiagramLayoutMode {
    HorizontalFlow,
    VerticalTree,
    RadialRelationship,
    Grid,
}

internal fun autoLayoutNodeTransforms(
    nodes: Collection<TextNode>,
    relations: Collection<Relation>,
    mode: DiagramLayoutMode,
    horizontalGap: Float,
    verticalGap: Float,
): Map<CanvasObjectId, CanvasTransform> {
    if (nodes.size < 2) return nodes.associate { it.id to it.transform }
    val byId = nodes.associateBy(TextNode::id)
    val edges = directedEdges(relations, byId.keys)
    val order = topologicalOrder(byId, edges)
    val anchorX = nodes.minOf { it.transform.position.x }
    val anchorY = nodes.minOf { it.transform.position.y }

    return when (mode) {
        DiagramLayoutMode.HorizontalFlow -> {
            val maxHeight =
                order.maxOf {
                    byId
                        .getValue(it)
                        .transform.size.height
                }
            var nextX = anchorX
            order.associateWith { id ->
                val transform = byId.getValue(id).transform
                transform
                    .copy(
                        position =
                            transform.position.copy(
                                x = nextX,
                                y = anchorY + (maxHeight - transform.size.height) / 2f,
                            ),
                    ).also { nextX += transform.size.width + horizontalGap }
            }
        }

        DiagramLayoutMode.VerticalTree -> {
            val depths = hierarchyDepths(order, edges)
            val layers =
                order
                    .groupBy { depths.getValue(it) }
                    .entries
                    .sortedBy { entry -> entry.key }
                    .map { it.key to it.value }
            val layerWidths =
                layers.associate { (depth, ids) ->
                    depth to (
                        ids
                            .sumOf {
                                byId
                                    .getValue(it)
                                    .transform.size.width
                                    .toDouble()
                            }.toFloat() +
                            horizontalGap * (ids.size - 1).coerceAtLeast(0)
                    )
                }
            val widestLayer = layerWidths.values.maxOrNull() ?: 0f
            val result = mutableMapOf<CanvasObjectId, CanvasTransform>()
            var nextY = anchorY
            layers.forEach { (depth, ids) ->
                var nextX = anchorX + (widestLayer - layerWidths.getValue(depth)) / 2f
                val layerHeight =
                    ids.maxOf {
                        byId
                            .getValue(it)
                            .transform.size.height
                    }
                ids.forEach { id ->
                    val transform = byId.getValue(id).transform
                    result[id] =
                        transform.copy(
                            position =
                                transform.position.copy(
                                    x = nextX,
                                    y = nextY + (layerHeight - transform.size.height) / 2f,
                                ),
                        )
                    nextX += transform.size.width + horizontalGap
                }
                nextY += layerHeight + verticalGap
            }
            result
        }

        DiagramLayoutMode.RadialRelationship -> {
            val stable = compareBy<TextNode>(TextNode::zIndex, { it.id.value })
            val degreeById =
                byId.keys.associateWith { id ->
                    edges.count { (source, target) -> source == id || target == id }
                }
            val centerNode =
                nodes
                    .sortedWith(
                        compareByDescending<TextNode> { degreeById.getValue(it.id) }.then(stable),
                    ).first()
            val orbitNodes =
                order
                    .filterNot { it == centerNode.id }
                    .map(byId::getValue)
            val maxWidth = nodes.maxOf { it.transform.size.width }
            val maxHeight = nodes.maxOf { it.transform.size.height }
            val orbitCircumference =
                orbitNodes
                    .sumOf { node ->
                        max(node.transform.size.width, node.transform.size.height).toDouble() + horizontalGap
                    }.toFloat()
            val radius =
                max(
                    max(maxWidth, maxHeight) + max(horizontalGap, verticalGap),
                    orbitCircumference / (2f * PI.toFloat()),
                )
            val center =
                Vec2(
                    x = anchorX + radius + maxWidth / 2f,
                    y = anchorY + radius + maxHeight / 2f,
                )
            buildMap {
                put(
                    centerNode.id,
                    centerNode.transform.copy(
                        position =
                            center -
                                Vec2(
                                    centerNode.transform.size.width / 2f,
                                    centerNode.transform.size.height / 2f,
                                ),
                    ),
                )
                orbitNodes.forEachIndexed { index, node ->
                    val angle = -PI / 2.0 + (2.0 * PI * index / orbitNodes.size)
                    val nodeCenter =
                        center +
                            Vec2(
                                x = (cos(angle) * radius).toFloat(),
                                y = (sin(angle) * radius).toFloat(),
                            )
                    put(
                        node.id,
                        node.transform.copy(
                            position =
                                nodeCenter -
                                    Vec2(
                                        node.transform.size.width / 2f,
                                        node.transform.size.height / 2f,
                                    ),
                        ),
                    )
                }
            }
        }

        DiagramLayoutMode.Grid -> {
            val columnCount = ceil(sqrt(order.size.toDouble())).toInt().coerceAtLeast(1)
            val rowCount = ceil(order.size.toDouble() / columnCount).toInt()
            val columnWidths =
                List(columnCount) { column ->
                    order
                        .filterIndexed { index, _ -> index % columnCount == column }
                        .maxOfOrNull { id ->
                            byId
                                .getValue(id)
                                .transform.size.width
                        }
                        ?: 0f
                }
            val rowHeights =
                List(rowCount) { row ->
                    order
                        .drop(row * columnCount)
                        .take(columnCount)
                        .maxOf { id ->
                            byId
                                .getValue(id)
                                .transform.size.height
                        }
                }
            val columnX = mutableListOf<Float>()
            var nextX = anchorX
            columnWidths.forEach { width ->
                columnX += nextX
                nextX += width + horizontalGap
            }
            val rowY = mutableListOf<Float>()
            var nextY = anchorY
            rowHeights.forEach { height ->
                rowY += nextY
                nextY += height + verticalGap
            }
            order
                .mapIndexed { index, id ->
                    val transform = byId.getValue(id).transform
                    val column = index % columnCount
                    val row = index / columnCount
                    id to
                        transform.copy(
                            position =
                                Vec2(
                                    x = columnX[column] + (columnWidths[column] - transform.size.width) / 2f,
                                    y = rowY[row] + (rowHeights[row] - transform.size.height) / 2f,
                                ),
                        )
                }.toMap()
        }
    }
}

private fun directedEdges(
    relations: Collection<Relation>,
    ids: Set<CanvasObjectId>,
): List<Pair<CanvasObjectId, CanvasObjectId>> =
    relations
        .mapNotNull { relation ->
            if (relation.sourceObjectId !in ids || relation.targetObjectId !in ids) return@mapNotNull null
            when (relation.direction) {
                RelationDirection.Backward -> {
                    relation.targetObjectId to relation.sourceObjectId
                }

                RelationDirection.None, RelationDirection.Forward, RelationDirection.Both -> {
                    relation.sourceObjectId to relation.targetObjectId
                }
            }
        }.distinct()

private fun topologicalOrder(
    nodes: Map<CanvasObjectId, TextNode>,
    edges: List<Pair<CanvasObjectId, CanvasObjectId>>,
): List<CanvasObjectId> {
    val stable = compareBy<CanvasObjectId>({ nodes.getValue(it).zIndex }, CanvasObjectId::value)
    val outgoing = edges.groupBy({ it.first }, { it.second })
    val indegree = nodes.keys.associateWith { id -> edges.count { it.second == id } }.toMutableMap()
    val ready =
        nodes.keys
            .filter { indegree.getValue(it) == 0 }
            .sortedWith(stable)
            .toMutableList()
    val ordered = mutableListOf<CanvasObjectId>()
    while (ready.isNotEmpty()) {
        val id = ready.removeAt(0)
        ordered += id
        outgoing[id].orEmpty().sortedWith(stable).forEach { target ->
            indegree[target] = indegree.getValue(target) - 1
            if (indegree.getValue(target) == 0) {
                ready += target
                ready.sortWith(stable)
            }
        }
    }
    ordered += nodes.keys.filterNot(ordered::contains).sortedWith(stable)
    return ordered
}

private fun hierarchyDepths(
    order: List<CanvasObjectId>,
    edges: List<Pair<CanvasObjectId, CanvasObjectId>>,
): Map<CanvasObjectId, Int> {
    val predecessors = edges.groupBy({ it.second }, { it.first })
    val depth = mutableMapOf<CanvasObjectId, Int>()
    order.forEach { id ->
        depth[id] = predecessors[id]
            .orEmpty()
            .mapNotNull(depth::get)
            .maxOrNull()
            ?.plus(1) ?: 0
    }
    return depth
}
