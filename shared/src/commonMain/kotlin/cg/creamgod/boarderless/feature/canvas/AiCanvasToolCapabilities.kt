package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.ai.AiDiagramCapabilities
import cg.creamgod.boarderless.domain.model.NodeShape

/** Same repository gate as manual canvas loops. This does not claim provider tool support. */
internal fun aiCanvasToolCapabilities(supportsRelationGeometry: Boolean) = AiDiagramCapabilities(
    setOf(
        NodeShape.RoundedRectangle, NodeShape.Rectangle, NodeShape.Ellipse, NodeShape.Diamond,
        NodeShape.Pill, NodeShape.Parallelogram, NodeShape.Hexagon,
    ),
    NodeColorPresetTokens.toSet(),
    setOf("group"),
    selfLoops = supportsRelationGeometry,
    relationNodeInsertion = true,
)
