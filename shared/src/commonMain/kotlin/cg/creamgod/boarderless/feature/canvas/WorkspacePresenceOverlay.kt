package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.WorkspacePresencePeer
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.model.Viewport
import cg.creamgod.boarderless.domain.model.Workspace

/** Draw-only overlay; no pointer handlers, focus request or changes to local selection. */
@Composable internal fun WorkspacePresenceOverlay(
    peers: List<WorkspacePresencePeer>,
    viewport: Viewport,
    workspace: Workspace,
) {
    if (peers.isEmpty()) return
    val colors = BoarderLessTheme.colors
    val textMeasurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        peers.forEach peerLoop@{ peer ->
            peer.selectedIds.forEach selectionLoop@{ id ->
                val node = workspace.objectById(id) ?: return@selectionLoop
                val points =
                    runCatching { rotatedTransformCorners(node.transform).map(viewport::worldToScreen) }
                        .getOrNull() ?: return@selectionLoop
                val outline =
                    Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                drawPath(
                    outline,
                    colors.accent.copy(alpha = 0.55f),
                    style =
                        Stroke(
                            2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }
            val cursor = peer.cursor ?: return@peerLoop
            val point = runCatching { viewport.worldToScreen(cursor) }.getOrNull() ?: return@peerLoop
            if (point.x !in 0f..size.width || point.y !in 0f..size.height) return@peerLoop
            val marker =
                Path().apply {
                    moveTo(point.x, point.y)
                    lineTo(point.x + 10.dp.toPx(), point.y + 14.dp.toPx())
                    lineTo(point.x + 2.dp.toPx(), point.y + 11.dp.toPx())
                    close()
                }
            drawPath(marker, colors.accent)
            drawText(
                textMeasurer,
                peer.displayName,
                topLeft = Offset(point.x + 12.dp.toPx(), point.y + 8.dp.toPx()),
                style = TextStyle(color = colors.contentText, fontSize = 11.sp),
            )
        }
    }
}
