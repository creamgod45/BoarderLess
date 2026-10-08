package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Viewport
import cg.creamgod.boarderless.i18n.Strings

@Composable
internal fun BoxScope.CanvasBrushControls(
    supported: Boolean, active: Boolean, enabled: Boolean, viewport: Viewport,
    readLive: () -> PenCanvasLive, commit: (WorkspaceOperation, CanvasObjectId) -> Boolean,
    onClose: () -> Unit, onToolbarHeight: (Int) -> Unit, onStart: () -> Unit,
) {
    if (!supported) return
    if (active) {
        BrushCanvasLayer(viewport, readLive, commit, onClose, onToolbarHeight)
    } else {
        ShellButton(Strings.brush.title(), enabled = enabled,
            hint = Strings.brush.hint(), onClick = onStart,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

internal class CanvasBrushState {
    var lastObjectId by mutableStateOf<CanvasObjectId?>(null)
    var toolbarHeightPx by mutableStateOf(0)
}

@Composable
internal fun rememberCanvasBrushState(scope: Any?): CanvasBrushState = remember(scope) { CanvasBrushState() }
