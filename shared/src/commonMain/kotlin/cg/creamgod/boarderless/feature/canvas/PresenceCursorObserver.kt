package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import cg.creamgod.boarderless.domain.model.Vec2

/** Attach only to the canvas scene, never a root containing form/key fields or panels.
 * Passive Initial-pass observation: no consume, focus, gesture detection or transform mutation.
 * Parent coordinates are screen coordinates even when a child node is rotated.
 */
@Composable
internal fun Modifier.presenceCursorObserver(
    enabled: Boolean,
    ownerId: String,
    onSample: (Vec2?) -> Unit,
): Modifier {
    val latestSample = rememberUpdatedState(onSample)
    return pointerInput(enabled, ownerId) {
        if (!enabled) return@pointerInput
        // Capture this owner's callback. An old finally must not clear a new owner's sample.
        val publishSample = latestSample.value
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull()
                    val clear = event.type == PointerEventType.Exit ||
                        (event.type == PointerEventType.Release && pointer?.type == PointerType.Touch)
                    val position = pointer?.position
                    publishSample(if (clear || position == null || !position.x.isFinite() || !position.y.isFinite())
                        null else Vec2(position.x, position.y))
                }
            }
        } finally {
            publishSample(null)
        }
    }
}
