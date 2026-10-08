package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.WorkspaceStorageMode
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings

@Composable internal fun WorkspaceStorageChooser(failed: Boolean, onChoose: (WorkspaceStorageMode) -> Unit) {
    val colors = BoarderLessTheme.colors
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.padding(20.dp).widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(Strings.workspaceStorage.title(), style = TextStyle(color = colors.contentText, fontSize = 20.sp))
                BasicText(Strings.workspaceStorage.explanation(), style = TextStyle(color = colors.contentMuted, fontSize = 14.sp))
                ShellButton(Strings.workspaceStorage.local(), onClick = { onChoose(WorkspaceStorageMode.Local) }, modifier = Modifier.fillMaxWidth())
                ShellButton(Strings.workspaceStorage.server(), onClick = { onChoose(WorkspaceStorageMode.Server) }, modifier = Modifier.fillMaxWidth())
                if (failed) BasicText(Strings.workspaceStorage.preferenceFailed(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
            }
        }
    }
}
