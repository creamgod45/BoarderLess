package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import kotlin.math.min
import kotlin.math.sqrt

/** World geometry is shared by rendering, selection, previews and insertion placement. */
internal fun relationWorldRoute(
    relation: Relation,
    nodes: Map<CanvasObjectId, CanvasObject>,
    transforms: Map<CanvasObjectId, CanvasTransform> = nodes.mapValues { it.value.transform },
): List<Vec2>? {
    val source = nodes[relation.sourceObjectId] ?: return null
    val target = nodes[relation.targetObjectId] ?: return null
    val sourceTransform = transforms[source.id] ?: return null
    val targetTransform = transforms[target.id] ?: return null
    val route = relation.geometry?.route ?: RelationRoute.Auto
    return if (source.id == target.id) {
        val loop = route as? RelationRoute.Loop ?: RelationRoute.Loop()
        selfLoopRelationRoute(source, sourceTransform, RelationLoopSide.valueOf(loop.side.replaceFirstChar { it.uppercase() }), loop.extent)
    } else {
        when (route) {
            is RelationRoute.Manual -> {
                listOf(source.connectionBoundary(sourceTransform, route.waypoints.first())) +
                    route.waypoints + target.connectionBoundary(targetTransform, route.waypoints.last())
            }

            RelationRoute.Auto -> {
                orthogonalRelationRoute(
                    source,
                    sourceTransform,
                    target,
                    targetTransform,
                    transforms.filterKeys { it != source.id && it != target.id }.values,
                    (min(sourceTransform.size.height, targetTransform.size.height) * 0.18f).coerceAtLeast(18f),
                )
            }

            is RelationRoute.Loop -> {
                error("Loop needs one endpoint")
            }
        }
    }
}

internal fun manualRelationLabelAnchor(
    points: List<Vec2>,
    placement: RelationLabelPlacement.Manual,
): Vec2? {
    val segments =
        points.zipWithNext().mapNotNull { (a, b) ->
            val delta = b - a
            val length = sqrt(delta.x.toDouble() * delta.x + delta.y.toDouble() * delta.y).toFloat()
            if (length > 0f && length.isFinite()) Triple(a, delta, length) else null
        }
    if (segments.isEmpty()) return null
    var remaining = segments.sumOf { it.third.toDouble() }.toFloat() * placement.pathFraction
    for ((index, segment) in segments.withIndex()) {
        val (a, delta, length) = segment
        if (remaining <= length || index == segments.lastIndex) {
            val tangent = delta * (1f / length)
            val normal = Vec2(-tangent.y, tangent.x)
            return a + tangent * remaining.coerceIn(0f, length) + tangent * placement.tangentOffset + normal * placement.normalOffset
        }
        remaining -= length
    }
    return null
}

internal fun clipboardGeometryRoutes(payload: ClipboardPayload): Map<ClipboardRelation, List<Vec2>> {
    if (payload.relations.none { it.geometry != null }) return emptyMap()
    val nodes =
        (
            payload.nodes.map { copied ->
                TextNode(
                    CanvasObjectId(copied.originalId),
                    transform = CanvasTransform(Vec2(copied.x, copied.y), CanvasSize(copied.width, copied.height), copied.rotationDegrees),
                    text = copied.text,
                    vectorPath = copied.vectorPath,
                    shape = NodeShape.fromToken(copied.shapeToken) ?: NodeShape.RoundedRectangle,
                )
            } +
                payload.media.map { copied ->
                    TextNode(
                        CanvasObjectId(copied.originalId),
                        transform =
                            CanvasTransform(
                                Vec2(copied.x, copied.y),
                                CanvasSize(copied.width, copied.height),
                                copied.rotationDegrees,
                            ),
                        text = copied.altText,
                        shape = NodeShape.Rectangle,
                    )
                }
        ).associateBy { it.id }
    return payload.relations
        .filter { it.geometry != null }
        .mapNotNull { copied ->
            val relation =
                Relation(
                    RelationId("preview"),
                    sourceObjectId = CanvasObjectId(copied.sourceId),
                    targetObjectId = CanvasObjectId(copied.targetId),
                    geometry = copied.geometry,
                )
            runCatching { relationWorldRoute(relation, nodes) }.getOrNull()?.let { copied to it }
        }.toMap()
}
