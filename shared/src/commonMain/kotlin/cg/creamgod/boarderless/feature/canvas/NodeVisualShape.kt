package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cg.creamgod.boarderless.domain.model.NodeShape

/** Outline with holes for the expansion shapes; stroke-only shapes have an empty (unfilled) outline. */
private fun artComposeShape(art: NodeShapeArt): Shape =
    // A stroke-only outline has no area; an empty Generic outline would crash Compose's border cache,
    // so it uses a plain rectangle and callers draw neither fill nor border for it (see drawsFilledOutline).
    if (art.strokeOnly) RoundedCornerShape(0.dp) else GenericShape { size, _ ->
        fillType = androidx.compose.ui.graphics.PathFillType.NonZero
        fun add(points: List<cg.creamgod.boarderless.domain.model.Vec2>, clockwise: Boolean) {
            if (points.size < 3) return
            val ordered = if (signedAreaIsClockwise(points) == clockwise) points else points.reversed()
            ordered.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x * size.width, point.y * size.height) else lineTo(point.x * size.width, point.y * size.height)
            }
            close()
        }
        art.body?.let { add(it, clockwise = true) }
        art.holes.forEach { add(it, clockwise = false) }
    }

internal fun NodeShape.composeShape(): Shape =
    art()?.let { return artComposeShape(it) } ?: when (this) {
        NodeShape.RoundedRectangle -> {
            RoundedCornerShape(16.dp)
        }

        NodeShape.Rectangle -> {
            RoundedCornerShape(3.dp)
        }

        NodeShape.PlainText -> {
            RoundedCornerShape(0.dp)
        }

        NodeShape.Pill -> {
            RoundedCornerShape(percent = 50)
        }

        NodeShape.Ellipse -> {
            GenericShape { size, _ ->
                addOval(Rect(Offset.Zero, size))
            }
        }

        NodeShape.Diamond -> {
            GenericShape { size, _ ->
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
        }

        NodeShape.Parallelogram -> {
            GenericShape { size, _ ->
                val skew = size.width * 0.16f
                moveTo(skew, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - skew, size.height)
                lineTo(0f, size.height)
                close()
            }
        }

        NodeShape.Hexagon -> {
            GenericShape { size, _ ->
                val inset = size.width * 0.18f
                moveTo(inset, 0f)
                lineTo(size.width - inset, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width - inset, size.height)
                lineTo(inset, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
        }

        NodeShape.Document -> {
            GenericShape { size, _ ->
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.86f)
                cubicTo(
                    size.width * 0.82f,
                    size.height * 0.74f,
                    size.width * 0.68f,
                    size.height * 0.74f,
                    size.width * 0.5f,
                    size.height * 0.86f,
                )
                cubicTo(
                    size.width * 0.32f,
                    size.height * 0.98f,
                    size.width * 0.18f,
                    size.height * 0.98f,
                    0f,
                    size.height * 0.86f,
                )
                close()
            }
        }

        NodeShape.Database -> {
            GenericShape { size, _ ->
                moveTo(0f, size.height * 0.15f)
                cubicTo(0f, -size.height * 0.05f, size.width, -size.height * 0.05f, size.width, size.height * 0.15f)
                lineTo(size.width, size.height * 0.85f)
                cubicTo(size.width, size.height * 1.05f, 0f, size.height * 1.05f, 0f, size.height * 0.85f)
                close()
            }
        }

        else -> {
            GenericShape { size, _ ->
                polygonVertices()?.forEachIndexed { index, point ->
                    if (index == 0) {
                        moveTo(point.x * size.width, point.y * size.height)
                    } else {
                        lineTo(point.x * size.width, point.y * size.height)
                    }
                }
                close()
            }
        }
    }

internal fun NodeShape.horizontalContentPadding(width: Dp): Dp =
    art()?.let { art -> art.textRect?.let { width * maxOf(it.left, 1f - it.right) } ?: (width * .25f) } ?: when (this) {
        NodeShape.Diamond -> width * 0.24f
        NodeShape.Ellipse -> width * 0.15f
        NodeShape.Parallelogram, NodeShape.Hexagon -> width * 0.18f
        NodeShape.Document, NodeShape.Database -> width * 0.12f
        NodeShape.Pill -> width * 0.12f
        NodeShape.RoundedRectangle, NodeShape.Rectangle -> 18.dp
        NodeShape.PlainText -> 8.dp
        NodeShape.Plus -> width * .35f
        NodeShape.Star -> width * .38f
        else -> width * .28f
    }

internal fun NodeShape.verticalContentPadding(height: Dp): Dp =
    art()?.let { art -> art.textRect?.let { height * maxOf(it.top, 1f - it.bottom) } ?: (height * .25f) } ?: when (this) {
        NodeShape.Diamond -> height * 0.18f
        NodeShape.Ellipse, NodeShape.Pill -> height * 0.12f
        NodeShape.Document, NodeShape.Database -> height * 0.16f
        NodeShape.RoundedRectangle, NodeShape.Rectangle, NodeShape.Parallelogram, NodeShape.Hexagon -> 18.dp
        NodeShape.PlainText -> 8.dp
        NodeShape.Plus -> height * .35f
        NodeShape.Star -> height * .40f
        else -> height * .30f
    }

internal fun DrawScope.drawNodePreviewShape(
    shape: NodeShape,
    color: Color,
    borderColor: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: Float,
    borderWidth: Float,
) {
    when (shape) {
        NodeShape.PlainText -> { // Text glyph hint, not a card fill or private text rendered into UI chrome.
            repeat(3) { line ->
                val y = topLeft.y + size.height * (.25f + line * .22f)
                drawLine(
                    color,
                    Offset(topLeft.x + size.width * .08f, y),
                    Offset(topLeft.x + size.width * (if (line == 2) .62f else .90f), y),
                    strokeWidth = borderWidth,
                )
            }
        }

        NodeShape.RoundedRectangle -> {
            val radius = CornerRadius(cornerRadius, cornerRadius)
            drawRoundRect(color, topLeft, size, radius)
            drawRoundRect(borderColor, topLeft, size, radius, style = Stroke(borderWidth))
        }

        NodeShape.Rectangle -> {
            drawRect(color, topLeft, size)
            drawRect(borderColor, topLeft, size, style = Stroke(borderWidth))
        }

        NodeShape.Pill -> {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color, topLeft, size, radius)
            drawRoundRect(borderColor, topLeft, size, radius, style = Stroke(borderWidth))
        }

        else -> {
            val path = previewPath(shape, topLeft, size)
            drawPath(path, color)
            drawPath(path, borderColor, style = Stroke(borderWidth))
        }
    }
    shape.art()?.let { drawNodeShapeDecor(it, borderColor, borderWidth, topLeft, size, densityScale = 1f) }
    if (shape == NodeShape.Database) {
        drawArc(
            color = borderColor,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(size.width, size.height * 0.3f),
            style = Stroke(borderWidth),
        )
    }
}

private fun previewPath(
    shape: NodeShape,
    topLeft: Offset,
    size: Size,
): Path =
    Path().apply {
        fun x(value: Float) = topLeft.x + size.width * value

        fun y(value: Float) = topLeft.y + size.height * value
        when (shape) {
            NodeShape.Ellipse -> {
                addOval(Rect(topLeft, size))
            }

            NodeShape.Diamond -> {
                moveTo(x(0.5f), y(0f))
                lineTo(x(1f), y(0.5f))
                lineTo(x(0.5f), y(1f))
                lineTo(x(0f), y(0.5f))
                close()
            }

            NodeShape.Parallelogram -> {
                moveTo(x(0.16f), y(0f))
                lineTo(x(1f), y(0f))
                lineTo(x(0.84f), y(1f))
                lineTo(x(0f), y(1f))
                close()
            }

            NodeShape.Hexagon -> {
                moveTo(x(0.18f), y(0f))
                lineTo(x(0.82f), y(0f))
                lineTo(x(1f), y(0.5f))
                lineTo(x(0.82f), y(1f))
                lineTo(x(0.18f), y(1f))
                lineTo(x(0f), y(0.5f))
                close()
            }

            NodeShape.Document -> {
                moveTo(x(0f), y(0f))
                lineTo(x(1f), y(0f))
                lineTo(x(1f), y(0.86f))
                cubicTo(x(0.82f), y(0.74f), x(0.68f), y(0.74f), x(0.5f), y(0.86f))
                cubicTo(x(0.32f), y(0.98f), x(0.18f), y(0.98f), x(0f), y(0.86f))
                close()
            }

            NodeShape.Database -> {
                moveTo(x(0f), y(0.15f))
                cubicTo(x(0f), y(-0.05f), x(1f), y(-0.05f), x(1f), y(0.15f))
                lineTo(x(1f), y(0.85f))
                cubicTo(x(1f), y(1.05f), x(0f), y(1.05f), x(0f), y(0.85f))
                close()
            }

            NodeShape.RoundedRectangle, NodeShape.Rectangle, NodeShape.Pill -> {
                Unit
            }

            else -> {
                shape.polygonVertices()?.forEachIndexed { index, point ->
                    if (index == 0) moveTo(x(point.x), y(point.y)) else lineTo(x(point.x), y(point.y))
                }
                close()
                shape.art()?.holes?.forEach { hole ->
                    hole.reversed().forEachIndexed { index, point ->
                        if (index == 0) moveTo(x(point.x), y(point.y)) else lineTo(x(point.x), y(point.y))
                    }
                    close()
                }
            }
        }
    }


/** False for text-only and stroke-only shapes, which draw neither a fill nor a border around their box. */
internal val NodeShape.drawsFilledOutline: Boolean get() = this != NodeShape.PlainText && art()?.strokeOnly != true

/** Whether the shape offers a text area to edit; existing text is still rendered either way. */
internal val NodeShape.offersTextEditing: Boolean get() = art()?.let { it.textRect != null } ?: true

/**
 * Lines, dots and shaded parts of the expansion shapes, drawn over the node fill with the node border
 * colour. Normalized art coordinates are scaled to [size]; dot offsets and radii are in dp.
 */
internal fun DrawScope.drawNodeShapeDecor(
    art: NodeShapeArt,
    borderColor: Color,
    borderWidth: Float,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    densityScale: Float = 1f,
) {
    fun point(p: cg.creamgod.boarderless.domain.model.Vec2) = Offset(topLeft.x + p.x * size.width, topLeft.y + p.y * size.height)
    art.shaded.forEach { polygon ->
        if (polygon.size >= 3) {
            val path = Path().apply {
                polygon.forEachIndexed { index, p -> point(p).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                close()
            }
            drawPath(path, borderColor.copy(alpha = .16f))
        }
    }
    art.lines.forEach { line ->
        val path = Path().apply {
            line.forEachIndexed { index, p -> point(p).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, borderColor, style = Stroke(borderWidth))
    }
    art.dots.forEach { dot ->
        val center = Offset(topLeft.x + dot.anchorX * size.width + dot.offsetXDp * densityScale, topLeft.y + dot.anchorY * size.height + dot.offsetYDp * densityScale)
        drawCircle(borderColor, radius = dot.radiusDp * densityScale, center = center)
    }
}
