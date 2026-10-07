package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.Vec2

internal data class CanvasObjectBounds(
    val topLeft: Vec2,
    val bottomRight: Vec2,
) {
    val center: Vec2
        get() = (topLeft + bottomRight) / 2f
}

internal fun canvasObjectBounds(objects: Collection<CanvasObject>): CanvasObjectBounds? {
    if (objects.isEmpty()) return null
    val corners = objects.flatMap { rotatedTransformCorners(it.transform) }
    return CanvasObjectBounds(
        topLeft = Vec2(corners.minOf { it.x }, corners.minOf { it.y }),
        bottomRight = Vec2(corners.maxOf { it.x }, corners.maxOf { it.y }),
    )
}

internal fun groupFrameTransform(
    objects: Collection<CanvasObject>,
    padding: Float,
): CanvasTransform? {
    require(padding >= 0f) { "Group padding must not be negative" }
    val bounds = canvasObjectBounds(objects) ?: return null
    return CanvasTransform(
        position = bounds.topLeft - Vec2(padding, padding),
        size =
            CanvasSize(
                width = bounds.bottomRight.x - bounds.topLeft.x + padding * 2f,
                height = bounds.bottomRight.y - bounds.topLeft.y + padding * 2f,
            ),
    )
}

internal fun fittedGroupFrameTransform(
    group: GroupFrame,
    contents: Collection<CanvasObject>,
    padding: Float,
): CanvasTransform? {
    require(padding >= 0f) { "Group padding must not be negative" }
    if (contents.isEmpty()) return null
    val localCorners =
        contents
            .flatMap { rotatedTransformCorners(it.transform) }
            .map { point -> rotateVector(point, -group.transform.rotationDegrees) }
    val left = localCorners.minOf(Vec2::x) - padding
    val top = localCorners.minOf(Vec2::y) - padding
    val right = localCorners.maxOf(Vec2::x) + padding
    val bottom = localCorners.maxOf(Vec2::y) + padding
    val localCenter = Vec2((left + right) / 2f, (top + bottom) / 2f)
    val size = CanvasSize(right - left, bottom - top)
    val worldCenter = rotateVector(localCenter, group.transform.rotationDegrees)
    return group.transform.copy(
        position = worldCenter - Vec2(size.width / 2f, size.height / 2f),
        size = size,
    )
}

internal fun rotatedTransformCorners(transform: CanvasTransform): List<Vec2> {
    val center = transform.position + Vec2(transform.size.width / 2f, transform.size.height / 2f)
    val halfWidth = transform.size.width / 2f
    val halfHeight = transform.size.height / 2f
    return listOf(
        Vec2(-halfWidth, -halfHeight),
        Vec2(halfWidth, -halfHeight),
        Vec2(halfWidth, halfHeight),
        Vec2(-halfWidth, halfHeight),
    ).map { corner -> center + rotateVector(corner, transform.rotationDegrees) }
}
