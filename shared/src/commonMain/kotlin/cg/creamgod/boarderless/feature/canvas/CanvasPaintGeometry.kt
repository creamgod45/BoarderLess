package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.export.CanvasPaintBounds
import cg.creamgod.boarderless.domain.model.*

internal fun objectLocalPaintBounds(transform: CanvasTransform, local: CanvasPaintBounds, outset: Float = 0f): CanvasPaintBounds {
    require(outset.isFinite() && outset >= 0f)
    val center = Vec2(transform.size.width / 2f, transform.size.height / 2f)
    val corners = listOf(Vec2(local.left, local.top), Vec2(local.right, local.top),
        Vec2(local.right, local.bottom), Vec2(local.left, local.bottom)).map {
        transform.position + center + rotateVector(it - center, transform.rotationDegrees)
    }
    return CanvasPaintBounds(corners.minOf { it.x } - outset, corners.minOf { it.y } - outset,
        corners.maxOf { it.x } + outset, corners.maxOf { it.y } + outset)
}

/** Bezier curves lie inside their control-point hull; round caps/joins fit the stroke outset. */
internal fun vectorPaintBounds(transform: CanvasTransform, path: VectorPath): CanvasPaintBounds {
    val points = path.commands.flatMap { it.points() }
    val sx = transform.size.width / path.viewBox.width; val sy = transform.size.height / path.viewBox.height
    val local = CanvasPaintBounds(points.minOf { it.x } * sx, points.minOf { it.y } * sy,
        points.maxOf { it.x } * sx, points.maxOf { it.y } * sy)
    return objectLocalPaintBounds(transform, local,
        if (path.style.strokeColorToken != null) path.style.strokeWidth * maxOf(sx, sy) / 2f else 0f)
}
