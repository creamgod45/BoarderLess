package cg.creamgod.boarderless.feature.canvas

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import cg.creamgod.boarderless.domain.model.*

/** Same command conversion for future canvas, picker preview and custom-shape thumbnails. */
internal fun VectorPath.composePath(): Path = Path().apply {
    fillType = if (style.fillRule == VectorFillRule.EvenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    commands.forEach { command ->
        when (command) {
            is VectorPathCommand.Move -> moveTo(command.point.x, command.point.y)
            is VectorPathCommand.Line -> lineTo(command.point.x, command.point.y)
            is VectorPathCommand.Quadratic -> quadraticTo(command.control.x, command.control.y, command.point.x, command.point.y)
            is VectorPathCommand.Cubic -> cubicTo(command.control1.x, command.control1.y, command.control2.x,
                command.control2.y, command.point.x, command.point.y)
            VectorPathCommand.Close -> close()
        }
    }
}

/** Draw in local viewBox units; caller owns transform/scale and resolves safe theme color tokens. */
internal fun DrawScope.drawVectorPath(vector: VectorPath, colorForToken: (String) -> Color) {
    val path = vector.composePath()
    vector.style.fillColorToken?.let { drawPath(path, colorForToken(it)) }
    vector.style.strokeColorToken?.let {
        drawPath(path, colorForToken(it), style = Stroke(vector.style.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
