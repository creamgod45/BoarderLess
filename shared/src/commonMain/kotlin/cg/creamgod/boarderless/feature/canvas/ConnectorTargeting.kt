package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2

internal fun connectorDropTargetId(
    nodes: Collection<TextNode>,
    sourceId: CanvasObjectId,
    worldPoint: Vec2,
): CanvasObjectId? = nodes
    .asSequence()
    .filter { it.id != sourceId && it.transform.containsWorldPoint(worldPoint, it.shape) }
    .maxWithOrNull(compareBy<TextNode>({ it.zIndex }, { it.id.value }))
    ?.id
