package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

internal enum class PenHandle { Anchor, Incoming, Outgoing, NewAnchor }
internal data class PenEditorGesture(val before: PenPathHistory, val start: Vec2, val index: Int,
    val handle: PenHandle, val working: PenPathDraft) {
    fun move(point: Vec2): PenEditorGesture {
        val delta = point - start
        val original = if (handle == PenHandle.NewAnchor) PenAnchor(start) else before.draft.anchors[index]
        val moved = when (handle) {
            PenHandle.Anchor -> original.translated(delta)
            PenHandle.Incoming -> original.copy(incoming = original.incoming!! + delta)
            PenHandle.Outgoing -> original.copy(outgoing = original.outgoing!! + delta)
            PenHandle.NewAnchor -> original.copy(incoming = start - delta, outgoing = start + delta)
        }
        return copy(working = working.replace(index, moved))
    }
    fun commit(): PenPathHistory = before.edit(working) // One history step per whole gesture.
}

internal fun beginPenEditorGesture(history: PenPathHistory, point: Vec2, radius: Float,
    addMode: Boolean, selected: Int?): PenEditorGesture? {
    require(radius.isFinite() && radius > 0f)
    val hits = buildList {
        history.draft.anchors.forEachIndexed { index, anchor ->
            add(Triple(index, PenHandle.Anchor, anchor.point))
            if (index == selected) {
                anchor.incoming?.let { add(Triple(index, PenHandle.Incoming, it)) }
                anchor.outgoing?.let { add(Triple(index, PenHandle.Outgoing, it)) }
            }
        }
    }.map { (index, handle, candidate) ->
        Triple(index, handle, (candidate.x.toDouble() - point.x).let { it * it } + (candidate.y.toDouble() - point.y).let { it * it })
    }.filter { it.third <= radius.toDouble() * radius }.minByOrNull { it.third }
    if (hits != null) return PenEditorGesture(history, point, hits.first, hits.second, history.draft)
    if (!addMode || history.draft.closed || history.draft.anchors.size >= 1024) return null
    if (point.x !in 0f..history.draft.viewBox.width || point.y !in 0f..history.draft.viewBox.height) return null
    return PenEditorGesture(history, point, history.draft.anchors.size, PenHandle.NewAnchor,
        history.draft.append(PenAnchor(point)))
}
