package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.i18n.Strings

/** Save failure retains the first identities and operation rather than preparing another paste. */
internal data class ClipboardPasteDraft(
    val owner: WorkspaceSession,
    val baseline: Workspace,
    val payload: ClipboardPayload,
    val insertion: ClipboardInsertion,
) {
    fun canRetry(live: WorkspaceSession?, current: Workspace, available: Boolean): Boolean =
        available && live != null && live.canEditContent && live.userId == owner.userId && live.clientId == owner.clientId &&
            live.workspace.id == baseline.id && live.workspaceVersion == owner.workspaceVersion && live.lastServerSeq == owner.lastServerSeq &&
            current == baseline
}

@Composable internal fun ClipboardPasteRetryDialog(
    draft: ClipboardPasteDraft,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(Strings.clipboardPaste.retryTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                BasicText(Strings.clipboardPaste.retryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                QuickSchemePreview(draft.payload, Modifier.fillMaxWidth().height(200.dp))
                if (!canRetry) BasicText(Strings.sequence.stale(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                ShellButton(Strings.clipboardPaste.retrySave(), enabled = canRetry, onClick = onRetry)
                ShellButton(Strings.common.close(), onClick = onDismiss)
            }
        }
    }
}
