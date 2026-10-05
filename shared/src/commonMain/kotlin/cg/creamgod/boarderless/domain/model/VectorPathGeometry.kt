package cg.creamgod.boarderless.domain.model

import kotlin.math.abs
import kotlin.math.sqrt

data class VectorContour(val points: List<Vec2>, val closed: Boolean)

/** Bounded approximation in viewBox units. Refuses overflow/depth exhaustion, never substitutes a box. */
fun VectorPath.flatten(tolerance: Float = 0.25f): List<VectorContour> {
    require(tolerance.isFinite() && tolerance in 0.001f..1000f)
    val contours = mutableListOf<VectorContour>()
    var points = mutableListOf<Vec2>()
    var total = 0
    fun append(point: Vec2) { require(++total <= 32768) { "Vector geometry limit exceeded" }; points.add(point) }
    fun finish(closed: Boolean) {
        if (points.isNotEmpty()) { contours.add(VectorContour(points.toList(), closed)); points = mutableListOf() }
    }
    fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    fun quadratic(a: Vec2, b: Vec2, c: Vec2, depth: Int) {
        if (distanceToSegment(b, a, c) <= tolerance) { append(c); return }
        require(depth < 16) { "Vector geometry limit exceeded" }
        val ab = midpoint(a, b); val bc = midpoint(b, c); val mid = midpoint(ab, bc)
        quadratic(a, ab, mid, depth + 1); quadratic(mid, bc, c, depth + 1)
    }
    fun cubic(a: Vec2, b: Vec2, c: Vec2, d: Vec2, depth: Int) {
        if (maxOf(distanceToSegment(b, a, d), distanceToSegment(c, a, d)) <= tolerance) { append(d); return }
        require(depth < 16) { "Vector geometry limit exceeded" }
        val ab = midpoint(a, b); val bc = midpoint(b, c); val cd = midpoint(c, d)
        val abc = midpoint(ab, bc); val bcd = midpoint(bc, cd); val mid = midpoint(abc, bcd)
        cubic(a, ab, abc, mid, depth + 1); cubic(mid, bcd, cd, d, depth + 1)
    }
    commands.forEach { command ->
        when (command) {
            is VectorPathCommand.Move -> { finish(false); append(command.point) }
            is VectorPathCommand.Line -> append(command.point)
            is VectorPathCommand.Quadratic -> quadratic(points.last(), command.control, command.point, 0)
            is VectorPathCommand.Cubic -> cubic(points.last(), command.control1, command.control2, command.point, 0)
            VectorPathCommand.Close -> { if (points.last() != points.first()) append(points.first()); finish(true) }
        }
    }
    finish(false)
    return contours.toList()
}

/** Open contours implicitly close for fill, but not for stroke, matching the path renderer. */
fun VectorPath.containsFill(point: Vec2, tolerance: Float = 0.25f): Boolean {
    if (style.fillColorToken == null) return false
    var winding = 0
    var crossings = 0
    flatten(tolerance).forEach { contour ->
        contour.edges(closeForFill = true).forEach { (a, b) ->
            if (distanceToSegment(point, a, b) <= tolerance) return true
            val side = cross(b.x.toDouble() - a.x, b.y.toDouble() - a.y, point.x.toDouble() - a.x, point.y.toDouble() - a.y)
            if (a.y <= point.y && b.y > point.y && side > 0) { winding++; crossings++ }
            if (a.y > point.y && b.y <= point.y && side < 0) { winding--; crossings++ }
        }
    }
    return if (style.fillRule == VectorFillRule.EvenOdd) crossings % 2 != 0 else winding != 0
}

/** Round caps/joins; renderer must use the same stroke policy. Tolerance adds a bounded pick margin. */
fun VectorPath.containsStroke(point: Vec2, tolerance: Float = 0.25f): Boolean {
    if (style.strokeColorToken == null) return false
    val radius = style.strokeWidth / 2.0 + tolerance
    return flatten(tolerance).any { contour -> contour.edges(false).any { (a, b) -> distanceToSegment(point, a, b) <= radius } }
}

/** First forward intersection with flattened fill boundary/path centreline, not a bounding rectangle.
 * Stroke width is not offset here. Origin may be outside/inside or inside a hole; nearest hit wins.
 */
fun VectorPath.firstBoundaryAlongRay(origin: Vec2, direction: Vec2, tolerance: Float = 0.25f): Vec2? {
    val length = sqrt(direction.x.toDouble() * direction.x + direction.y.toDouble() * direction.y)
    if (length == 0.0) return null
    val dx = direction.x / length; val dy = direction.y / length
    var closest = Double.POSITIVE_INFINITY
    flatten(tolerance).forEach { contour -> contour.edges(style.fillColorToken != null).forEach { (a, b) ->
        val ex = b.x.toDouble() - a.x; val ey = b.y.toDouble() - a.y
        val ax = a.x.toDouble() - origin.x; val ay = a.y.toDouble() - origin.y
        val denominator = cross(dx, dy, ex, ey)
        if (abs(denominator) > 1e-10) {
            val t = cross(ax, ay, ex, ey) / denominator
            val u = cross(ax, ay, dx, dy) / denominator
            if (t >= -1e-8 && u >= -1e-8 && u <= 1.0 + 1e-8) closest = minOf(closest, maxOf(0.0, t))
        } else if (abs(cross(ax, ay, dx, dy)) <= 1e-8) {
            val ta = ax * dx + ay * dy
            val tb = (b.x.toDouble() - origin.x) * dx + (b.y.toDouble() - origin.y) * dy
            if (maxOf(ta, tb) >= 0) closest = minOf(closest, maxOf(0.0, minOf(ta, tb)))
        }
    } }
    return if (closest.isFinite()) Vec2((origin.x + dx * closest).toFloat(), (origin.y + dy * closest).toFloat()) else null
}

private fun VectorContour.edges(closeForFill: Boolean): List<Pair<Vec2, Vec2>> = buildList {
    points.zipWithNext().forEach(::add)
    if ((closed || closeForFill) && points.last() != points.first()) add(points.last() to points.first())
}

private fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx

private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
    val dx = b.x.toDouble() - a.x; val dy = b.y.toDouble() - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else
        (((p.x.toDouble() - a.x) * dx + (p.y.toDouble() - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    val x = p.x - (a.x + t * dx); val y = p.y - (a.y + t * dy)
    return sqrt(x * x + y * y)
}
