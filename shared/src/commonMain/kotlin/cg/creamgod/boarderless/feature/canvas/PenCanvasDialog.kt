package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

internal enum class PenCanvasInsertResult { Saved, Retry, Stale, Invalid }

internal data class PenCanvasLive(
    val session: WorkspaceSession?,
    val workspace: Workspace,
    val ready: Boolean,
)

@Composable internal fun PenEditAction(
    workspace: Workspace,
    selection: Set<CanvasObjectId>,
    enabled: Boolean,
    supported: Boolean,
    onEdit: () -> Unit,
) {
    val node = (selection.singleOrNull()?.let(workspace.objects::get) as? TextNode)?.takeIf { it.vectorPath != null } ?: return
    if (supported) ShellButton(Strings.penDraft.editPath(), enabled = enabled && editablePenNode(workspace, node), onClick = onEdit)
}

@Composable internal fun PenCanvasDialog(
    owner: WorkspaceSession,
    baseline: Workspace,
    position: Vec2,
    selection: Set<CanvasObjectId>,
    supported: Boolean,
    readLive: () -> PenCanvasLive,
    commit: (WorkspaceOperation, CanvasObjectId) -> Boolean,
    onDismiss: () -> Unit,
) {
    val original = remember { (selection.singleOrNull()?.let(baseline.objects::get) as? TextNode)?.takeIf { it.vectorPath != null } }
    val editing = remember { original?.let { runCatching { PenCanvasEditing(owner, baseline, it) }.getOrNull() } }
    val capture = remember { PenCanvasCreation(owner, baseline, position) }
    var prepared by remember { mutableStateOf<WorkspaceOperation?>(null) }
    val latestLive by rememberUpdatedState(readLive)
    val latestCommit by rememberUpdatedState(commit)
    if (original != null && editing == null) {
        Dialog(onDismissRequest = onDismiss) {
            GlassSurface(Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(Strings.penDraft.editUnavailable(), style = TextStyle(color = BoarderLessTheme.colors.contentText))
                    ShellButton(Strings.common.close(), onClick = onDismiss)
                }
            }
        }
        return
    }
    PenEditorDialog(
        onDismiss = onDismiss,
        initialDraft = editing?.draft ?: PenPathDraft(CanvasSize(400f, 300f)),
        editingExisting = editing != null,
        onInsert =
            if (supported) {
                apply@{ path ->
                    val current = latestLive()
                    if (!current.ready ||
                        !(editing?.isCurrent(current.session, current.workspace) ?: capture.isCurrent(current.session, current.workspace))
                    ) {
                        return@apply PenCanvasInsertResult.Stale
                    }
                    val operation =
                        prepared ?: try {
                            val candidate =
                                if (editing !=
                                    null
                                ) {
                                    editing.operation(path, randomUuid())
                                } else {
                                    capture.operation(path, randomUuid(), randomUuid())
                                }
                            if (candidate == null) return@apply PenCanvasInsertResult.Saved
                            candidate.also { prepared = it }
                        } catch (_: Exception) {
                            return@apply PenCanvasInsertResult.Invalid
                        }
                    val target = original?.id ?: (operation as CreateObjectsOperation).objects.single().id
                    try {
                        if (latestCommit(operation, target)) PenCanvasInsertResult.Saved else PenCanvasInsertResult.Retry
                    } catch (
                        _: Exception,
                    ) {
                        PenCanvasInsertResult.Retry
                    }
                }
            } else {
                null
            },
    )
}
