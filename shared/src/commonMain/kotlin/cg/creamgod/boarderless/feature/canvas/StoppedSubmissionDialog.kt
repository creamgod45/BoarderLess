package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.PendingSubmissionReceipt
import cg.creamgod.boarderless.data.StoppedSubmissionVerification
import cg.creamgod.boarderless.data.StoppedWorkspaceChange
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Scope-owned lookup/verification; explicit atomic evidence save only when the adapter supports it.
 * Never applies a merge, clears quarantine or resends the original wire.
 */
@Composable internal fun StoppedSubmissionDialog(
    entries: List<StoppedWorkspaceChange>,
    canPublish: () -> Boolean,
    inspect: suspend (String) -> PendingSubmissionReceipt,
    verify: suspend (String) -> StoppedSubmissionVerification,
    saveEvidence: (suspend (String) -> StoppedSubmissionVerification)? = null,
    onEvidenceSaved: () -> Unit = {},
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val latestCanPublish by rememberUpdatedState(canPublish)
    val latestInspect by rememberUpdatedState(inspect)
    val latestVerify by rememberUpdatedState(verify)
    val latestSaveEvidence by rememberUpdatedState(saveEvidence)
    val latestEvidenceSaved by rememberUpdatedState(onEvidenceSaved)
    var checking by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var outcomes by remember { mutableStateOf<Map<String, PendingReceiptStatus>>(emptyMap()) }

    fun checkEntry(
        transactionId: String,
        full: Boolean,
        save: Boolean = false,
    ) {
        if (!latestCanPublish() || checking != null) return
        if (save && (!full || latestSaveEvidence == null)) return
        if (full && outcomes[transactionId] !in listOf(PendingReceiptStatus.Committed, PendingReceiptStatus.Fenced)) return
        checking = transactionId
        messages = messages - transactionId
        outcomes = outcomes - transactionId
        scope.launch {
            var message = if (save) Strings.status.stoppedArchiveSaveUncertain() else Strings.status.pendingReceiptFailed()
            var outcome: PendingReceiptStatus? = null
            try {
                check(latestCanPublish())
                outcome =
                    withTimeout(15_000) {
                        if (full) {
                            (if (save) requireNotNull(latestSaveEvidence)(transactionId) else latestVerify(transactionId))
                                .also {
                                    check(it.transactionId == transactionId && it.status != PendingReceiptStatus.Unknown)
                                }.status
                        } else {
                            latestInspect(transactionId).status
                        }
                    }
                coroutineContext.ensureActive()
                message =
                    if (save) {
                        Strings.status.stoppedArchiveEvidenceSaved()
                    } else {
                        when (outcome) {
                            PendingReceiptStatus.Committed -> {
                                if (full) {
                                    Strings.status.stoppedArchiveVerifiedCommitted()
                                } else {
                                    Strings.status
                                        .stoppedArchiveCommitted()
                                }
                            }

                            PendingReceiptStatus.Fenced -> {
                                if (full) {
                                    Strings.status.stoppedArchiveVerifiedFenced()
                                } else {
                                    Strings.status
                                        .pendingReceiptFenced()
                                }
                            }

                            else -> {
                                Strings.status.pendingReceiptUnknown()
                            }
                        }
                    }
            } catch (
                _: TimeoutCancellationException,
            ) {
                outcome = null
            } catch (
                cancelled: CancellationException,
            ) {
                outcome = null
                throw cancelled
            } catch (
                _: Exception,
            ) {
                outcome = null // Safe message, never raw private response.
            } finally {
                if (latestCanPublish()) {
                    messages = messages + (transactionId to message)
                    outcome?.let { outcomes = outcomes + (transactionId to it) }
                    checking = null
                    if (save && outcome != null) latestEvidenceSaved()
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 640.dp)
                    .fillMaxHeight(0.85f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.status.stoppedArchiveTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.status.stoppedArchiveNotice(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(entries, key = { it.transactionId }) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SelectionContainer {
                                    BasicText(entry.transactionId, style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                                }
                                if (entry.hasSavedEvidence) {
                                    BasicText(
                                        Strings.status.stoppedArchiveSavedRecord(),
                                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                                    )
                                }
                                messages[entry.transactionId]?.let {
                                    BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                                }
                                ShellButton(
                                    label =
                                        if (checking == entry.transactionId) {
                                            Strings.status.stoppedArchiveChecking()
                                        } else {
                                            Strings.status.pendingReceiptCheck()
                                        },
                                    enabled = checking == null,
                                    onClick = { checkEntry(entry.transactionId, full = false) },
                                )
                                if (outcomes[entry.transactionId] in listOf(PendingReceiptStatus.Committed, PendingReceiptStatus.Fenced)) {
                                    ShellButton(
                                        label = Strings.status.stoppedArchiveVerify(),
                                        enabled = checking == null,
                                        onClick = { checkEntry(entry.transactionId, full = true) },
                                    )
                                }
                                if (latestSaveEvidence != null &&
                                    outcomes[entry.transactionId] in listOf(PendingReceiptStatus.Committed, PendingReceiptStatus.Fenced)
                                ) {
                                    ShellButton(
                                        label = Strings.status.stoppedArchiveSaveEvidence(),
                                        enabled = checking == null,
                                        onClick = { checkEntry(entry.transactionId, full = true, save = true) },
                                    )
                                }
                            }
                        }
                    }
                    if (latestSaveEvidence == null) {
                        BasicText(
                            Strings.status.stoppedArchiveAtomicUnavailable(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                        )
                    }
                    ShellButton(label = Strings.status.stoppedArchiveReload(), enabled = checking == null, onClick = {
                        if (latestCanPublish()) {
                            messages = emptyMap()
                            outcomes = emptyMap()
                            onReload()
                        }
                    })
                    ShellButton(label = Strings.common.close(), onClick = onDismiss)
                }
            }
        }
    }
}
