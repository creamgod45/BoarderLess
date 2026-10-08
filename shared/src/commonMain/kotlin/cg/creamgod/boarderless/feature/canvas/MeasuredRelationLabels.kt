package cg.creamgod.boarderless.feature.canvas

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.roundToInt

internal data class MeasuredRelationLabel(val text: String, val position: Vec2, val size: CanvasSize)

internal fun measureRelationLabels(
    relations: Collection<Relation>, nodes: Map<CanvasObjectId, CanvasObject>, viewport: Viewport,
    density: Float, dragPreviews: Map<CanvasObjectId, Vec2>, transformPreviews: Map<CanvasObjectId, CanvasTransform>,
    measurer: TextMeasurer, style: TextStyle,
): Map<RelationId, MeasuredRelationLabel> {
    val paths =
        relations
            .mapNotNull { relation ->
                relationSegment(relation, nodes, viewport, dragPreviews, transformPreviews)?.let { relation.id to it.points }
            }.toMap()
    val labels = relations.filter { !it.label.isNullOrBlank() && it.id in paths }
    val inputs =
        labels.map { relation ->
            val measured =
                measurer
                    .measure(
                        relation.label.orEmpty(),
                        style = style,
                        constraints = Constraints(maxWidth = (226f * density).roundToInt().coerceAtLeast(1)),
                    ).size
            RelationLabelInput(
                relation.id,
                paths.getValue(relation.id),
                CanvasSize(measured.width + 14f * density, measured.height + 6f * density),
            )
        }
    val obstacles =
        nodes.map { (id, node) ->
            val transform = transformPreviews[id] ?: node.transform
            val corners =
                rotatedTransformCorners(transform.copy(position = transform.position + (dragPreviews[id] ?: Vec2.Zero)))
                    .map(viewport::worldToScreen)
            LabelBounds(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
        }
    val manual =
        labels
            .mapNotNull { relation ->
                val placement =
                    relation.geometry?.labelPlacement as? cg.creamgod.boarderless.domain.model.RelationLabelPlacement.Manual
                        ?: return@mapNotNull null
                val anchor =
                    manualRelationLabelAnchor(
                        paths.getValue(relation.id).map(viewport::screenToWorld),
                        placement,
                    )?.let(viewport::worldToScreen)
                        ?: return@mapNotNull null
                val size = inputs.single { it.id == relation.id }.size
                relation.id to (anchor - Vec2(size.width / 2f, size.height / 2f))
            }.toMap()
    val manualBounds =
        manual.map { (id, position) ->
            val size = inputs.single { it.id == id }.size
            LabelBounds(position.x, position.y, position.x + size.width, position.y + size.height)
        }
    val placements = layoutRelationLabels(inputs.filter { it.id !in manual }, paths.values, obstacles + manualBounds, 6f * density) + manual
    return labels.mapNotNull { relation ->
        placements[relation.id]?.let { position ->
            relation.id to MeasuredRelationLabel(relation.label.orEmpty(), position, inputs.single { it.id == relation.id }.size)
        }
    }.toMap()
}
