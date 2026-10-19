package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.roundToInt

/** A floating child of the owner's transform layer. No shape clip or height truncation. */
@Composable internal fun NodeTextLayer(
    placement: TextPlacement?,
    box: CanvasSize,
    zoom: Float,
    defaultAnchor: TextAnchor,
    padding: Float,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, _ ->
        val width = box.width.roundToInt().coerceAtLeast(1)
        val height = box.height.roundToInt().coerceAtLeast(1)
        val child = measurables.single().measure(Constraints(maxWidth = (width - padding * 2).roundToInt().coerceAtLeast(1)))
        val local = textLayerPosition(placement?.anchor ?: defaultAnchor, box, CanvasSize(child.width.toFloat(), child.height.toFloat()), padding, 8f * zoom)
        val x = local.x + (placement?.offsetX ?: 0f) * zoom
        val y = local.y + (placement?.offsetY ?: 0f) * zoom
        layout(width, height) { child.place(x.roundToInt(), y.roundToInt()) }
    }
}
