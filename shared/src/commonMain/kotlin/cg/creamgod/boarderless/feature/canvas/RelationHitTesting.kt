package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

internal data class RelationPath(
    val points: List<Vec2>,
)

internal fun relationSegment(
    relation: Relation,
    nodes: Map<CanvasObjectId, CanvasObject>,
    viewport: Viewport,
    dragPreviews: Map<CanvasObjectId, Vec2> = emptyMap(),
    transformPreviews: Map<CanvasObjectId, CanvasTransform> = emptyMap(),
): RelationPath? {
    val source = nodes[relation.sourceObjectId] ?: return null
    val target = nodes[relation.targetObjectId] ?: return null
    val renderedTransforms =
        nodes.mapValues { (id, node) ->
            val transform = transformPreviews[id] ?: node.transform
            transform.copy(position = transform.position + (dragPreviews[id] ?: Vec2.Zero))
        }
    return runCatching {
        relationWorldRoute(relation, nodes, renderedTransforms)
    }.getOrNull()?.let { RelationPath(it.map(viewport::worldToScreen)) }
}

internal fun findRelationAt(
    screenPosition: Vec2,
    relations: Collection<Relation>,
    nodes: Map<CanvasObjectId, CanvasObject>,
    viewport: Viewport,
    tolerance: Float,
    dragPreviews: Map<CanvasObjectId, Vec2> = emptyMap(),
    transformPreviews: Map<CanvasObjectId, CanvasTransform> = emptyMap(),
): Relation? =
    relations
        .mapNotNull { relation ->
            val segment = relationSegment(relation, nodes, viewport, dragPreviews, transformPreviews) ?: return@mapNotNull null
            relation to distanceToPolyline(screenPosition, segment.points)
        }.filter { (_, distance) -> distance <= tolerance }
        .minByOrNull { (_, distance) -> distance }
        ?.first

