package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import kotlin.math.*

/** Screen-pixel geometry only. Automatic placements are presentation, never persisted mutations. */
internal data class RelationLabelInput(
    val id: RelationId,
    val route: List<Vec2>,
    val size: CanvasSize,
)

internal data class LabelBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun overlaps(other: LabelBounds) = left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun crosses(
        a: Vec2,
        b: Vec2,
    ): Boolean {
        var low = 0f
        var high = 1f
        val dx = b.x - a.x
        val dy = b.y - a.y
        for ((p, q) in listOf(-dx to (a.x - left), dx to (right - a.x), -dy to (a.y - top), dy to (bottom - a.y))) {
            if (p == 0f) {
                if (q < 0f) return false
            } else {
                val t = q / p
                if (p < 0f) low = max(low, t) else high = min(high, t)
                if (low > high) return false
            }
        }
        return true
    }
}

/** Stable ID order avoids map-insertion-dependent label stacking. Crowded layouts use best effort. */
internal fun layoutRelationLabels(
    labels: List<RelationLabelInput>,
    routes: Collection<List<Vec2>>,
    obstacles: List<LabelBounds>,
    gap: Float,
): Map<RelationId, Vec2> {
    require(gap.isFinite() && gap >= 0f)
    val occupied = mutableListOf<LabelBounds>()
    return buildMap {
        labels.sortedBy { it.id.value }.forEach { label ->
            if (label.route.size < 2) return@forEach
            val candidates =
                buildList {
                    // Try near the route midpoint first; then alternate segments and longer offsets.
                    val preferred = polylineMidpoint(label.route)
                    val segments =
                        label.route.zipWithNext().sortedBy { (a, b) ->
                            val mid = (a + b) / 2f
                            (mid.x - preferred.x).pow(2) + (mid.y - preferred.y).pow(2)
                        }
                    for ((a, b) in segments) {
                        val delta = b - a
                        val length = sqrt(delta.x * delta.x + delta.y * delta.y)
                        if (length <= 0f) continue
                        val normal = Vec2(-delta.y / length, delta.x / length)
                        val clearance = abs(normal.x) * label.size.width / 2f + abs(normal.y) * label.size.height / 2f + gap
                        for (fraction in listOf(0.5f, 0.25f, 0.75f)) {
                            for (factor in listOf(1f, -1f, 2f, -2f, 4f, -4f)) {
                                val center = a + delta * fraction + normal * (clearance * factor)
                                add(center - Vec2(label.size.width / 2f, label.size.height / 2f))
                            }
                        }
                    }
                }

            fun bounds(p: Vec2) =
                LabelBounds(
                    p.x - gap / 2f,
                    p.y - gap / 2f,
                    p.x + label.size.width + gap / 2f,
                    p.y + label.size.height + gap / 2f,
                )

            fun score(p: Vec2): Int {
                val box = bounds(p)
                return occupied.count(box::overlaps) * 100 + obstacles.count(box::overlaps) * 100 +
                    routes.count { route -> route.zipWithNext().any { (a, b) -> box.crosses(a, b) } } * 10
            }
            val location = candidates.firstOrNull { score(it) == 0 } ?: candidates.minByOrNull(::score) ?: return@forEach
            put(label.id, location)
            occupied += bounds(location)
        }
    }
}
