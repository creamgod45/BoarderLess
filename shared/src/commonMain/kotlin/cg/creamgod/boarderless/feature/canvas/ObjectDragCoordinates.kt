package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pointer deltas inside a graphicsLayer are local to its rotation and visual scale. */
internal fun objectLocalDragToWorld(
    delta: Vec2,
    rotationDegrees: Float,
    viewportZoom: Float,
    visualScale: Float = 1f,
): Vec2 {
    require(rotationDegrees.isFinite() && viewportZoom.isFinite() && viewportZoom > 0f)
    require(visualScale.isFinite() && visualScale > 0f)
    val radians = (rotationDegrees % 360f) * PI.toFloat() / 180f
    val factor = visualScale / viewportZoom
    return Vec2(
        (delta.x * cos(radians) - delta.y * sin(radians)) * factor,
        (delta.x * sin(radians) + delta.y * cos(radians)) * factor,
    )
}
