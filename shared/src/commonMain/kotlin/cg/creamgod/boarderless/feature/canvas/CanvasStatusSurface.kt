package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cg.creamgod.boarderless.designsystem.GlassSurface

// A composable boundary keeps recovery controls out of the large canvas method.
@Composable
internal fun CanvasStatusSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier) { GlassSurface { Column(content = content) } }
}

// Box is inline; this boundary separates the canvas body from setup and effects.
@Composable
internal fun WorkspaceCanvasSurface(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, content = content)
}
