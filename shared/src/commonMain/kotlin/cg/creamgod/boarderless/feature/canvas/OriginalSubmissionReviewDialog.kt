package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings

/** Navigation/read-only receipt inspection, never fence, replay, clear or replacement writes. */
@Composable internal fun OriginalSubmissionReviewDialog(
    pending: PendingWorkspaceChange?, draft: PendingWorkspaceDraft?, busy: Boolean,
    enabled: Boolean, message: String?, onReadLocal: () -> Unit, onReadReceipt: () -> Unit,
    onReviewDraft: () -> Unit, onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth(.94f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.draftReview.originalTransactions(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.draftReview.originalTransactionsHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    if (busy) BasicText(Strings.draftReview.originalReading(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    pending?.let { BasicText("${it.transactionId} · ${it.operationCount}", style = TextStyle(color = colors.contentText)) }
                    draft?.let { BasicText("${it.id} · ${it.operationCount}", style = TextStyle(color = colors.contentText)) }
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                    ShellButton(Strings.draftReview.reloadOriginal(), enabled = enabled && !busy, onClick = onReadLocal)
                    ShellButton(Strings.draftReview.readOriginalReceipt(), enabled = enabled && !busy && pending != null,
                        onClick = onReadReceipt)
                    ShellButton(Strings.draftReview.review(), enabled = enabled && !busy && draft != null, onClick = onReviewDraft)
                    ShellButton(Strings.common.close(), onClick = onDismiss)
                }
            }
        }
    }
}
