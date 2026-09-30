package cg.creamgod.boarderless.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * [horizontalScroll] that a plain mouse wheel can also drive. Touchpads and Shift+wheel already
 * scroll horizontally; a mouse wheel only reports vertical deltas, which a horizontal scroller
 * ignores, so a toolbar wider than the window was unreachable with a mouse. Vertical wheel deltas
 * are mapped onto the horizontal axis and consumed only while the row can still move that way.
 *
 * Use it for top-level bars only: inside a vertically scrolling panel it would take the wheel away
 * from the panel, so wrap those rows instead.
 */
fun Modifier.horizontalScrollWithMouseWheel(state: ScrollState): Modifier = this
    .pointerInput(state) {
        val lineStep = 56.dp.toPx()
        awaitPointerEventScope {
            while (true) {
                // Initial pass: see the wheel before the buttons and the scroller underneath do.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val delta = change.scrollDelta
                if (abs(delta.y) <= abs(delta.x) || state.maxValue == 0) continue
                if (state.dispatchRawDelta(wheelDeltaToPixels(delta.y, density, lineStep)) != 0f) change.consume()
            }
        }
    }
    .horizontalScroll(state)

/**
 * Web reports wheel deltas in CSS pixels (about 100 per notch); desktop reports wheel "lines"
 * (about 1 per notch, fractional for precise devices). Returns device pixels to scroll.
 */
internal fun wheelDeltaToPixels(delta: Float, density: Float, lineStepPx: Float): Float =
    if (abs(delta) >= PixelDeltaThreshold) delta * density else delta * lineStepPx

private const val PixelDeltaThreshold = 8f
