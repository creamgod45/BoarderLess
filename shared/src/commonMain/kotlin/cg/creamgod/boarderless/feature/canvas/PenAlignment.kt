package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import kotlin.math.abs

internal data class PenAlignment(val point: Vec2, val x: Float?, val y: Float?)

/** Tolerance is in path units; the editor divides the screen tolerance by the current zoom. */
internal fun alignPenPoint(draft: PenPathDraft, point: Vec2, exclude: Int?, tolerance: Float, snap: Boolean): PenAlignment {
    require(tolerance.isFinite() && tolerance >= 0f)
    val anchors = draft.anchors.filterIndexed { index, _ -> index != exclude }
    fun nearest(values: List<Float>, value: Float): Float? =
        values.filter { abs(it - value) <= tolerance }.minByOrNull { abs(it - value) }
    val x = nearest(anchors.map { it.point.x } + draft.viewBox.width / 2f, point.x)
    val y = nearest(anchors.map { it.point.y } + draft.viewBox.height / 2f, point.y)
    return PenAlignment(if (snap) Vec2(x ?: point.x, y ?: point.y) else point, x, y)
}

internal fun PenEditorGesture.moveAligned(point: Vec2, tolerance: Float, snap: Boolean): Pair<PenEditorGesture, PenAlignment> {
    val moved = move(point)
    val anchor = moved.working.anchors[index]
    val target = when (handle) {
        PenHandle.Anchor -> anchor.point
        PenHandle.Incoming -> requireNotNull(anchor.incoming)
        PenHandle.Outgoing, PenHandle.NewAnchor -> anchor.outgoing ?: anchor.point
    }
    val alignment = alignPenPoint(before.draft, target, index, tolerance, snap)
    return (if (snap) move(point + (alignment.point - target)) else moved) to alignment
}
