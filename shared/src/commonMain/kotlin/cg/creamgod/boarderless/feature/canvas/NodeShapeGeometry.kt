package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal fun CanvasTransform.containsWorldPoint(
    point: Vec2,
    shape: NodeShape,
): Boolean {
    val center = position + Vec2(size.width / 2f, size.height / 2f)
    val local = rotateVector(point - center, -rotationDegrees)
    return shape.containsLocalPoint(local, size.width / 2f, size.height / 2f)
}

internal fun shapeBoundaryWorldPoint(
    transform: CanvasTransform,
    shape: NodeShape,
    toward: Vec2,
): Vec2 {
    val center = transform.position + Vec2(transform.size.width / 2f, transform.size.height / 2f)
    val origin = shapeConnectionOriginWorldPoint(transform, shape)
    val localDirection = rotateVector(toward - origin, -transform.rotationDegrees)
    val length = sqrt(localDirection.x * localDirection.x + localDirection.y * localDirection.y)
    if (length < 0.001f) return origin
    val unit = localDirection / length
    shape.polygonVertices()?.let { vertices ->
        val distance =
            polygonRayBoundary(
                vertices,
                unit,
                transform.size.width,
                transform.size.height,
                rotateVector(origin - center, -transform.rotationDegrees),
            )
        if (distance != null) return origin + rotateVector(unit * distance, transform.rotationDegrees)
    }
    var inside = 0f
    var outside = max(transform.size.width, transform.size.height) * 1.5f
    repeat(24) {
        val candidate = (inside + outside) / 2f
        if (shape.containsLocalPoint(
                point = unit * candidate,
                halfWidth = transform.size.width / 2f,
                halfHeight = transform.size.height / 2f,
            )
        ) {
            inside = candidate
        } else {
            outside = candidate
        }
    }
    return center + rotateVector(unit * inside, transform.rotationDegrees)
}

/** Bounding-box centre is on the hypotenuse of a right triangle; use its interior centroid. */
internal fun shapeConnectionOriginWorldPoint(
    transform: CanvasTransform,
    shape: NodeShape,
): Vec2 {
    val center = transform.position + Vec2(transform.size.width / 2f, transform.size.height / 2f)
    val local = if (shape == NodeShape.RightTriangle) Vec2(-transform.size.width / 6f, transform.size.height / 6f) else Vec2.Zero
    return center + rotateVector(local, transform.rotationDegrees)
}

internal fun NodeShape.containsLocalPoint(
    point: Vec2,
    halfWidth: Float,
    halfHeight: Float,
): Boolean {
    if (!halfWidth.isFinite() || !halfHeight.isFinite() || halfWidth <= 0f || halfHeight <= 0f ||
        !point.x.isFinite() || !point.y.isFinite()
    ) {
        return false
    }
    val x = point.x / halfWidth
    val y = point.y / halfHeight
    if (abs(x) > 1f || abs(y) > 1f) return false
    polygonVertices()?.let { return polygonContains(it, Vec2((x + 1f) / 2f, (y + 1f) / 2f)) }
    return when (this) {
        NodeShape.RoundedRectangle, NodeShape.Rectangle, NodeShape.PlainText -> {
            true
        }

        NodeShape.Ellipse -> {
            x * x + y * y <= 1f
        }

        NodeShape.Diamond -> {
            abs(x) + abs(y) <= 1f
        }

        NodeShape.Pill -> {
            if (halfWidth >= halfHeight) {
                val core = 1f - halfHeight / halfWidth
                abs(x) <= core || ((abs(x) - core) / (1f - core)).let { dx -> dx * dx + y * y <= 1f }
            } else {
                val core = 1f - halfWidth / halfHeight
                abs(y) <= core || ((abs(y) - core) / (1f - core)).let { dy -> x * x + dy * dy <= 1f }
            }
        }

        NodeShape.Parallelogram -> {
            abs(y) <= 1f && abs(x + y * 0.16f) <= 1f
        }

        NodeShape.Hexagon -> {
            abs(y) <= 1f && abs(x) <= 1f - abs(y) * 0.18f
        }

        NodeShape.Document -> {
            val bottom = 0.72f + 0.16f * sin(PI.toFloat() * (x + 1f))
            abs(x) <= 1f && y >= -1f && y <= bottom
        }

        NodeShape.Database -> {
            when {
                abs(x) > 1f || abs(y) > 1f -> false
                y < -0.7f -> x * x + ((y + 0.7f) / 0.3f).let { it * it } <= 1f
                y > 0.7f -> x * x + ((y - 0.7f) / 0.3f).let { it * it } <= 1f
                else -> true
            }
        }

        else -> {
            false
        } // Polygon outlines handled above; unknown additions fail closed.
    }
}
