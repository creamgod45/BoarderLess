package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

internal enum class PenPaint { Fill, Stroke }

/** Preview is separate from history. Apply is one edit and rejects a stale draft. */
internal data class PenStyleColorEdit(
    val before: PenPathDraft,
    val target: PenPaint,
    val token: String,
) {
    fun preview(): PenPathDraft = before.copy(style = before.style.withPaint(target, token))

    fun commit(history: PenPathHistory): PenPathHistory {
        require(history.draft == before)
        return history.edit(preview())
    }
}

internal fun VectorPathStyle.withPaint(
    target: PenPaint,
    token: String?,
): VectorPathStyle =
    when (target) {
        PenPaint.Fill -> copy(fillColorToken = token)
        PenPaint.Stroke -> copy(strokeColorToken = token)
    }

internal fun parsePenStrokeWidth(input: String): Float? =
    if (input.length > 16) {
        null
    } else {
        input.trim().toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= 128f }
    }
