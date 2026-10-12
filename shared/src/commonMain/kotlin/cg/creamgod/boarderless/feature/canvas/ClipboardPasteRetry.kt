package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    onRetry: suspend () -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        GlassSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(Strings.clipboardPaste.retryTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                BasicText(Strings.clipboardPaste.retryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                QuickSchemePreview(draft.payload, Modifier.fillMaxWidth().height(200.dp))
                if (!canRetry) BasicText(Strings.sequence.stale(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                if (saving) BasicText(Strings.status.savingChanges(1), style = TextStyle(color = colors.contentMuted))
                ShellButton(Strings.clipboardPaste.retrySave(), enabled = !saving && canRetry, onClick = {
                    if (!saving && canRetry) {
                        saving = true
                        scope.launch {
                            try { if (onRetry()) onDismiss() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Keep the original reviewed draft available. */ }
                            finally { saving = false }
                        }
                    }
                })
                ShellButton(Strings.common.close(), enabled = !saving, onClick = { if (!saving) onDismiss() })
            }
        }
    }
}
