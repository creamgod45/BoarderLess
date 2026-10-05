package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.PenPathDraft
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.min

/** Device-local editor camera. Never changes path coordinates or authoring history. */
internal data class PenEditorViewport(val center: Vec2, val zoom: Float = 1f) {
    init { require(zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM) }
    fun zoomBy(factor: Float): PenEditorViewport {
        require(factor.isFinite() && factor > 0f)
        return copy(zoom = (zoom.toDouble() * factor).coerceIn(MIN_ZOOM.toDouble(), MAX_ZOOM.toDouble()).toFloat())
    }
    companion object {
        const val MIN_ZOOM = .0000000001f
        const val MAX_ZOOM = 64f
        fun reset(viewBox: CanvasSize) = PenEditorViewport(Vec2(viewBox.width / 2, viewBox.height / 2))
        fun fit(draft: PenPathDraft): PenEditorViewport {
            // Include every handle, even unselected ones, and the drawing area. Bezier curves
            // lie in their control hull, so this framing does not cut off curve overshoot.
            val points = listOf(Vec2(0f, 0f), Vec2(draft.viewBox.width, draft.viewBox.height)) +
                draft.anchors.flatMap { listOfNotNull(it.point, it.incoming, it.outgoing) }
            val left = points.minOf { it.x }; val right = points.maxOf { it.x }
            val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
            return PenEditorViewport(Vec2((left + right) / 2, (top + bottom) / 2),
                min(draft.viewBox.width / (right - left), draft.viewBox.height / (bottom - top)).coerceIn(MIN_ZOOM, MAX_ZOOM))
        }
    }
}

internal data class PenEditorTransform(val center: Vec2, val screenCenter: Vec2, val scale: Float) {
    init { require(scale.isFinite() && scale > 0f) }
    val origin: Vec2 get() = Vec2(screenCenter.x - center.x * scale, screenCenter.y - center.y * scale)
    fun local(screen: Vec2) = Vec2(center.x + (screen.x - screenCenter.x) / scale, center.y + (screen.y - screenCenter.y) / scale)
    fun screen(local: Vec2) = Vec2(screenCenter.x + (local.x - center.x) * scale, screenCenter.y + (local.y - center.y) * scale)
    fun panFrom(start: PenEditorViewport, screenDelta: Vec2) = start.copy(center = Vec2(
        (start.center.x - screenDelta.x / scale).coerceIn(-1_000_000f, 1_000_000f),
        (start.center.y - screenDelta.y / scale).coerceIn(-1_000_000f, 1_000_000f)))
}

internal fun penEditorTransform(viewBox: CanvasSize, viewport: PenEditorViewport,
    width: Float, height: Float, padding: Float): PenEditorTransform? {
    if (!width.isFinite() || !height.isFinite() || !padding.isFinite() || padding < 0f || width <= 2 * padding || height <= 2 * padding) return null
    val scale = min((width - 2 * padding) / viewBox.width, (height - 2 * padding) / viewBox.height) * viewport.zoom
    if (!scale.isFinite() || scale <= 0f) return null
    return PenEditorTransform(viewport.center, Vec2(width / 2, height / 2), scale)
}
