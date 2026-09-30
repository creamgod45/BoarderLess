package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.max
import kotlin.math.roundToInt

internal fun gridSnappedSelectionDelta(
    anchorPosition: Vec2,
    rawDelta: Vec2,
    gridSize: Float,
): Vec2 {
    require(gridSize > 0f) { "Grid size must be positive" }
    val freePosition = anchorPosition + rawDelta
    val snappedPosition = Vec2(
        x = (freePosition.x / gridSize).roundToInt() * gridSize,
        y = (freePosition.y / gridSize).roundToInt() * gridSize,
    )
    return snappedPosition - anchorPosition
}

internal fun scaleSelectionTransforms(
    transforms: Map<CanvasObjectId, CanvasTransform>,
    factor: Float,
    minimumSize: CanvasSize,
): Map<CanvasObjectId, CanvasTransform> {
    require(factor > 0f) { "Selection scale factor must be positive" }
    val selectionCenter = selectionBoundsCenter(transforms.values) ?: return emptyMap()
    return transforms.mapValues { (_, transform) ->
        val originalCenter = transform.center
        val nextCenter = selectionCenter + (originalCenter - selectionCenter) * factor
        val nextSize = CanvasSize(
            width = max(transform.size.width * factor, minimumSize.width),
            height = max(transform.size.height * factor, minimumSize.height),
        )
        transform.copy(
            position = nextCenter - Vec2(nextSize.width / 2f, nextSize.height / 2f),
            size = nextSize,
        )
    }
}

internal fun rotateSelectionTransforms(
    transforms: Map<CanvasObjectId, CanvasTransform>,
    degrees: Float,
): Map<CanvasObjectId, CanvasTransform> {
    val selectionCenter = selectionBoundsCenter(transforms.values) ?: return emptyMap()
    return transforms.mapValues { (_, transform) ->
        val nextCenter = selectionCenter + rotateVector(transform.center - selectionCenter, degrees)
        transform.copy(
            position = nextCenter - Vec2(transform.size.width / 2f, transform.size.height / 2f),
            rotationDegrees = normalizeDegrees(transform.rotationDegrees + degrees),
        )
    }
}

private val CanvasTransform.center: Vec2
    get() = position + Vec2(size.width / 2f, size.height / 2f)

private fun selectionBoundsCenter(transforms: Collection<CanvasTransform>): Vec2? {
    if (transforms.isEmpty()) return null
    val corners = transforms.flatMap(::rotatedTransformCorners)
    val left = corners.minOf { it.x }
    val top = corners.minOf { it.y }
    val right = corners.maxOf { it.x }
    val bottom = corners.maxOf { it.y }
    return Vec2((left + right) / 2f, (top + bottom) / 2f)
}
