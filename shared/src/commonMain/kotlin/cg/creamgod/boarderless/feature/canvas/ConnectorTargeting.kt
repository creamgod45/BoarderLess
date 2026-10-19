package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Vec2

internal fun connectorDropTargetId(
    nodes: Collection<CanvasObject>,
    sourceId: CanvasObjectId,
    worldPoint: Vec2,
): CanvasObjectId? =
    nodes
        .connectableNodes()
        .asSequence()
        .filter { it.id != sourceId && it.transform.containsWorldPoint(worldPoint, it.connectionShape) }
        .maxWithOrNull(compareBy<CanvasObject>({ it.zIndex }, { it.id.value }))
        ?.id
