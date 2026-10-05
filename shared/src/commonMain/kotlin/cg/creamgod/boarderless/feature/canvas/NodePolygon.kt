package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.abs

/** One owned normalized outline for rendering, selection and relation endpoints. */
internal fun NodeShape.polygonVertices(): List<Vec2>? {
    fun points(vararg xy: Float) = xy.toList().chunked(2).map { Vec2(it[0], it[1]) }
    val arrow = points(0f, .25f, .65f, .25f, .65f, 0f, 1f, .5f, .65f, 1f, .65f, .75f, 0f, .75f)
    return when (this) {
        NodeShape.Triangle -> points(.5f, 0f, 1f, 1f, 0f, 1f)
        NodeShape.Pentagon -> points(.5f, 0f, 1f, .38f, .81f, 1f, .19f, 1f, 0f, .38f)
        NodeShape.Octagon -> points(.3f, 0f, .7f, 0f, 1f, .3f, 1f, .7f, .7f, 1f, .3f, 1f, 0f, .7f, 0f, .3f)
        NodeShape.Trapezoid -> points(.2f, 0f, .8f, 0f, 1f, 1f, 0f, 1f)
        NodeShape.Plus -> points(.35f, 0f, .65f, 0f, .65f, .35f, 1f, .35f, 1f, .65f,
            .65f, .65f, .65f, 1f, .35f, 1f, .35f, .65f, 0f, .65f, 0f, .35f, .35f, .35f)
        NodeShape.ArrowRight -> arrow
        NodeShape.ArrowLeft -> arrow.map { Vec2(1f - it.x, it.y) }
        NodeShape.ArrowUp -> arrow.map { Vec2(it.y, 1f - it.x) }
        NodeShape.ArrowDown -> arrow.map { Vec2(it.y, it.x) }
        NodeShape.TriangleDown -> points(0f, 0f, 1f, 0f, .5f, 1f)
        NodeShape.RightTriangle -> points(0f, 0f, 1f, 1f, 0f, 1f)
        NodeShape.Chevron -> points(0f, 0f, .65f, 0f, 1f, .5f, .65f, 1f, 0f, 1f, .35f, .5f)
        NodeShape.DoubleArrow -> points(0f, .5f, .3f, 0f, .3f, .3f, .7f, .3f,
            .7f, 0f, 1f, .5f, .7f, 1f, .7f, .7f, .3f, .7f, .3f, 1f)
        NodeShape.Star -> points(.5f, 0f, .62f, .35f, 1f, .35f, .7f, .59f,
            .81f, 1f, .5f, .75f, .19f, 1f, .3f, .59f, 0f, .35f, .38f, .35f)
        NodeShape.ManualInput -> points(0f, .25f, 1f, 0f, 1f, 1f, 0f, 1f)
        else -> null
    }
}

internal fun polygonContains(vertices: List<Vec2>, point: Vec2): Boolean {
    var inside = false
    for (index in vertices.indices) {
        val a = vertices[index]
        val b = vertices[(index + 1) % vertices.size]
        val cross = (point.x - a.x) * (b.y - a.y) - (point.y - a.y) * (b.x - a.x)
        if (abs(cross) < 0.000001f && point.x >= minOf(a.x, b.x) - 0.000001f &&
            point.x <= maxOf(a.x, b.x) + 0.000001f && point.y >= minOf(a.y, b.y) - 0.000001f &&
            point.y <= maxOf(a.y, b.y) + 0.000001f) return true
        if ((a.y > point.y) != (b.y > point.y) &&
            point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
    }
    return inside
}

/** Nearest ray exit, not binary containment (a concave arrow can exit and reenter). */
internal fun polygonRayBoundary(vertices: List<Vec2>, direction: Vec2, width: Float, height: Float, origin: Vec2 = Vec2.Zero): Float? {
    fun local(point: Vec2) = Vec2((point.x - .5f) * width, (point.y - .5f) * height) - origin
    fun cross(a: Vec2, b: Vec2) = a.x * b.y - a.y * b.x
    return vertices.indices.mapNotNull { index ->
        val a = local(vertices[index])
        val edge = local(vertices[(index + 1) % vertices.size]) - a
        val denominator = cross(direction, edge)
        if (abs(denominator) < 0.000001f) null else {
            val distance = cross(a, edge) / denominator
            val fraction = cross(a, direction) / denominator
            distance.takeIf { it >= 0f && fraction >= -0.000001f && fraction <= 1.000001f }
        }
    }.minOrNull()
}
