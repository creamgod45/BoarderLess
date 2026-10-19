package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import cg.creamgod.boarderless.domain.model.Vec2

/** Attached only to the background canvas, beneath object cards. A selected line
 * may own this gesture; objects, labels, area selection and secondary pan keep theirs. */
@Composable internal fun Modifier.relationLineDrag(enabled: Boolean,
    onBegin: (Vec2) -> String?, onMove: (String, Vec2) -> Unit,
    onEnd: (String) -> Unit, onCancel: (String) -> Unit): Modifier {
    val begin by rememberUpdatedState(onBegin)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
            if (currentEvent.buttons.isSecondaryPressed || currentEvent.keyboardModifiers.isShiftPressed ||
                currentEvent.changes.count { it.pressed } > 1) return@awaitEachGesture
            val token = begin(Vec2(down.position.x, down.position.y)) ?: return@awaitEachGesture
            down.consume()
            val motion = ObjectSelectionGesture(false, if (down.type == PointerType.Touch) viewConfiguration.touchSlop else 0f)
            var moved = false
            var interrupted = false
            try {
                val finished = drag(down.id) { change ->
                    if (currentEvent.changes.count { it.pressed } > 1) {
                        interrupted = true
                        cancel(token)
                    }
                    if (!interrupted) {
                        val delta = change.positionChange()
                        if (motion.move(Vec2(delta.x, delta.y))) {
                            moved = true
                            change.consume()
                            move(token, motion.displacement)
                        }
                    }
                }
                if (finished && moved && !interrupted) end(token) else cancel(token)
            } finally { cancel(token) }
        }
    }
}

/** Exact projection on the displayed world polyline, not guessed execution order. */
internal fun relationSegmentProjection(points: List<Vec2>, point: Vec2): Pair<Int, Vec2>? {
    var best: Pair<Int, Vec2>? = null
    var distance = Double.POSITIVE_INFINITY
    points.zipWithNext().forEachIndexed { index, (a, b) ->
        val dx = (b.x - a.x).toDouble(); val dy = (b.y - a.y).toDouble()
        val length = dx * dx + dy * dy
        if (length > 0 && length.isFinite()) {
            val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / length).coerceIn(0.0, 1.0)
            val projected = Vec2((a.x + dx * t).toFloat(), (a.y + dy * t).toFloat())
            val px = (point.x - projected.x).toDouble(); val py = (point.y - projected.y).toDouble()
            val d = px * px + py * py
            if (d < distance) { distance = d; best = index to projected }
        }
    }
    return best
}
