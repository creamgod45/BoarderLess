package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.abs

/** Iterative simplification preserves endpoints and corners without a recursion-depth hazard. */
internal fun simplifyBrushStroke(points: List<Vec2>, tolerance: Float): List<Vec2> {
    require(points.size in 1..8192 && tolerance.isFinite() && tolerance >= 0f)
    if (points.size < 3) return points.toList()
    val keep = BooleanArray(points.size); keep[0] = true; keep[points.lastIndex] = true
    val stack = mutableListOf(0 to points.lastIndex)
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeAt(stack.lastIndex)
        val a = points[first]; val b = points[last]
        val dx = (b.x - a.x).toDouble(); val dy = (b.y - a.y).toDouble(); val length = dx * dx + dy * dy
        var farthest = -1; var distance = tolerance.toDouble() * tolerance
        for (i in first + 1 until last) {
            val p = points[i]; val px = (p.x - a.x).toDouble(); val py = (p.y - a.y).toDouble()
            val t = if (length == 0.0) 0.0 else ((px * dx + py * dy) / length).coerceIn(0.0, 1.0)
            val ex = px - t * dx; val ey = py - t * dy; val d = ex * ex + ey * ey
            if (d > distance) { distance = d; farthest = i }
        }
        if (farthest >= 0) { keep[farthest] = true; stack.add(first to farthest); stack.add(farthest to last) }
    }
    return points.filterIndexed { index, _ -> keep[index] }
}

internal data class BrushCanvasDraft(val capture: PenCanvasCreation, val operation: CreateObjectsOperation) {
    val objectId get() = operation.objects.single().id
    fun isCurrent(live: PenCanvasLive) = live.ready && capture.isCurrent(live.session, live.workspace)
}

internal fun prepareBrushStroke(owner: WorkspaceSession, baseline: Workspace, points: List<Vec2>, width: Float,
    color: String, tolerance: Float, objectId: String, operationId: String): BrushCanvasDraft {
    require(points.size in 1..8192 && points.all { abs(it.x) <= 1_000_000f && abs(it.y) <= 1_000_000f })
    val simplified = simplifyBrushStroke(points, tolerance)
    require(simplified.size <= 4095) // Never silently drop the end of a long drawing.
    val origin = Vec2(simplified.minOf { it.x }, simplified.minOf { it.y })
    val local = simplified.map { it - origin }.let { if (it.size == 1 || it.all { p -> p == it[0] }) listOf(it[0], it[0] + Vec2(.001f, 0f)) else it }
    val raw = VectorPath(CanvasSize(local.maxOf { it.x }.coerceAtLeast(.001f), local.maxOf { it.y }.coerceAtLeast(.001f)),
        listOf(VectorPathCommand.Move(local.first())) + local.drop(1).map(VectorPathCommand::Line),
        VectorPathStyle(fillColorToken = null, strokeColorToken = color, strokeWidth = width))
    val normalized = normalizePenPath(raw)
    val capture = PenCanvasCreation(owner, baseline, origin + normalized.origin)
    return BrushCanvasDraft(capture, capture.operation(normalized.path, objectId, operationId))
}
