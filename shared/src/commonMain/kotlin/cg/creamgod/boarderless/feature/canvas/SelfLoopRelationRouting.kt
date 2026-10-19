package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasTransform
import kotlin.math.abs

/** Geometry-only preparation for relation v2. No persistence or UI capability is enabled here. */
internal enum class RelationLoopSide(
    val outward: Vec2,
) {
    Top(Vec2(0f, -1f)),
    Right(Vec2(1f, 0f)),
    Bottom(Vec2(0f, 1f)),
    Left(Vec2(-1f, 0f)),
}

/** A nonzero source-to-target loop with separate shape-boundary ports on a chosen local side.
 * All points rotate about the node centre together. Arrow direction must not reverse this route.
 * The outer run clears the unrotated rectangular footprint; this does not avoid other nodes.
 */
internal fun selfLoopRelationRoute(
    node: CanvasObject,
    transform: CanvasTransform,
    side: RelationLoopSide = RelationLoopSide.Right,
    extent: Float = 80f,
): List<Vec2> {
    require(extent.isFinite() && extent in 16f..4096f) { "Invalid loop extent" }
    require(transform.size.width > 0f && transform.size.height > 0f) { "Loop ports need a nonzero node" }
    val localTransform = transform.copy(position = Vec2.Zero, rotationDegrees = 0f)
    val localCenter = Vec2(transform.size.width / 2f, transform.size.height / 2f)
    val worldCenter = transform.position + localCenter
    val outward = side.outward
    val tangent = Vec2(-outward.y, outward.x)
    val normalHalfSize =
        when (side) {
            RelationLoopSide.Left, RelationLoopSide.Right -> transform.size.width / 2f
            else -> transform.size.height / 2f
        }
    val tangentSize =
        when (side) {
            RelationLoopSide.Left, RelationLoopSide.Right -> transform.size.height
            else -> transform.size.width
        }

    fun port(sign: Float): Vec2 {
        // Enter from outside on a side-normal ray. A centre-origin ray can choose an inner
        // notch of a concave shape (Plus/Star), making the outward stub cross the shape again.
        val outside = localCenter + outward * (normalHalfSize + 16f) + tangent * (sign * tangentSize / 4f)
        node.connectionShape.polygonVertices()?.let { polygon ->
            val distance =
                polygonRayBoundary(
                    polygon,
                    outward * -1f,
                    transform.size.width,
                    transform.size.height,
                    outside - localCenter,
                )
            require(distance != null) { "Loop side has no shape boundary" }
            return outside - outward * distance
        }
        // Curved outlines use the same contains predicate as the renderer/hit testing.
        val span = normalHalfSize * 2f + 32f
        var outsideDistance = 0f
        for (step in 1..64) {
            var insideDistance = span * step / 64f
            if (localTransform.containsWorldPoint(outside - outward * insideDistance, node.connectionShape)) {
                repeat(24) {
                    val midpoint = (outsideDistance + insideDistance) / 2f
                    if (localTransform.containsWorldPoint(outside - outward * midpoint, node.connectionShape)) {
                        insideDistance = midpoint
                    } else {
                        outsideDistance = midpoint
                    }
                }
                return outside - outward * outsideDistance
            }
            outsideDistance = insideDistance
        }
        error("Loop side has no shape boundary")
    }
    val vectorPorts =
        (node as? TextNode)?.vectorPath?.let { path ->
            vectorLoopPorts(path, transform.size, localCenter, outward, tangent, normalHalfSize, tangentSize)
        }
    val start = vectorPorts?.first ?: port(-1f)
    val end = vectorPorts?.second ?: port(1f)
    require(start != end) { "Loop ports must be distinct" }

    fun tangentDistance(port: Vec2): Float {
        val relative = port - localCenter
        return relative.x * tangent.x + relative.y * tangent.y
    }
    val startTangent = tangentDistance(start)
    val endTangent = tangentDistance(end)
    // A horizontal/vertical open stroke can have distinct ports but no span on this side.
    // Separate the outer corners so the loop and both arrow tangents remain visible.
    val flat = abs(endTangent - startTangent) < 0.001f

    fun outer(distance: Float) = localCenter + outward * (normalHalfSize + extent) + tangent * distance
    return listOf(
        start,
        outer(startTangent - if (flat) 8f else 0f),
        outer(endTangent + if (flat) 8f else 0f),
        end,
    ).map {
        worldCenter + rotateVector(it - localCenter, transform.rotationDegrees)
    }
}

/** Cast from outside inward, keeping ports on the actual contour/centreline. Open or narrow
 * paths may miss either ray; bounded flattened points supply distinct fallback ports. */
private fun vectorLoopPorts(
    path: VectorPath,
    size: CanvasSize,
    center: Vec2,
    outward: Vec2,
    tangent: Vec2,
    normalHalfSize: Float,
    tangentSize: Float,
): Pair<Vec2, Vec2> {
    fun local(point: Vec2) =
        Vec2(
            point.x * path.viewBox.width / size.width,
            point.y * path.viewBox.height / size.height,
        )

    fun canvas(point: Vec2) =
        Vec2(
            point.x * size.width / path.viewBox.width,
            point.y * size.height / path.viewBox.height,
        )
    val points =
        path
            .flatten()
            .flatMap { it.points }
            .map(::canvas)
            .distinct()
    require(points.size >= 2) { "Loop ports need a nonzero contour" }

    fun squared(
        a: Vec2,
        b: Vec2,
    ): Double {
        val d = a - b
        return d.x.toDouble() * d.x + d.y.toDouble() * d.y
    }

    fun choose(sign: Float): Vec2 {
        val outside = center + outward * (normalHalfSize + 16f) + tangent * (sign * tangentSize / 4f)
        val direction = local(outward * -1f)
        return path.firstBoundaryAlongRay(local(outside), direction)?.let(::canvas)
            ?: points.minBy { squared(it, outside) }
    }
    val start = choose(-1f)
    val requestedEnd = choose(1f)
    val end =
        if (squared(start, requestedEnd) > 0.000001) {
            requestedEnd
        } else {
            points.maxBy { squared(it, start) }
        }
    require(squared(start, end) > 0.000001) { "Loop ports must be distinct" }
    return start to end
}
