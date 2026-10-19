package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import cg.creamgod.boarderless.domain.export.CanvasExportRaster
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt

internal class CanvasExportPreviewState {
    var result by mutableStateOf<Result<CanvasExportRaster>?>(null)
        internal set
}

/** Preview is scaled for the panel; readback records the entire export rectangle at its true size. */
@Composable internal fun CanvasExportPreview(
    workspace: Workspace, measured: CanvasExportMeasuredScene, mediaFrames: Map<CanvasObjectId, ImageBitmap>,
    background: Color, modifier: Modifier, onRaster: (Result<CanvasExportRaster>) -> Unit = {},
): CanvasExportPreviewState {
    val layer = rememberGraphicsLayer()
    val profile = listOf(workspace, measured, mediaFrames, background, BoarderLessTheme.colors,
        LocalDensity.current, LocalWindowInfo.current.containerSize)
    val state = remember(profile) { CanvasExportPreviewState() }
    val frame = remember(state) { CompletableDeferred<Unit>() }
    val callback by rememberUpdatedState(onRaster)
    val plan = measured.layout
    var previewFit by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(frame) {
        try {
            frame.await()
            val bitmap = layer.toImageBitmap()
            check(bitmap.width == plan.pixelWidth && bitmap.height == plan.pixelHeight)
            val pixels = IntArray(plan.pixelWidth * plan.pixelHeight)
            bitmap.readPixels(pixels)
            val result = Result.success(CanvasExportRaster(plan.pixelWidth, plan.pixelHeight, pixels))
            state.result = result; callback(result)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { val result = Result.failure<CanvasExportRaster>(failure); state.result = result; callback(result) }
    }
    Layout(modifier = modifier.clipToBounds(), content = {
        Box(Modifier.fillMaxSize().drawWithContent {
            try {
                layer.record(size = IntSize(plan.pixelWidth, plan.pixelHeight)) {
                    this@drawWithContent.drawContent()
                }
                // Scale the display of the recorded scene; readback retains its full dimensions.
                withTransform({ scale(previewFit, previewFit, Offset.Zero) }) { drawLayer(layer) }
                frame.complete(Unit)
            } catch (failure: Throwable) { frame.completeExceptionally(failure) }
        }) {
            CanvasExportScene(workspace, plan.viewport, measured.labels, mediaFrames, background, Modifier.fillMaxSize())
        }
    }) { measurables, constraints ->
        val width = constraints.constrainWidth(plan.pixelWidth)
        val height = constraints.constrainHeight(plan.pixelHeight)
        val child = measurables.single().measure(Constraints.fixed(plan.pixelWidth, plan.pixelHeight))
        val fit = minOf(width.toFloat() / plan.pixelWidth, height.toFloat() / plan.pixelHeight)
        previewFit = fit
        layout(width, height) {
            child.place(((width - plan.pixelWidth * fit) / 2f).roundToInt(), ((height - plan.pixelHeight * fit) / 2f).roundToInt())
        }
    }
    return state
}
