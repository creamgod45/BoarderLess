package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Axis-aligned rectangle in normalized node coordinates. */
internal data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** Dot placed relative to an anchor in normalized coordinates plus a dp offset (so it keeps its size when scaled). */
internal data class ShapeDot(val anchorX: Float, val anchorY: Float, val offsetXDp: Float, val radiusDp: Float, val offsetYDp: Float = 0f)

/**
 * Single source of truth for the fixed shapes added by docs/SHAPE_LIBRARY_SPEC.md. Everything is
 * expressed in normalized `[0,1]` node coordinates and derived once, so drawing, hit testing,
 * relation boundaries, previews and the text area cannot disagree.
 *
 * [body] is the filled outline (null for stroke-only shapes); [holes] are subtracted from it.
 * [shaded] polygons use a slightly darker fill, [lines] are open polylines drawn with the border
 * colour (for stroke-only shapes they are the shape itself), and [dots] are small filled circles.
 */
internal class NodeShapeArt(
    val body: List<Vec2>?,
    val holes: List<List<Vec2>> = emptyList(),
    val shaded: List<List<Vec2>> = emptyList(),
    val lines: List<List<Vec2>> = emptyList(),
    val dots: List<ShapeDot> = emptyList(),
    /** Null means the shape offers no text area by default (existing text is still rendered). */
    val textRect: NormRect? = null,
    val origin: Vec2 = Vec2(.5f, .5f),
    /** Stroke-only shapes connect at [origin] and are hit by distance to [lines], not by area. */
    val strokeOnly: Boolean = false,
)

private const val Segments = 48

private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, startDegrees: Float, endDegrees: Float, segments: Int = Segments): List<Vec2> {
    val steps = maxOf(2, (abs(endDegrees - startDegrees) / 360f * segments).toInt() + 1)
    return (0..steps).map { index ->
        val angle = (startDegrees + (endDegrees - startDegrees) * index / steps) * PI.toFloat() / 180f
        Vec2(cx + rx * cos(angle), cy + ry * sin(angle))
    }
}

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = arc(cx, cy, rx, ry, 0f, 360f).dropLast(1)

private fun points(vararg xy: Float) = xy.toList().chunked(2).map { Vec2(it[0], it[1]) }

private fun regularPolygon(sides: Int): List<Vec2> {
    val raw = (0 until sides).map {
        val angle = (-90.0 + 360.0 * it / sides) * PI / 180.0
        Vec2(cos(angle).toFloat(), sin(angle).toFloat())
    }
    val minX = raw.minOf { it.x }; val maxX = raw.maxOf { it.x }
    val minY = raw.minOf { it.y }; val maxY = raw.maxOf { it.y }
    return raw.map { Vec2((it.x - minX) / (maxX - minX), (it.y - minY) / (maxY - minY)) }
}

private fun roundedRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float): List<Vec2> =
    arc(right - rx, top + ry, rx, ry, -90f, 0f, 24) + arc(right - rx, bottom - ry, rx, ry, 0f, 90f, 24) +
        arc(left + rx, bottom - ry, rx, ry, 90f, 180f, 24) + arc(left + rx, top + ry, rx, ry, 180f, 270f, 24)

/** Outline of a region that is star-shaped about [center], found by ray marching [inside]. */
private fun starShaped(center: Vec2, rays: Int = 144, inside: (Float, Float) -> Boolean): List<Vec2> =
    (0 until rays).map { index ->
        val angle = 2.0 * PI * index / rays
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        var low = 0f
        var high = 1.6f
        repeat(28) {
            val mid = (low + high) / 2f
            if (inside(center.x + dx * mid, center.y + dy * mid)) low = mid else high = mid
        }
        Vec2(center.x + dx * low, center.y + dy * low)
    }

private fun circle(x: Float, y: Float, cx: Float, cy: Float, r: Float) = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r

private fun brace(): List<Vec2> {
    // M(1,0) C(.6,0)(.5,.1)(.5,.25) L(.5,.4) C(.5,.47)(.4,.5)(0,.5) C(.4,.5)(.5,.53)(.5,.6) L(.5,.75) C(.5,.9)(.6,1)(1,1)
    fun cubic(a: Vec2, b: Vec2, c: Vec2, d: Vec2, steps: Int = 14) = (1..steps).map {
        val t = it / steps.toFloat()
        val u = 1f - t
        Vec2(
            u * u * u * a.x + 3 * u * u * t * b.x + 3 * u * t * t * c.x + t * t * t * d.x,
            u * u * u * a.y + 3 * u * u * t * b.y + 3 * u * t * t * c.y + t * t * t * d.y,
        )
    }
    val start = Vec2(1f, 0f)
    return listOf(start) +
        cubic(start, Vec2(.6f, 0f), Vec2(.5f, .1f), Vec2(.5f, .25f)) + Vec2(.5f, .4f) +
        cubic(Vec2(.5f, .4f), Vec2(.5f, .47f), Vec2(.4f, .5f), Vec2(0f, .5f)) +
        cubic(Vec2(0f, .5f), Vec2(.4f, .5f), Vec2(.5f, .53f), Vec2(.5f, .6f)) + Vec2(.5f, .75f) +
        cubic(Vec2(.5f, .75f), Vec2(.5f, .9f), Vec2(.6f, 1f), Vec2(1f, 1f))
}

private fun crescent(): List<Vec2> {
    // Outer disc (0.5,0.5) r=0.5 minus inner disc (0.75,0.5) r=0.425; the discs meet at two points.
    val outerRadius = .5f
    val innerCenterX = .75f
    val innerRadius = .425f
    val distance = innerCenterX - .5f
    val along = (distance * distance + outerRadius * outerRadius - innerRadius * innerRadius) / (2f * distance)
    val half = sqrt(outerRadius * outerRadius - along * along)
    val bottom = Vec2(.5f + along, .5f + half)
    val top = Vec2(.5f + along, .5f - half)
    fun degrees(cx: Float, point: Vec2) = atan2Degrees(point.y - .5f, point.x - cx)
    // Outer arc: bottom intersection, around the left, to the top intersection (screen y points down).
    val outerStart = degrees(.5f, bottom)
    val outerEnd = degrees(.5f, top).let { if (it <= outerStart) it + 360f else it }
    val outer = arc(.5f, .5f, outerRadius, outerRadius, outerStart, outerEnd)
    // Inner arc: back from the top intersection to the bottom one along the inner circle's left side.
    val innerStart = degrees(innerCenterX, top)
    val innerEnd = degrees(innerCenterX, bottom).let { if (it >= innerStart) it - 360f else it }
    val inner = arc(innerCenterX, .5f, innerRadius, innerRadius, innerStart, innerEnd)
    return outer + inner.drop(1).dropLast(1)
}

private fun atan2Degrees(y: Float, x: Float) = (kotlin.math.atan2(y, x) * 180f / PI.toFloat()).let { if (it < 0f) it + 360f else it }

private fun unit(points: List<Vec2>) = points.map { Vec2(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }

private fun NodeShapeArt.clamped() = NodeShapeArt(
    body?.let(::unit), holes.map(::unit), shaded.map(::unit), lines.map(::unit), dots, textRect, origin, strokeOnly,
)

private val arts: Map<NodeShape, NodeShapeArt> by lazy {
    val calloutTail = points(.30f, .8f, .10f, 1f, .12f, .8f)
    fun callout(rx: Float, ry: Float): List<Vec2> {
        val round = roundedRect(0f, 0f, 1f, .8f, rx, ry)
        // The bottom edge runs right to left; splice the tail in after the bottom-right corner arc.
        val splice = round.indexOfFirst { it.y >= .8f - 1e-4f && it.x <= .30f } .let { if (it < 0) round.size else it }
        return round.take(splice) + calloutTail + round.drop(splice)
    }
    mapOf(
        NodeShape.Semicircle to NodeShapeArt(
            body = listOf(Vec2(0f, 1f)) + arc(.5f, 1f, .5f, 1f, 180f, 360f) + Vec2(1f, 1f),
            textRect = NormRect(.15f, .35f, .85f, .95f), origin = Vec2(.5f, .6f),
        ),
        NodeShape.Ring to NodeShapeArt(body = ellipse(.5f, .5f, .5f, .5f), holes = listOf(ellipse(.5f, .5f, .275f, .275f))),
        NodeShape.Heptagon to NodeShapeArt(body = regularPolygon(7), textRect = NormRect(.2f, .25f, .8f, .8f)),
        NodeShape.Heart to NodeShapeArt(
            body = starShaped(Vec2(.5f, .5f)) { x, y ->
                circle(x, y, .27f, .30f, .27f) || circle(x, y, .73f, .30f, .27f) ||
                    (y in .36f..1f && x >= .01f + (y - .36f) / .64f * .49f && x <= .99f - (y - .36f) / .64f * .49f)
            },
            textRect = NormRect(.28f, .20f, .72f, .60f),
        ),
        NodeShape.Lightning to NodeShapeArt(
            body = points(.55f, 0f, .15f, .55f, .45f, .55f, .3f, 1f, .85f, .4f, .55f, .4f, .76f, 0f),
            origin = Vec2(.5f, .5f),
        ),
        NodeShape.Crescent to NodeShapeArt(
            body = crescent(), textRect = NormRect(.04f, .38f, .28f, .62f), origin = Vec2(.16f, .5f),
        ),
        NodeShape.PredefinedProcess to NodeShapeArt(
            body = points(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            lines = listOf(points(.1f, 0f, .1f, 1f), points(.9f, 0f, .9f, 1f)),
            textRect = NormRect(.15f, .10f, .85f, .90f),
        ),
        NodeShape.InternalStorage to NodeShapeArt(
            body = points(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            lines = listOf(points(.12f, 0f, .12f, 1f), points(0f, .15f, 1f, .15f)),
            textRect = NormRect(.17f, .20f, .95f, .95f),
        ),
        NodeShape.Delay to NodeShapeArt(
            body = points(0f, 0f, .5f, 0f) + arc(.5f, .5f, .5f, .5f, -90f, 90f) + points(.5f, 1f, 0f, 1f),
            textRect = NormRect(.08f, .12f, .78f, .88f),
        ),
        NodeShape.OrJunction to NodeShapeArt(
            body = ellipse(.5f, .5f, .5f, .5f), lines = listOf(points(.5f, 0f, .5f, 1f), points(0f, .5f, 1f, .5f)),
        ),
        NodeShape.SummingJunction to NodeShapeArt(
            body = ellipse(.5f, .5f, .5f, .5f),
            lines = listOf(points(.1464f, .1464f, .8536f, .8536f), points(.8536f, .1464f, .1464f, .8536f)),
        ),
        NodeShape.OffPageConnector to NodeShapeArt(
            body = points(0f, 0f, 1f, 0f, 1f, .7f, .5f, 1f, 0f, .7f), textRect = NormRect(.10f, .10f, .90f, .65f),
        ),
        NodeShape.Display to NodeShapeArt(
            body = points(0f, .5f, .2f, 0f, .8f, 0f) + arc(.8f, .5f, .2f, .5f, -90f, 90f, 24) + points(.8f, 1f, .2f, 1f),
            textRect = NormRect(.25f, .10f, .85f, .90f),
        ),
        NodeShape.MultiDocument to NodeShapeArt(
            // Silhouette of the three stacked sheets; the sheet edges are drawn as lines (flat bottoms).
            body = points(0f, .15f, .075f, .15f, .075f, .075f, .15f, .075f, .15f, 0f, 1f, 0f, 1f, .8f, .925f, .8f, .925f, .925f, .85f, .925f, .85f, 1f, 0f, 1f),
            lines = listOf(
                points(0f, .15f, .85f, .15f, .85f, 1f), // front sheet top/right
                points(.075f, .15f, .075f, .075f, .925f, .075f, .925f, .925f, .85f, .925f), // middle
                points(.15f, .075f, .15f, 0f, 1f, 0f, 1f, .8f, .925f, .8f), // back
            ),
            textRect = NormRect(.05f, .25f, .80f, .75f),
        ),
        NodeShape.CalloutRound to NodeShapeArt(
            body = callout(.1f, .125f), textRect = NormRect(.05f, .08f, .95f, .72f), origin = Vec2(.5f, .4f),
        ),
        NodeShape.CalloutRect to NodeShapeArt(
            body = callout(0.001f, 0.001f), textRect = NormRect(.05f, .08f, .95f, .72f), origin = Vec2(.5f, .4f),
        ),
        NodeShape.Note to NodeShapeArt(
            body = points(0f, 0f, .82f, 0f, 1f, .18f, 1f, 1f, 0f, 1f),
            shaded = listOf(points(.82f, 0f, .82f, .18f, 1f, .18f)),
            lines = listOf(points(.82f, 0f, .82f, .18f, 1f, .18f)),
            textRect = NormRect(.06f, .20f, .94f, .92f),
        ),
        NodeShape.BraceLeft to NodeShapeArt(body = null, lines = listOf(brace()), origin = Vec2(0f, .5f), strokeOnly = true),
        NodeShape.BraceRight to NodeShapeArt(
            body = null, lines = listOf(brace().map { Vec2(1f - it.x, it.y) }), origin = Vec2(1f, .5f), strokeOnly = true,
        ),
        NodeShape.Folder to NodeShapeArt(
            body = points(0f, 0f, .4f, 0f, .4f, .12f, 1f, .12f, 1f, 1f, 0f, 1f), textRect = NormRect(.05f, .20f, .95f, .95f),
        ),
        NodeShape.Window to NodeShapeArt(
            body = roundedRect(0f, 0f, 1f, 1f, .05f, .08f),
            shaded = listOf(roundedRect(0f, 0f, 1f, .14f, .05f, .08f).filter { it.y <= .14f } ),
            lines = listOf(points(0f, .14f, 1f, .14f)),
            dots = listOf(ShapeDot(0f, .07f, 10f, 3f), ShapeDot(0f, .07f, 20f, 3f), ShapeDot(0f, .07f, 30f, 3f)),
            textRect = NormRect(.05f, .20f, .95f, .95f),
        ),
        NodeShape.Cloud to NodeShapeArt(
            body = starShaped(Vec2(.5f, .55f)) { x, y ->
                circle(x, y, .22f, .62f, .22f) || circle(x, y, .40f, .38f, .26f) || circle(x, y, .65f, .36f, .24f) ||
                    circle(x, y, .80f, .60f, .20f) || (x in .22f..0.80f && y in .55f..0.82f)
            },
            textRect = NormRect(.20f, .40f, .80f, .80f),
        ),
        NodeShape.Server to NodeShapeArt(
            body = roundedRect(0f, 0f, 1f, 1f, .04f, .06f),
            lines = listOf(points(0f, 1f / 3f, 1f, 1f / 3f), points(0f, 2f / 3f, 1f, 2f / 3f)),
            dots = listOf(ShapeDot(1f, 1f / 6f, -12f, 3f), ShapeDot(1f, .5f, -12f, 3f), ShapeDot(1f, 5f / 6f, -12f, 3f)),
            textRect = NormRect(.05f, .05f, .80f, .95f),
        ),
        NodeShape.Person to NodeShapeArt(
            body = starShaped(Vec2(.5f, .39f)) { x, y ->
                circle(x, y, .5f, .22f, .18f) || (y >= .38f && y <= 1f && ((x - .5f) / .4f).let { dx -> dx * dx } + ((y - 1f) / .62f).let { dy -> dy * dy } <= 1f)
            },
        ),
    ).mapValues { it.value.clamped() }
}

/** Geometry for shapes added by the expansion spec; null for the original shapes. */
internal fun NodeShape.art(): NodeShapeArt? = arts[this]

internal fun polylineDistance(line: List<Vec2>, x: Float, y: Float): Float {
    var best = Float.MAX_VALUE
    for (index in 0 until line.size - 1) {
        val a = line[index]; val b = line[index + 1]
        val dx = b.x - a.x; val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        best = minOf(best, hypot(x - (a.x + t * dx), y - (a.y + t * dy)))
    }
    return best
}

internal fun signedAreaIsClockwise(polygon: List<Vec2>): Boolean {
    var area = 0.0
    for (index in polygon.indices) {
        val a = polygon[index]; val b = polygon[(index + 1) % polygon.size]
        area += a.x.toDouble() * b.y - b.x.toDouble() * a.y
    }
    return area > 0.0 // y points down on screen, so a positive sum is clockwise.
}

internal fun sqrtOf(value: Float) = sqrt(value)
