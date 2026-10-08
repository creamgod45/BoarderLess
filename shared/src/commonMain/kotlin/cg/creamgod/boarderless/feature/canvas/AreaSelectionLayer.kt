package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import cg.creamgod.boarderless.domain.model.*

/** Own input above objects while marquee mode is active, so node drags cannot steal selection. */
@Composable internal fun AreaSelectionLayer(viewport: Viewport, onViewportChange: (Viewport) -> Unit,
    onChange: (Vec2, Vec2) -> Unit, onCancel: () -> Unit, onCommit: (Vec2, Vec2, Boolean) -> Unit) {
    val liveViewport by rememberUpdatedState(viewport)
    val pan by rememberUpdatedState(onViewportChange)
    val changeBox by rememberUpdatedState(onChange)
    val cancelBox by rememberUpdatedState(onCancel)
    val commitBox by rememberUpdatedState(onCommit)
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val event = awaitPointerEvent()
            val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: return@awaitEachGesture
            val secondary = event.buttons.isSecondaryPressed
            val additive = event.keyboardModifiers.isShiftPressed
            val start = Vec2(down.position.x, down.position.y)
            val initial = liveViewport
            var end = start
            var completed = false
            down.consume()
            try {
                if (!secondary) changeBox(start, end)
                completed = drag(down.id) { pointer ->
                    end = Vec2(pointer.position.x, pointer.position.y)
                    if (secondary) pan(initial.panBy(end - start)) else changeBox(start, end)
                    pointer.consume()
                }
                if (!secondary && completed) commitBox(start, end, additive)
            } finally {
                if (!secondary && !completed) cancelBox()
            }
        }
    })
}
