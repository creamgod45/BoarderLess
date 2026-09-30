package cg.creamgod.boarderless.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

enum class ShellIcon {
    Add,
    AlignBottom,
    AlignHorizontalCenter,
    AlignLeft,
    AlignRight,
    AlignTop,
    AlignVerticalCenter,
    AreaSelect,
    Back,
    CanvasStyle,
    Close,
    Copy,
    Cut,
    Delete,
    DistributeHorizontal,
    DistributeVertical,
    Duplicate,
    Fit,
    Forward,
    Grid,
    Group,
    History,
    Layers,
    Library,
    Lock,
    Menu,
    Members,
    Paste,
    QaChecklist,
    Redo,
    Radial,
    Rename,
    Retry,
    Rotate,
    Schemes,
    Search,
    Snap,
    Undo,
    Unlock,
    Workspaces,
    ZoomIn,
    ZoomOut,
    ZoomReset,
}

/** A small, dependency-free icon set shared by every Compose target. */
@Composable
fun ShellIconGlyph(
    icon: ShellIcon,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(17.dp)) {
        drawShellIcon(icon = icon, color = tint)
    }
}

private fun DrawScope.drawShellIcon(icon: ShellIcon, color: Color) {
    val unit = size.minDimension / 20f
    val stroke = 1.75f * unit
    val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), stroke, StrokeCap.Round)
    fun path(block: Path.() -> Unit) {
        drawPath(Path().apply(block), color = color, style = lineStyle)
    }

    when (icon) {
        ShellIcon.Add -> {
            line(10f, 4f, 10f, 16f)
            line(4f, 10f, 16f, 10f)
        }
        ShellIcon.AlignLeft -> {
            line(4f, 3f, 4f, 17f)
            line(4f, 6f, 15f, 6f)
            line(4f, 10f, 11f, 10f)
            line(4f, 14f, 13f, 14f)
        }
        ShellIcon.AlignHorizontalCenter -> {
            line(10f, 2.5f, 10f, 17.5f)
            line(4f, 6f, 16f, 6f)
            line(6f, 10f, 14f, 10f)
            line(5f, 14f, 15f, 14f)
        }
        ShellIcon.AlignRight -> {
            line(16f, 3f, 16f, 17f)
            line(5f, 6f, 16f, 6f)
            line(9f, 10f, 16f, 10f)
            line(7f, 14f, 16f, 14f)
        }
        ShellIcon.AlignTop -> {
            line(3f, 4f, 17f, 4f)
            line(6f, 4f, 6f, 15f)
            line(10f, 4f, 10f, 11f)
            line(14f, 4f, 14f, 13f)
        }
        ShellIcon.AlignVerticalCenter -> {
            line(2.5f, 10f, 17.5f, 10f)
            line(6f, 4f, 6f, 16f)
            line(10f, 6f, 10f, 14f)
            line(14f, 5f, 14f, 15f)
        }
        ShellIcon.AlignBottom -> {
            line(3f, 16f, 17f, 16f)
            line(6f, 5f, 6f, 16f)
            line(10f, 9f, 10f, 16f)
            line(14f, 7f, 14f, 16f)
        }
        ShellIcon.Menu -> {
            line(4f, 5f, 16f, 5f)
            line(4f, 10f, 16f, 10f)
            line(4f, 15f, 16f, 15f)
        }
        ShellIcon.Search -> {
            drawCircle(color, 5.3f * unit, p(8.5f, 8.5f), style = lineStyle)
            line(12.3f, 12.3f, 16.5f, 16.5f)
        }
        ShellIcon.Close -> {
            line(5f, 5f, 15f, 15f)
            line(15f, 5f, 5f, 15f)
        }
        ShellIcon.Copy -> {
            drawRoundRect(color, p(6f, 3f), Size(10f * unit, 11f * unit), CornerRadius(1.6f * unit), lineStyle)
            drawRoundRect(color, p(3.5f, 6f), Size(10f * unit, 11f * unit), CornerRadius(1.6f * unit), lineStyle)
        }
        ShellIcon.Cut -> {
            drawCircle(color, 2.2f * unit, p(5.5f, 14.5f), style = lineStyle)
            drawCircle(color, 2.2f * unit, p(14.5f, 14.5f), style = lineStyle)
            line(7f, 13f, 15.5f, 4.5f)
            line(13f, 13f, 4.5f, 4.5f)
        }
        ShellIcon.Paste -> {
            drawRoundRect(color, p(4f, 5f), Size(12f * unit, 12f * unit), CornerRadius(1.8f * unit), lineStyle)
            drawRoundRect(color, p(7f, 3f), Size(6f * unit, 4f * unit), CornerRadius(1.4f * unit), lineStyle)
            line(7f, 10f, 13f, 10f)
            line(7f, 13f, 11.5f, 13f)
        }
        ShellIcon.Duplicate -> {
            drawRoundRect(color, p(3.5f, 6f), Size(10f * unit, 10f * unit), CornerRadius(1.6f * unit), lineStyle)
            drawRoundRect(color, p(6.5f, 3f), Size(10f * unit, 10f * unit), CornerRadius(1.6f * unit), lineStyle)
            line(11.5f, 5.8f, 11.5f, 10.2f)
            line(9.3f, 8f, 13.7f, 8f)
        }
        ShellIcon.Delete -> {
            line(4.5f, 6f, 15.5f, 6f)
            line(8f, 3.5f, 12f, 3.5f)
            path {
                moveTo(6f * unit, 7f * unit)
                lineTo(7f * unit, 16.5f * unit)
                lineTo(13f * unit, 16.5f * unit)
                lineTo(14f * unit, 7f * unit)
            }
            line(9f, 9f, 9f, 14f)
            line(11f, 9f, 11f, 14f)
        }
        ShellIcon.Grid -> {
            for (x in listOf(4f, 10f)) for (y in listOf(4f, 10f)) {
                drawRoundRect(
                    color = color,
                    topLeft = p(x, y),
                    size = Size(5f * unit, 5f * unit),
                    cornerRadius = CornerRadius(1.2f * unit),
                    style = lineStyle,
                )
            }
        }
        ShellIcon.Group -> {
            drawRoundRect(
                color = color,
                topLeft = p(3f, 3f),
                size = Size(14f * unit, 14f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = lineStyle,
            )
            drawRoundRect(color, p(5.5f, 6f), Size(4f * unit, 4f * unit), CornerRadius(unit), lineStyle)
            drawRoundRect(color, p(10.5f, 10f), Size(4f * unit, 4f * unit), CornerRadius(unit), lineStyle)
        }
        ShellIcon.Layers -> {
            fun diamond(y: Float) = path {
                moveTo(10f * unit, y * unit)
                lineTo(16f * unit, (y + 3f) * unit)
                lineTo(10f * unit, (y + 6f) * unit)
                lineTo(4f * unit, (y + 3f) * unit)
                close()
            }
            diamond(3f)
            path {
                moveTo(4f * unit, 10f * unit)
                lineTo(10f * unit, 13f * unit)
                lineTo(16f * unit, 10f * unit)
            }
            path {
                moveTo(4f * unit, 13f * unit)
                lineTo(10f * unit, 16f * unit)
                lineTo(16f * unit, 13f * unit)
            }
        }
        ShellIcon.Radial -> {
            drawCircle(color, 2.2f * unit, p(10f, 10f), style = lineStyle)
            listOf(p(10f, 3.5f), p(16f, 13.5f), p(4f, 13.5f)).forEach { satellite ->
                drawLine(color, p(10f, 10f), satellite, stroke, StrokeCap.Round)
                drawCircle(color, 1.8f * unit, satellite, style = lineStyle)
            }
        }
        ShellIcon.DistributeHorizontal -> {
            line(4f, 5f, 4f, 15f)
            line(10f, 3f, 10f, 17f)
            line(16f, 5f, 16f, 15f)
            line(6.5f, 10f, 7.5f, 10f)
            line(12.5f, 10f, 13.5f, 10f)
        }
        ShellIcon.DistributeVertical -> {
            line(5f, 4f, 15f, 4f)
            line(3f, 10f, 17f, 10f)
            line(5f, 16f, 15f, 16f)
            line(10f, 6.5f, 10f, 7.5f)
            line(10f, 12.5f, 10f, 13.5f)
        }
        ShellIcon.Library -> {
            drawRoundRect(color, p(3.5f, 3.5f), Size(5.5f * unit, 5.5f * unit), CornerRadius(1.2f * unit), lineStyle)
            drawRoundRect(color, p(11f, 3.5f), Size(5.5f * unit, 5.5f * unit), CornerRadius(1.2f * unit), lineStyle)
            drawRoundRect(color, p(3.5f, 11f), Size(5.5f * unit, 5.5f * unit), CornerRadius(1.2f * unit), lineStyle)
            drawRoundRect(color, p(11f, 11f), Size(5.5f * unit, 5.5f * unit), CornerRadius(1.2f * unit), lineStyle)
        }
        ShellIcon.History -> {
            drawCircle(color, 6.2f * unit, p(10f, 10f), style = lineStyle)
            line(10f, 6f, 10f, 10f)
            line(10f, 10f, 13f, 11.5f)
            line(3.2f, 4.5f, 3.2f, 8f)
            line(3.2f, 4.5f, 6.5f, 4.5f)
        }
        ShellIcon.QaChecklist -> {
            drawRoundRect(color, p(3.5f, 2.8f), Size(13f * unit, 14.5f * unit), CornerRadius(2f * unit), lineStyle)
            path {
                moveTo(6f * unit, 7f * unit)
                lineTo(7.4f * unit, 8.4f * unit)
                lineTo(9.4f * unit, 5.5f * unit)
            }
            line(11f, 7f, 14f, 7f)
            path {
                moveTo(6f * unit, 12f * unit)
                lineTo(7.4f * unit, 13.4f * unit)
                lineTo(9.4f * unit, 10.5f * unit)
            }
            line(11f, 12f, 14f, 12f)
        }
        ShellIcon.Workspaces -> {
            drawRoundRect(color, p(3f, 5f), Size(11f * unit, 10f * unit), CornerRadius(2f * unit), lineStyle)
            drawRoundRect(color, p(6f, 3f), Size(11f * unit, 10f * unit), CornerRadius(2f * unit), lineStyle)
        }
        ShellIcon.Members -> {
            drawCircle(color, 2.6f * unit, p(8f, 7f), style = lineStyle)
            drawCircle(color, 2.1f * unit, p(14f, 8f), style = lineStyle)
            path {
                moveTo(3.5f * unit, 16f * unit)
                cubicTo(4f * unit, 11.5f * unit, 12f * unit, 11.5f * unit, 12.5f * unit, 16f * unit)
            }
            path {
                moveTo(12f * unit, 12.5f * unit)
                cubicTo(15.5f * unit, 11.5f * unit, 17f * unit, 13.5f * unit, 17f * unit, 16f * unit)
            }
        }
        ShellIcon.Schemes -> {
            path {
                moveTo(5f * unit, 3.5f * unit)
                lineTo(14.5f * unit, 3.5f * unit)
                lineTo(14.5f * unit, 16.5f * unit)
                lineTo(10f * unit, 13.8f * unit)
                lineTo(5f * unit, 16.5f * unit)
                close()
            }
            line(8f, 7f, 12f, 7f)
            line(8f, 10f, 11f, 10f)
        }
        ShellIcon.Undo, ShellIcon.Redo -> {
            val mirrored = icon == ShellIcon.Redo
            fun x(value: Float) = if (mirrored) 20f - value else value
            path {
                moveTo(x(16f) * unit, 15f * unit)
                cubicTo(x(16f) * unit, 8f * unit, x(10f) * unit, 6f * unit, x(5f) * unit, 9f * unit)
            }
            line(x(5f), 9f, x(8f), 4.5f)
            line(x(5f), 9f, x(10f), 10f)
        }
        ShellIcon.ZoomIn, ShellIcon.ZoomOut -> {
            drawCircle(color, 5.2f * unit, p(8.5f, 8.5f), style = lineStyle)
            line(12.2f, 12.2f, 16.5f, 16.5f)
            line(5.8f, 8.5f, 11.2f, 8.5f)
            if (icon == ShellIcon.ZoomIn) line(8.5f, 5.8f, 8.5f, 11.2f)
        }
        ShellIcon.ZoomReset -> {
            drawCircle(color, 6f * unit, p(10f, 10f), style = lineStyle)
            drawCircle(color, 1.7f * unit, p(10f, 10f), style = lineStyle)
        }
        ShellIcon.Fit, ShellIcon.AreaSelect -> {
            val inset = if (icon == ShellIcon.Fit) 3.5f else 4.5f
            val extent = if (icon == ShellIcon.Fit) 7.5f else 7f
            line(inset, extent, inset, inset)
            line(inset, inset, extent, inset)
            line(20f - extent, inset, 20f - inset, inset)
            line(20f - inset, inset, 20f - inset, extent)
            line(inset, 20f - extent, inset, 20f - inset)
            line(inset, 20f - inset, extent, 20f - inset)
            line(20f - extent, 20f - inset, 20f - inset, 20f - inset)
            line(20f - inset, 20f - extent, 20f - inset, 20f - inset)
            if (icon == ShellIcon.AreaSelect) drawCircle(color, 1.4f * unit, p(10f, 10f))
        }
        ShellIcon.Snap -> {
            path {
                moveTo(5f * unit, 4f * unit)
                lineTo(5f * unit, 10.5f * unit)
                cubicTo(5f * unit, 18f * unit, 15f * unit, 18f * unit, 15f * unit, 10.5f * unit)
                lineTo(15f * unit, 4f * unit)
            }
            line(5f, 7f, 8f, 7f)
            line(12f, 7f, 15f, 7f)
        }
        ShellIcon.CanvasStyle -> {
            drawCircle(color, 6.2f * unit, p(10f, 10f), style = lineStyle)
            drawCircle(color, 0.9f * unit, p(7f, 8f))
            drawCircle(color, 0.9f * unit, p(11f, 6.5f))
            drawCircle(color, 0.9f * unit, p(14f, 9f))
            path {
                moveTo(10f * unit, 16.2f * unit)
                cubicTo(8f * unit, 14.5f * unit, 11f * unit, 12f * unit, 13f * unit, 14f * unit)
            }
        }
        ShellIcon.Retry -> {
            path {
                moveTo(15.5f * unit, 7f * unit)
                cubicTo(12f * unit, 2.5f * unit, 4.5f * unit, 4.5f * unit, 4.5f * unit, 10.5f * unit)
                cubicTo(4.5f * unit, 16.5f * unit, 12f * unit, 18f * unit, 15.5f * unit, 13f * unit)
            }
            line(15.5f, 7f, 15.5f, 3.5f)
            line(15.5f, 7f, 12f, 7f)
        }
        ShellIcon.Lock, ShellIcon.Unlock -> {
            drawRoundRect(color, p(4.5f, 9f), Size(11f * unit, 8f * unit), CornerRadius(1.8f * unit), lineStyle)
            path {
                moveTo(7f * unit, 9f * unit)
                lineTo(7f * unit, 7f * unit)
                cubicTo(7f * unit, 2.5f * unit, 13f * unit, 2.5f * unit, 13f * unit, 7f * unit)
                if (icon == ShellIcon.Unlock) lineTo(15.5f * unit, 7f * unit)
            }
            drawCircle(color, 1f * unit, p(10f, 13f))
        }
        ShellIcon.Rotate -> {
            path {
                moveTo(15.5f * unit, 7.5f * unit)
                cubicTo(13f * unit, 3f * unit, 6f * unit, 3.5f * unit, 4.5f * unit, 9f * unit)
                cubicTo(3f * unit, 14.5f * unit, 9.5f * unit, 18f * unit, 14f * unit, 14f * unit)
            }
            line(15.5f, 7.5f, 11.5f, 7f)
            line(15.5f, 7.5f, 16f, 3.5f)
        }
        ShellIcon.Rename -> {
            path {
                moveTo(4f * unit, 15.5f * unit)
                lineTo(6f * unit, 10.5f * unit)
                lineTo(13f * unit, 3.5f * unit)
                lineTo(16.5f * unit, 7f * unit)
                lineTo(9.5f * unit, 14f * unit)
                close()
            }
            line(4f, 15.5f, 9.5f, 14f)
        }
        ShellIcon.Forward, ShellIcon.Back -> {
            val up = icon == ShellIcon.Forward
            val a = if (up) 5f else 15f
            val b = if (up) 15f else 5f
            line(10f, b, 10f, a)
            line(10f, a, 6f, if (up) 9f else 11f)
            line(10f, a, 14f, if (up) 9f else 11f)
            line(5f, if (up) 17f else 3f, 15f, if (up) 17f else 3f)
        }
    }
}
