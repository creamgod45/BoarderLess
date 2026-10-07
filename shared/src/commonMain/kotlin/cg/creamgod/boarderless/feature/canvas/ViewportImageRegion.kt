package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.ViewportImageRequest
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import kotlin.math.*

/** Conservative inverse-rotated viewport bounds, accounting for ContentScale.Fit letterboxing. */
internal fun viewportImageRequest(
    transform: CanvasTransform,
    viewport: Viewport,
    canvasWidth: Int,
    canvasHeight: Int,
    imageWidth: Int?,
    imageHeight: Int?,
): ViewportImageRequest? {
    if (canvasWidth <= 0 || canvasHeight <= 0 || viewport.zoom <= 0) return null
    val w = transform.size.width
    val h = transform.size.height
    val iw = imageWidth?.toFloat() ?: w
    val ih = imageHeight?.toFloat() ?: h
    val fit = min(w / iw, h / ih)
    val fittedW = iw * fit
    val fittedH = ih * fit
    val insetX = (w - fittedW) / 2
    val insetY = (h - fittedH) / 2
    val angle = -transform.rotationDegrees * PI.toFloat() / 180f
    val corners =
        listOf(
            Vec2.Zero,
            Vec2(canvasWidth.toFloat(), 0f),
            Vec2(0f, canvasHeight.toFloat()),
            Vec2(canvasWidth.toFloat(), canvasHeight.toFloat()),
        ).map { point ->
            val world = viewport.screenToWorld(point) - transform.position - Vec2(w / 2, h / 2)
            Vec2(world.x * cos(angle) - world.y * sin(angle) + w / 2, world.x * sin(angle) + world.y * cos(angle) + h / 2)
        }
    val left = ((corners.minOf { it.x } - insetX) / fittedW).coerceIn(0f, 1f)
    val top = ((corners.minOf { it.y } - insetY) / fittedH).coerceIn(0f, 1f)
    val right = ((corners.maxOf { it.x } - insetX) / fittedW).coerceIn(0f, 1f)
    val bottom = ((corners.maxOf { it.y } - insetY) / fittedH).coerceIn(0f, 1f)
    if (left >= right || top >= bottom) return null
    return ViewportImageRequest(
        left,
        top,
        right,
        bottom,
        (fittedW * viewport.zoom).toInt().coerceIn(1, 1_000_000),
        (fittedH * viewport.zoom).toInt().coerceIn(1, 1_000_000),
    )
}
