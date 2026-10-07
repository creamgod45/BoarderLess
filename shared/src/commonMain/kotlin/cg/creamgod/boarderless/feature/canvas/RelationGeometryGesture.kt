package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.sqrt

internal sealed interface RelationGeometryHandle {
    data class Waypoint(
        val index: Int,
    ) : RelationGeometryHandle

    data object NewWaypoint : RelationGeometryHandle

    data object LoopExtent : RelationGeometryHandle

    data class Label(
        val centerWorld: Vec2,
    ) : RelationGeometryHandle
}

/** One immutable before snapshot and one operation identity for the whole pointer gesture. */
internal data class RelationGeometryGesture(
    val owner: WorkspaceSession,
    val baseline: Workspace,
    val relation: Relation,
    val viewport: Viewport,
    val handle: RelationGeometryHandle,
    val operationId: String,
    val initial: RelationGeometry,
    val working: RelationGeometry = initial,
    val displacement: Vec2 = Vec2.Zero,
) {
    fun matches(
        current: WorkspaceSession?,
        live: Workspace,
        view: Viewport,
    ): Boolean =
        current != null &&
            current.userId == owner.userId && current.clientId == owner.clientId && current.workspace.id == owner.workspace.id &&
            current.canEditContent && current.workspaceVersion >= owner.workspaceVersion && current.lastServerSeq >= owner.lastServerSeq &&
            live == baseline && view == viewport

    fun move(screenDelta: Vec2): RelationGeometryGesture {
        val delta = screenDelta * (1f / viewport.zoom)
        val next =
            when (val target = handle) {
                is RelationGeometryHandle.Waypoint -> {
                    val route = initial.route as RelationRoute.Manual
                    initial.copy(
                        route =
                            route.copy(
                                waypoints =
                                    route.waypoints.mapIndexed { index, point ->
                                        if (index ==
                                            target.index
                                        ) {
                                            point + delta
                                        } else {
                                            point
                                        }
                                    },
                            ),
                    )
                }

                RelationGeometryHandle.NewWaypoint -> {
                    val route = initial.route as RelationRoute.Manual
                    initial.copy(route = route.copy(waypoints = listOf(route.waypoints.single() + delta)))
                }

                RelationGeometryHandle.LoopExtent -> {
                    val route = initial.route as RelationRoute.Loop
                    val rotation =
                        baseline.objects
                            .getValue(relation.sourceObjectId)
                            .transform.rotationDegrees
                    val local = rotateVector(delta, -rotation)
                    val normal = RelationLoopSide.valueOf(route.side.replaceFirstChar { it.uppercase() }).outward
                    initial.copy(route = route.copy(extent = (route.extent + local.x * normal.x + local.y * normal.y).coerceIn(16f, 4096f)))
                }

                is RelationGeometryHandle.Label -> {
                    initial.copy(
                        labelPlacement =
                            checkNotNull(
                                labelPlacementAtWorldPoint(
                                    checkNotNull(relationWorldRoute(relation, baseline.objects)),
                                    target.centerWorld + delta,
                                ),
                            ),
                    )
                }
            }
        return copy(working = next, displacement = delta)
    }

    fun operation(
        current: WorkspaceSession?,
        live: Workspace,
        view: Viewport,
    ): UpdateRelationAttributesOperation? {
        require(matches(current, live, view)) { "Relation gesture source changed" }
        if (displacement == Vec2.Zero || working == relation.geometry) return null
        val before = RelationAttributes(relation.direction, relation.intent, relation.label, relation.colorToken, relation.geometry)
        return UpdateRelationAttributesOperation(
            operationId,
            listOf(RelationAttributesChange(relation.id, relation.version, before, before.copy(geometry = working))),
        )
    }

    companion object {
        fun begin(
            owner: WorkspaceSession,
            workspace: Workspace,
            relation: Relation,
            viewport: Viewport,
            handle: RelationGeometryHandle,
            operationId: String = randomUuid(),
        ): RelationGeometryGesture {
            require(owner.canEditContent && owner.workspace.id == workspace.id && workspace.relations[relation.id] == relation)
            val existing = relation.geometry ?: RelationGeometry()
            val initial =
                when (handle) {
                    is RelationGeometryHandle.Waypoint -> {
                        existing.also {
                            require(handle.index in (it.route as RelationRoute.Manual).waypoints.indices)
                        }
                    }

                    RelationGeometryHandle.NewWaypoint -> {
                        require(relation.sourceObjectId != relation.targetObjectId && existing.route == RelationRoute.Auto)
                        existing.copy(
                            route =
                                RelationRoute.Manual(
                                    listOf(polylineMidpoint(checkNotNull(relationWorldRoute(relation, workspace.objects)))),
                                ),
                        )
                    }

                    RelationGeometryHandle.LoopExtent -> {
                        require(relation.sourceObjectId == relation.targetObjectId)
                        existing.copy(route = existing.route as? RelationRoute.Loop ?: RelationRoute.Loop())
                    }

                    is RelationGeometryHandle.Label -> {
                        existing.copy(
                            labelPlacement =
                                checkNotNull(
                                    labelPlacementAtWorldPoint(
                                        checkNotNull(relationWorldRoute(relation, workspace.objects)),
                                        handle.centerWorld,
                                    ),
                                ),
                        )
                    }
                }
            return RelationGeometryGesture(
                owner,
                workspace.copy(objects = workspace.objects.toMap(), relations = workspace.relations.toMap()),
                relation,
                viewport,
                handle,
                operationId,
                initial,
            )
        }
    }
}

/** Projection plus tangent/normal offsets reproduces the requested world anchor exactly. */
internal fun labelPlacementAtWorldPoint(
    points: List<Vec2>,
    point: Vec2,
): RelationLabelPlacement.Manual? {
    val lengths =
        points.zipWithNext().map { (a, b) ->
            val d = b - a
            sqrt(d.x.toDouble() * d.x + d.y.toDouble() * d.y).toFloat()
        }
    val total = lengths.sumOf { it.toDouble() }.toFloat()
    if (!total.isFinite() || total <= 0f) return null
    var before = 0f
    var bestDistance = Double.POSITIVE_INFINITY
    var best: Triple<Float, Float, Float>? = null
    points.zipWithNext().forEachIndexed { index, (a, b) ->
        val length = lengths[index]
        if (length > 0f) {
            val tangent = (b - a) * (1f / length)
            val relative = point - a
            val along = relative.x * tangent.x + relative.y * tangent.y
            val projection = along.coerceIn(0f, length)
            val delta = point - (a + tangent * projection)
            val distance = delta.x.toDouble() * delta.x + delta.y.toDouble() * delta.y
            if (distance < bestDistance) {
                bestDistance = distance
                best =
                    Triple(
                        ((before + projection) / total).coerceIn(0f, 1f),
                        delta.x * tangent.x + delta.y * tangent.y,
                        -delta.x * tangent.y + delta.y * tangent.x,
                    )
            }
        }
        before += length
    }
    return best?.let { RelationLabelPlacement.Manual(it.first, it.second, it.third) }
}
