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
import kotlinx.coroutines.CancellationException

internal enum class PenCanvasInsertResult { Saved, Retry, Unconfirmed, Stale, Invalid }

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
    commit: suspend (WorkspaceOperation, CanvasObjectId) -> Boolean,
    onDismiss: () -> Unit,
    onReviewOriginalSubmission: (() -> Unit)? = null,
) {
    val original = remember { (selection.singleOrNull()?.let(baseline.objects::get) as? TextNode)?.takeIf { it.vectorPath != null } }
    val editing = remember { original?.let { runCatching { PenCanvasEditing(owner, baseline, it) }.getOrNull() } }
    val capture = remember { PenCanvasCreation(owner, baseline, position) }
    var prepared by remember { mutableStateOf<WorkspaceOperation?>(null) }
    var publicationAttempted by remember { mutableStateOf(false) }
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
        onReviewOriginalSubmission = onReviewOriginalSubmission,
        initialText = original?.text ?: "",
        initialTextPlacement = original?.textPlacement,
        initialDraft = editing?.draft ?: PenPathDraft(CanvasSize(400f, 300f)),
        editingExisting = editing != null,
        onInsert =
            if (supported) {
                apply@{ path, text, placement ->
                    if (publicationAttempted) return@apply PenCanvasInsertResult.Unconfirmed
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
                                    editing.operation(path, randomUuid(), text, placement)
                                } else {
                                    capture.operation(path, randomUuid(), randomUuid(), text, placement)
                                }
                            if (candidate == null) return@apply PenCanvasInsertResult.Saved
                            candidate.also { prepared = it }
                        } catch (_: Exception) {
                            return@apply PenCanvasInsertResult.Invalid
                        }
                    val target = original?.id ?: (operation as CreateObjectsOperation).objects.single().id
                    publicationAttempted = true
                    try {
                        if (latestCommit(operation, target)) PenCanvasInsertResult.Saved else PenCanvasInsertResult.Unconfirmed
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        _: Exception,
                    ) {
                        PenCanvasInsertResult.Unconfirmed
                    }
                }
            } else {
                null
            },
    )
}
