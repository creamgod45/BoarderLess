package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.roundToInt

internal data class CanvasExportMeasuredScene(val layout: CanvasExportLayout, val labels: Map<RelationId, MeasuredRelationLabel>)

@Composable internal fun rememberCanvasExportMeasurement(workspace: Workspace, scale: Float, runtime: CanvasExportRuntime = CanvasExportRuntime.Unavailable): Result<CanvasExportMeasuredScene> {
    val measurer = rememberTextMeasurer(cacheSize = 1024)
    val density = LocalDensity.current
    val colors = BoarderLessTheme.colors
    return remember(workspace, scale, runtime, measurer, density, colors) {
        runCatching {
            require(scale.isFinite() && scale > 0f && scale <= 8f)
            val membership = SequenceCanvasMembership(workspace)
            val paint = mutableListOf<CanvasPaintBounds>()
            workspace.objects.values.filterNot { it.id in membership.internalIds }.forEach { obj ->
                val size = obj.transform.size
                // Conservative shadow/rounding extent. Plain/vector text has no shadow.
                val shadow = if (obj is TextNode && (obj.vectorPath != null || obj.shape == NodeShape.PlainText)) 2f
                    else 24f * density.density
                paint += objectLocalPaintBounds(obj.transform, CanvasPaintBounds(0f, 0f, size.width, size.height), shadow / scale)
            }
            membership.visibleNodes(workspace).forEach { node ->
                node.vectorPath?.let { paint += vectorPaintBounds(node.transform, it) }
                val box = CanvasSize(node.transform.size.width * scale, node.transform.size.height * scale)
                val padding = 8f * density.density * scale
                val maxWidth = (box.width.roundToInt() - padding * 2).roundToInt().coerceAtLeast(1)
                val text = measurer.measure(node.text,
                    labelTextStyle(node.textPlacement?.appearance, scale, colors.contentText),
                    constraints = Constraints(minWidth = maxWidth, maxWidth = maxWidth)).size
                val plain = node.vectorPath != null || node.shape == NodeShape.PlainText
                val centered = node.shape != NodeShape.RoundedRectangle && !plain
                val placement = node.textPlacement
                val position = textLayerPosition(placement?.anchor ?: if (centered) TextAnchor.Center else TextAnchor.TopLeft,
                    box, CanvasSize(text.width.toFloat(), text.height.toFloat()), padding, 8f * scale) +
                    Vec2((placement?.offsetX ?: 0f) * scale, (placement?.offsetY ?: 0f) * scale)
                val x = position.x.roundToInt() / scale; val y = position.y.roundToInt() / scale
                paint += objectLocalPaintBounds(node.transform,
                    CanvasPaintBounds(x, y, x + text.width / scale, y + text.height / scale), 2f / scale)
            }
            val viewport = Viewport(zoom = scale)
            val nodes = workspace.objects.values.connectableNodes().associateBy { it.id }
            val relations = membership.visibleRelations(workspace)
            val routes = relations.map { relation ->
                checkNotNull(relationSegment(relation, nodes, viewport, emptyMap(), emptyMap())) { "Cannot render exported connection" }.points
            }
            routes.forEach { points ->
                val outset = 16f * density.density / scale // Includes arrow head and round line caps.
                paint += CanvasPaintBounds(points.minOf { it.x } / scale - outset, points.minOf { it.y } / scale - outset,
                    points.maxOf { it.x } / scale + outset, points.maxOf { it.y } / scale + outset)
            }
            val labels = measureRelationLabels(relations, nodes, viewport, density.density, emptyMap(), emptyMap(), measurer,
                TextStyle(color = colors.contentText, fontSize = 10.sp, fontWeight = FontWeight.Medium))
            labels.values.forEach { label ->
                paint += CanvasPaintBounds((label.position.x - 2f) / scale, (label.position.y - 2f) / scale,
                    (label.position.x + label.size.width + 2f) / scale, (label.position.y + label.size.height + 2f) / scale)
            }
            CanvasExportMeasuredScene(canvasExportLayout(paint, scale = scale, maxDimension = runtime.maxDimension, maxPixels = runtime.maxPixels), labels)
        }
    }
}
