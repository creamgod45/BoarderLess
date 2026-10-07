package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.max
import kotlin.math.min

internal fun marqueeIntersectsTransform(
    transform: CanvasTransform,
    firstCorner: Vec2,
    secondCorner: Vec2,
): Boolean {
    val left = min(firstCorner.x, secondCorner.x)
    val top = min(firstCorner.y, secondCorner.y)
    val right = max(firstCorner.x, secondCorner.x)
    val bottom = max(firstCorner.y, secondCorner.y)
    val marqueeCorners =
        listOf(
            Vec2(left, top),
            Vec2(right, top),
            Vec2(right, bottom),
            Vec2(left, bottom),
        )
    val objectCorners = rotatedTransformCorners(transform)
    val objectHorizontal = rotateVector(Vec2(1f, 0f), transform.rotationDegrees)
    val objectVertical = rotateVector(Vec2(0f, 1f), transform.rotationDegrees)
    return listOf(Vec2(1f, 0f), Vec2(0f, 1f), objectHorizontal, objectVertical).all { axis ->
        val marqueeProjection = marqueeCorners.projectOnto(axis)
        val objectProjection = objectCorners.projectOnto(axis)
        marqueeProjection.first <= objectProjection.second && objectProjection.first <= marqueeProjection.second
    }
}

internal fun applyMarqueeSelection(
    current: Set<CanvasObjectId>,
    hits: Set<CanvasObjectId>,
    additive: Boolean,
): Set<CanvasObjectId> = if (additive) current + hits else hits

private fun List<Vec2>.projectOnto(axis: Vec2): Pair<Float, Float> {
    val values = map { point -> point.x * axis.x + point.y * axis.y }
    return values.minOrNull()!! to values.maxOrNull()!!
}
