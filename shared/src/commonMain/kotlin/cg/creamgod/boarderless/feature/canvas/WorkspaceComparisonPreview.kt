package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.WorkspaceDraftReview
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.export.CanvasPaintBounds
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** One viewport for all three versions so switching never hides a move by re-fitting it. */
internal fun comparisonPreviewViewport(paint: List<CanvasPaintBounds>, width: Float, height: Float): Viewport {
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
    if (paint.isEmpty()) return Viewport()
    val left = paint.minOf { it.left }.toDouble(); val right = paint.maxOf { it.right }.toDouble()
    val top = paint.minOf { it.top }.toDouble(); val bottom = paint.maxOf { it.bottom }.toDouble()
    val contentWidth = (right - left).coerceAtLeast(1.0)
    val contentHeight = (bottom - top).coerceAtLeast(1.0)
    val zoom = minOf((width - 32.0).coerceAtLeast(1.0) / contentWidth,
        (height - 32.0).coerceAtLeast(1.0) / contentHeight, 1.0).toFloat()
    return Viewport(Vec2(((width - contentWidth * zoom) / 2 - left * zoom).toFloat(),
        ((height - contentHeight * zoom) / 2 - top * zoom).toFloat()), zoom)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceComparisonPreview(review: WorkspaceDraftReview, merged: Workspace? = null) {
    val colors = BoarderLessTheme.colors
    var selected by remember(review) { mutableIntStateOf(1) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val versions = listOf(review.baseline, review.proposed, review.current) + listOfNotNull(merged)
    val labels = listOf(Strings.draftReview.baseline(), Strings.draftReview.proposed(), Strings.draftReview.remote()) +
        if (merged != null) listOf(Strings.draftReview.mergedPreview()) else emptyList()
    val paint = versions.map { rememberCanvasPaintMeasurement(it, 1f) }
    val bounds = paint.mapNotNull { it.getOrNull() }.flatMap { it.bounds }
    val index = selected.coerceAtMost(versions.lastIndex)
    val viewport = remember(bounds, size) {
        comparisonPreviewViewport(bounds, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
    }
    val scene = rememberCanvasPaintMeasurement(versions[index], viewport.zoom)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label -> ShellButton(label, accent = index == i, onClick = { selected = i }) }
        }
        BasicText(Strings.draftReview.previewHint(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        Box(Modifier.fillMaxWidth().height(280.dp).clipToBounds().onSizeChanged { size = it }) {
            if (paint.any { it.isFailure } || scene.isFailure) {
                BasicText(Strings.draftReview.previewUnavailable(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            } else if (bounds.isEmpty()) {
                BasicText(Strings.draftReview.previewEmpty(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            } else {
                CanvasExportScene(versions[index], viewport, scene.getOrThrow().labels, emptyMap(),
                    canvasBackgroundColor(versions[index].canvasStyle.backgroundToken, colors), Modifier.fillMaxSize(),
                    readOnlyMediaPlaceholders = true)
            }
        }
    }
}
