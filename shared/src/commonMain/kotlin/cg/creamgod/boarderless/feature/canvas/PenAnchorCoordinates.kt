package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

internal enum class PenCoordinateTarget { Anchor, Incoming, Outgoing }

internal fun parsePenCoordinate(input: String): Float? =
    if (input.length > 16) {
        null
    } else {
        input.trim().toFloatOrNull()?.takeIf { it.isFinite() && it in -1_000_000f..1_000_000f }
    }

/** Anchor edits translate both handles; handle edits only change that handle. Bounds are atomic. */
internal fun editPenCoordinate(
    draft: PenPathDraft,
    index: Int,
    target: PenCoordinateTarget,
    point: Vec2,
): PenPathDraft {
    require(index in draft.anchors.indices)
    val anchor = draft.anchors[index]
    val edited =
        when (target) {
            PenCoordinateTarget.Anchor -> anchor.translated(point - anchor.point)
            PenCoordinateTarget.Incoming -> anchor.copy(incoming = point)
            PenCoordinateTarget.Outgoing -> anchor.copy(outgoing = point)
        }
    return draft.replace(index, edited)
}

internal fun removePenControl(
    draft: PenPathDraft,
    index: Int,
    target: PenCoordinateTarget,
): PenPathDraft {
    require(index in draft.anchors.indices && target != PenCoordinateTarget.Anchor)
    val anchor = draft.anchors[index]
    return draft.replace(
        index,
        when (target) {
            PenCoordinateTarget.Incoming -> anchor.copy(incoming = null)
            PenCoordinateTarget.Outgoing -> anchor.copy(outgoing = null)
            PenCoordinateTarget.Anchor -> error("Anchor removal requires the existing delete action")
        },
    )
}

internal fun curvePenAnchor(
    draft: PenPathDraft,
    index: Int,
): PenPathDraft {
    require(index in draft.anchors.indices)
    val anchor = draft.anchors[index]
    return draft.replace(index, anchor.copy(incoming = anchor.point - Vec2(30f, 0f), outgoing = anchor.point + Vec2(30f, 0f)))
}
