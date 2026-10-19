package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class Vec2(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "coordinates must be finite" }
    }

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scale: Float): Vec2 = Vec2(x * scale, y * scale)

    operator fun div(scale: Float): Vec2 = Vec2(x / scale, y / scale)

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

@Serializable
data class CanvasSize(
    val width: Float,
    val height: Float,
) {
    init {
        require(width.isFinite() && width >= 0f) { "width must be finite and not negative" }
        require(height.isFinite() && height >= 0f) { "height must be finite and not negative" }
    }

    fun atLeast(minimum: CanvasSize): CanvasSize =
        CanvasSize(
            width = max(width, minimum.width),
            height = max(height, minimum.height),
        )
}

@Serializable
data class CanvasTransform(
    val position: Vec2,
    val size: CanvasSize,
    val rotationDegrees: Float = 0f,
) {
    init {
        require(rotationDegrees.isFinite()) { "rotation must be finite" }
    }
}
