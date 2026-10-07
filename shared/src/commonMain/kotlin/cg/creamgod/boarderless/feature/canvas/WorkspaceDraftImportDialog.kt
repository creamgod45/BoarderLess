package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.DraftMergeChoice
import cg.creamgod.boarderless.data.DraftMergeFieldId
import cg.creamgod.boarderless.data.DraftMergePreflightResult
import cg.creamgod.boarderless.data.WorkspaceDraftMergePlan
import cg.creamgod.boarderless.data.WorkspaceDraftReview
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Explicit pasted JSON inspection. No clipboard reads, file grants, merge or automatic submission. */
@Composable internal fun WorkspaceDraftImportDialog(
    onInspect: suspend (String) -> WorkspaceDraftReview,
    onSelectJson: (suspend () -> String?)? = null,
    onCheckMerge: (suspend (WorkspaceDraftMergePlan, Map<DraftMergeFieldId, DraftMergeChoice>) -> DraftMergePreflightResult)? = null,
    onReloadReview: ((WorkspaceDraftReview, cg.creamgod.boarderless.data.WorkspaceSession) -> WorkspaceDraftReview)? = null,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<WorkspaceDraftReview?>(null) }
    var open by remember { mutableStateOf(true) }
    val inspect by rememberUpdatedState(onInspect)
    val selectJson by rememberUpdatedState(onSelectJson)
    val dismiss = {
        open = false
        content = ""
        preview = null
        onDismiss()
    }
    val reviewed = preview
    if (reviewed != null) {
        key(reviewed) {
            WorkspaceDraftReviewDialog(
                reviewed,
                false,
                Strings.draftImport.previewWarning(),
                onCopyBackup = null,
                onSaveBackup = null,
                saveChoosesFolder = false,
                saveUsesBrowserDownload = false,
                imported = true,
                onCheckMerge = onCheckMerge,
                onReloadReview =
                    if (onReloadReview != null) {
                        (
                            { current ->
                                if (open) preview = onReloadReview(reviewed, current)
                            }
                        )
                    } else {
                        null
                    },
                onDismiss = dismiss,
            )
        }
        return
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).fillMaxHeight(0.8f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.draftImport.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(
                        if (onSelectJson != null) Strings.draftImport.fileWarning() else Strings.draftImport.warning(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                    )
                    if (onSelectJson != null) {
                        ShellButton(label = Strings.draftImport.selectFile(), enabled = !busy, onClick = {
                            content = ""
                            message = null
                            busy = true
                            scope.launch {
                                try {
                                    val selected = selectJson?.invoke()
                                    ensureActive()
                                    if (open) {
                                        if (selected == null) {
                                            message = Strings.draftImport.selectionCancelled()
                                        } else {
                                            content = selected
                                        }
                                    }
                                } catch (
                                    cancelled: CancellationException,
                                ) {
                                    throw cancelled
                                } catch (
                                    _: Exception,
                                ) {
                                    if (open) message = Strings.draftImport.failed()
                                } finally {
                                    busy = false
                                }
                            }
                        })
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = {
                            if (it.length <= 4 * 1024 * 1024) {
                                content = it
                                message = null
                            } else {
                                message = Strings.draftImport.failed()
                            }
                        },
                        enabled = !busy,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(colors.canvas)
                                .border(1.dp, colors.contentBorder)
                                .padding(10.dp),
                        textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                        cursorBrush = SolidColor(colors.accent),
                    )
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShellButton(label = Strings.draftImport.inspect(), enabled = !busy && content.isNotBlank(), onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val result = inspect(content)
                                    ensureActive()
                                    if (open) {
                                        preview = result
                                        content = ""
                                    }
                                } catch (
                                    cancelled: CancellationException,
                                ) {
                                    throw cancelled
                                } catch (
                                    _: Exception,
                                ) {
                                    if (open) message = Strings.draftImport.failed()
                                } finally {
                                    busy = false
                                }
                            }
                        })
                        ShellButton(label = Strings.common.cancel(), onClick = dismiss)
                    }
                }
            }
        }
    }
}
