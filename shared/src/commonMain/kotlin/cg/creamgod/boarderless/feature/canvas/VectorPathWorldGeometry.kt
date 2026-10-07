package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun CanvasTransform.containsVectorWorldPoint(path: VectorPath, point: Vec2): Boolean {
    val local = vectorLocalPoint(path, point) ?: return false
    return path.containsFill(local) || path.containsStroke(local)
}

/** Returns contour/centreline intersection; exact stroked silhouette offset remains a caller concern. */
internal fun vectorBoundaryWorldPoint(transform: CanvasTransform, path: VectorPath, toward: Vec2): Vec2? {
    val local = transform.vectorLocalPoint(path, toward) ?: return null
    val origin = Vec2(path.viewBox.width / 2f, path.viewBox.height / 2f)
    val boundary = path.firstBoundaryAlongRay(origin, local - origin) ?: return null
    val angle = (transform.rotationDegrees.toDouble() % 360.0) * PI / 180
    val x = (boundary.x - origin.x) * transform.size.width / path.viewBox.width
    val y = (boundary.y - origin.y) * transform.size.height / path.viewBox.height
    return Vec2((transform.position.x + transform.size.width / 2.0 + x * cos(angle) - y * sin(angle)).toFloat(),
        (transform.position.y + transform.size.height / 2.0 + x * sin(angle) + y * cos(angle)).toFloat())
}

private fun CanvasTransform.vectorLocalPoint(path: VectorPath, point: Vec2): Vec2? {
    if (size.width <= 0f || size.height <= 0f) return null
    val angle = (rotationDegrees.toDouble() % 360.0) * PI / 180
    val x = point.x - (position.x + size.width / 2.0)
    val y = point.y - (position.y + size.height / 2.0)
    val localX = ((x * cos(angle) + y * sin(angle)) * path.viewBox.width / size.width + path.viewBox.width / 2.0).toFloat()
    val localY = ((-x * sin(angle) + y * cos(angle)) * path.viewBox.height / size.height + path.viewBox.height / 2.0).toFloat()
    return if (localX.isFinite() && localY.isFinite()) Vec2(localX, localY) else null
}
