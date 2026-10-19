package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*

internal fun Modifier.mediaPaintBounds(position: Vec2, width: Dp, height: Dp,
    rotation: Float, selected: Boolean, colors: BoarderLessColors): Modifier =
    canvasObjectBounds(position, width, height)
        .graphicsLayer(rotationZ = rotation)
        .shadow(if (selected) 12.dp else 5.dp, RoundedCornerShape(16.dp))
        .clip(RoundedCornerShape(16.dp))
        .background(colors.canvas.copy(alpha = 0.94f))
        .border(if (selected) 2.dp else 1.dp, if (selected) colors.selection else colors.contentBorder, RoundedCornerShape(16.dp))

/** A verified frozen frame; never shows an unavailable placeholder or starts playback. */
@Composable internal fun CanvasExportMediaCard(node: MediaNode, frame: ImageBitmap, viewport: Viewport) {
    val density = LocalDensity.current
    val size = node.transform.size
    Box(Modifier.mediaPaintBounds(viewport.worldToScreen(node.transform.position),
        with(density) { (size.width * viewport.zoom).toDp() },
        with(density) { (size.height * viewport.zoom).toDp() }, node.transform.rotationDegrees,
        false, BoarderLessTheme.colors)) {
        Image(frame, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
