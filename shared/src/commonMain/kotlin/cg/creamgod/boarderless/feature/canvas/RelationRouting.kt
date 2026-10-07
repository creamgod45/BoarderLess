package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private data class RoutingBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun orthogonalRelationRoute(
    source: CanvasObject,
    sourceTransform: CanvasTransform,
    target: CanvasObject,
    targetTransform: CanvasTransform,
    obstacles: Collection<CanvasTransform> = emptyList(),
    clearance: Float = 24f,
): List<Vec2> {
    require(clearance >= 0f) { "Routing clearance must not be negative" }
    val sourceCenter =
        sourceTransform.position +
            Vec2(
                sourceTransform.size.width / 2f,
                sourceTransform.size.height / 2f,
            )
    val targetCenter =
        targetTransform.position +
            Vec2(
                targetTransform.size.width / 2f,
                targetTransform.size.height / 2f,
            )
    val horizontal = abs(targetCenter.x - sourceCenter.x) >= abs(targetCenter.y - sourceCenter.y)
    val sourceToward =
        if (horizontal) {
            Vec2(targetCenter.x, sourceCenter.y)
        } else {
            Vec2(sourceCenter.x, targetCenter.y)
        }
    val targetToward =
        if (horizontal) {
            Vec2(sourceCenter.x, targetCenter.y)
        } else {
            Vec2(targetCenter.x, sourceCenter.y)
        }
    val start = source.connectionBoundary(sourceTransform, sourceToward)
    val end = target.connectionBoundary(targetTransform, targetToward)
    val obstacleBounds = obstacles.map { it.routingBounds(clearance) }
    val relevantBounds =
        obstacleBounds.filter { bounds ->
            bounds.right >= min(start.x, end.x) - clearance * 2f &&
                bounds.left <= max(start.x, end.x) + clearance * 2f &&
                bounds.bottom >= min(start.y, end.y) - clearance * 2f &&
                bounds.top <= max(start.y, end.y) + clearance * 2f
        }
    val candidates = mutableListOf<List<Vec2>>()
    if (horizontal) {
        val middleX = (start.x + end.x) / 2f
        candidates += listOf(start, Vec2(middleX, start.y), Vec2(middleX, end.y), end)
        candidates += listOf(start, Vec2(end.x, start.y), end)
        candidates += listOf(start, Vec2(start.x, end.y), end)
        val above =
            min(
                min(start.y, end.y),
                relevantBounds.minOfOrNull(RoutingBounds::top) ?: min(start.y, end.y),
            ) - clearance
        val below =
            max(
                max(start.y, end.y),
                relevantBounds.maxOfOrNull(RoutingBounds::bottom) ?: max(start.y, end.y),
            ) + clearance
        candidates += listOf(start, Vec2(start.x, above), Vec2(end.x, above), end)
        candidates += listOf(start, Vec2(start.x, below), Vec2(end.x, below), end)
    } else {
        val middleY = (start.y + end.y) / 2f
        candidates += listOf(start, Vec2(start.x, middleY), Vec2(end.x, middleY), end)
        candidates += listOf(start, Vec2(start.x, end.y), end)
        candidates += listOf(start, Vec2(end.x, start.y), end)
        val left =
            min(
                min(start.x, end.x),
                relevantBounds.minOfOrNull(RoutingBounds::left) ?: min(start.x, end.x),
            ) - clearance
        val right =
            max(
                max(start.x, end.x),
                relevantBounds.maxOfOrNull(RoutingBounds::right) ?: max(start.x, end.x),
            ) + clearance
        candidates += listOf(start, Vec2(left, start.y), Vec2(left, end.y), end)
        candidates += listOf(start, Vec2(right, start.y), Vec2(right, end.y), end)
    }
    val routes = candidates.map(::compactRoute)
    val selected =
        routes
            .filterNot { route -> obstacleBounds.any { route.intersects(it) } }
            .minByOrNull(::routeLength)
            ?: routes.first()
    return if (selected.size >= 2) selected else listOf(start, start + Vec2(0.001f, 0f))
}

private fun CanvasTransform.routingBounds(clearance: Float): RoutingBounds {
    val corners = rotatedTransformCorners(this)
    return RoutingBounds(
        left = corners.minOf(Vec2::x) - clearance,
        top = corners.minOf(Vec2::y) - clearance,
        right = corners.maxOf(Vec2::x) + clearance,
        bottom = corners.maxOf(Vec2::y) + clearance,
    )
}

private fun compactRoute(points: List<Vec2>): List<Vec2> =
    points
        .fold(mutableListOf<Vec2>()) { compacted, point ->
            if (compacted.lastOrNull() != point) compacted += point
            compacted
        }.let { pointsWithoutDuplicates ->
            if (pointsWithoutDuplicates.size < 3) return@let pointsWithoutDuplicates
            pointsWithoutDuplicates.filterIndexed { index, point ->
                if (index == 0 || index == pointsWithoutDuplicates.lastIndex) return@filterIndexed true
                val previous = pointsWithoutDuplicates[index - 1]
                val next = pointsWithoutDuplicates[index + 1]
                !(
                    (previous.x == point.x && point.x == next.x) ||
                        (previous.y == point.y && point.y == next.y)
                )
            }
        }

private fun List<Vec2>.intersects(bounds: RoutingBounds): Boolean =
    zipWithNext().any { (start, end) ->
        when {
            start.y == end.y -> {
                start.y in bounds.top..bounds.bottom &&
                    max(start.x, end.x) >= bounds.left && min(start.x, end.x) <= bounds.right
            }

            start.x == end.x -> {
                start.x in bounds.left..bounds.right &&
                    max(start.y, end.y) >= bounds.top && min(start.y, end.y) <= bounds.bottom
            }

            else -> {
                true
            }
        }
    }

internal fun routeIntersectsTransform(
    route: List<Vec2>,
    transform: CanvasTransform,
    clearance: Float = 0f,
): Boolean {
    require(clearance >= 0f) { "Routing clearance must not be negative" }
    return route.intersects(transform.routingBounds(clearance))
}

private fun routeLength(route: List<Vec2>): Float =
    route
        .zipWithNext()
        .sumOf { (start, end) ->
            (abs(end.x - start.x) + abs(end.y - start.y)).toDouble()
        }.toFloat()

internal fun distanceToPolyline(
    point: Vec2,
    route: List<Vec2>,
): Float =
    route
        .zipWithNext()
        .minOfOrNull { (start, end) -> distanceToSegment(point, start, end) }
        ?: Float.POSITIVE_INFINITY

internal fun polylineMidpoint(route: List<Vec2>): Vec2 {
    if (route.isEmpty()) return Vec2.Zero
    if (route.size == 1) return route.single()
    val segments =
        route.zipWithNext().map { (start, end) ->
            val dx = end.x - start.x
            val dy = end.y - start.y
            Triple(start, end, sqrt(dx * dx + dy * dy))
        }
    val half = segments.sumOf { it.third.toDouble() }.toFloat() / 2f
    var travelled = 0f
    segments.forEach { (start, end, length) ->
        if (travelled + length >= half && length > 0f) {
            val ratio = (half - travelled) / length
            return start + (end - start) * ratio
        }
        travelled += length
    }
    return route.last()
}

private fun distanceToSegment(
    point: Vec2,
    start: Vec2,
    end: Vec2,
): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.001f) {
        val pointDx = point.x - start.x
        val pointDy = point.y - start.y
        return sqrt(pointDx * pointDx + pointDy * pointDy)
    }
    val projection =
        (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
            .coerceIn(0f, 1f)
    val nearest = Vec2(start.x + projection * dx, start.y + projection * dy)
    val offset = point - nearest
    return sqrt(offset.x * offset.x + offset.y * offset.y)
}
