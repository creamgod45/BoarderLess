package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.designsystem.color.*
import cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** Document grid style only. Visibility/snap/viewport are untouched; preview is ephemeral. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CanvasGridStyleDialog(owner: WorkspaceSession, baseline: Workspace,
    model: ColorModel, onModelChange: (ColorModel) -> Unit, busy: Boolean, readLive: () -> PenCanvasLive,
    onPreview: (CanvasGridStyle?) -> Unit, onApply: (UpdateCanvasStyleOperation, () -> Unit, () -> Unit) -> Unit,
    onDismiss: () -> Unit, onReviewOriginalSubmission: (() -> Unit)? = null) {
    val colors = BoarderLessTheme.colors
    val capture = remember { CanvasStyleDraft.capture(owner, baseline) }
    var chosen by remember { mutableStateOf(baseline.canvasStyle.gridStyle) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<UpdateCanvasStyleOperation?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var publicationFailed by remember { mutableStateOf(false) }
    val currentPreview by rememberUpdatedState(onPreview)
    DisposableEffect(Unit) { onDispose { currentPreview(null) } }
    LaunchedEffect(chosen) { currentPreview(chosen) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            if (picking) {
                val custom = customNodeColor(chosen.colorToken)
                ColorPickerWindow(chosen.colorToken,
                    custom ?: RgbColor(colors.contentMuted.red, colors.contentMuted.green, colors.contentMuted.blue),
                    listOf(ColorSwatchOption("auto", Strings.canvasBackground.gridAutomatic(), colors.contentMuted)), emptyList(),
                    model, onModelChange,
                    onPreview = { token -> currentPreview(chosen.copy(colorToken = token)) },
                    onApply = { token -> chosen = chosen.copy(colorToken = token); picking = false },
                    onCancel = { currentPreview(chosen); picking = false },
                    onEditingChange = {}, title = Strings.canvasBackground.gridColor(), busy = busy)
            } else GlassSurface(Modifier.widthIn(max = 560.dp).fillMaxWidth(.94f).fillMaxHeight(.8f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.canvasBackground.gridTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.canvasBackground.gridHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    if (problem) BasicText(Strings.canvasBackground.changedWhileEditing(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    if (publicationFailed) BasicText(Strings.canvasBackground.publicationUnconfirmed(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    else if (attempted) BasicText(Strings.canvasBackground.publicationPending(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    if (attempted && !busy) onReviewOriginalSubmission?.let { reviewOriginal ->
                        ShellButton(Strings.draftReview.originalTransactions(), onClick = { onDismiss(); reviewOriginal() })
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("lines", "dots", "none").forEach { pattern ->
                            val label = when (pattern) {
                                "lines" -> Strings.canvasBackground.gridLines()
                                "dots" -> Strings.canvasBackground.gridDots()
                                else -> Strings.canvasBackground.gridNone()
                            }
                            ShellButton(label, accent = chosen.pattern == pattern, enabled = !busy && prepared == null,
                                onClick = { chosen = chosen.copy(pattern = pattern); problem = false })
                        }
                    }
                    ShellButton(Strings.canvasBackground.gridColor(), enabled = !busy && prepared == null, onClick = { picking = true })
                    ShellButton(Strings.canvasBackground.gridAutomatic(), enabled = !busy && prepared == null,
                        accent = chosen.colorToken == "auto", onClick = { chosen = chosen.copy(colorToken = "auto") })
                    BasicText(chosen.colorToken, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    }
                    ShellButton(Strings.canvasBackground.gridApply(), accent = true, enabled = !busy && !attempted, onClick = {
                        val live = readLive()
                        try {
                            require(live.ready && live.session?.let(capture::matches) == true)
                            capture.requireCurrent(requireNotNull(live.session), live.workspace)
                            val operation = prepared ?: capture.gridOperation(requireNotNull(live.session), live.workspace, chosen, randomUuid())
                            if (operation == null) onDismiss() else {
                                prepared = operation
                                attempted = true
                                onApply(operation, onDismiss, { publicationFailed = true })
                            }
                        } catch (_: Exception) {
                            if (attempted) publicationFailed = true else problem = true
                        }
                    })
                    ShellButton(Strings.common.cancel(), enabled = !busy, onClick = onDismiss)
                }
            }
        }
    }
}
