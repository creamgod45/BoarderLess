package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class TransformInspectorValues(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
)

internal fun CanvasTransform.toInspectorValues(unitScale: Float): TransformInspectorValues {
    require(unitScale > 0f && unitScale.isFinite()) { "Inspector unit scale must be positive and finite" }
    return TransformInspectorValues(
        x = position.x / unitScale,
        y = position.y / unitScale,
        width = size.width / unitScale,
        height = size.height / unitScale,
        rotationDegrees = rotationDegrees,
    )
}

internal fun TransformInspectorValues.toCanvasTransform(
    unitScale: Float,
    minimumSize: CanvasSize,
): CanvasTransform? {
    if (unitScale <= 0f || !unitScale.isFinite()) return null
    if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() ||
        !rotationDegrees.isFinite()
    ) {
        return null
    }
    if (width <= 0f || height <= 0f) return null
    if (abs(x) > 1_000_000f || abs(y) > 1_000_000f || width > 100_000f || height > 100_000f) return null
    val scaledX = x * unitScale
    val scaledY = y * unitScale
    val scaledWidth = width * unitScale
    val scaledHeight = height * unitScale
    if (!scaledX.isFinite() || !scaledY.isFinite() || !scaledWidth.isFinite() || !scaledHeight.isFinite()) return null
    return CanvasTransform(
        position = Vec2(scaledX, scaledY),
        size = CanvasSize(scaledWidth, scaledHeight).atLeast(minimumSize),
        rotationDegrees = normalizeDegrees(rotationDegrees),
    )
}

internal fun formatInspectorNumber(value: Float): String {
    val integer = value.roundToInt()
    if (abs(value - integer) < 0.005f) return integer.toString()
    val rounded = (value * 100f).roundToInt() / 100f
    return rounded.toString().trimEnd('0').trimEnd('.')
}

internal fun accessibilityResizedTransform(
    transform: CanvasTransform,
    step: Float,
): CanvasTransform {
    if (!step.isFinite() || step <= 0f) return transform
    val width = transform.size.width + step
    val height = transform.size.height + step
    if (!width.isFinite() || !height.isFinite()) return transform
    return transform.copy(size = CanvasSize(width, height))
}

internal fun accessibilityRotatedTransform(
    transform: CanvasTransform,
    degrees: Float = 15f,
): CanvasTransform {
    if (!degrees.isFinite()) return transform
    return transform.copy(rotationDegrees = normalizeDegrees(transform.rotationDegrees + degrees))
}
