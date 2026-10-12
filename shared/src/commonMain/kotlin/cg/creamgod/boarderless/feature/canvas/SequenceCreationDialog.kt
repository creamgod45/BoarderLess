package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Explicit source -> validation/preview -> confirmation. No operation exists until Insert. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SequenceCreationDialog(
    owner: WorkspaceSession,
    baseline: Workspace,
    position: Vec2,
    editingContainerId: CanvasObjectId? = null,
    maximumAtomicOperations: Int? = null,
    submissionOperationCount: (WorkspaceOperation) -> Int? = { null },
    readLive: () -> PenCanvasLive,
    commit: suspend (WorkspaceOperation, CanvasObjectId) -> Boolean,
    onDismiss: () -> Unit,
    onReviewOriginalSubmission: (() -> Unit)? = null,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val capture = remember { if (editingContainerId == null) SequenceCanvasCreation(owner, baseline, position) else null }
    val editor = remember { editingContainerId?.let { SequenceCanvasEditing(owner, baseline, it) } }
    fun isCurrent(live: PenCanvasLive) = capture?.isCurrent(live.session, live.workspace)
        ?: checkNotNull(editor).isCurrent(live.session, live.workspace)
    var source by remember { mutableStateOf(editor?.initialDraft?.let(MermaidSequenceAdapter::export) ?: "") }
    var parsed by remember { mutableStateOf<SequenceDiagramDraft?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<TransactionOperation?>(null) }
    var plannedOperationCount by remember { mutableStateOf<Int?>(null) }
    var commitAttempted by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var guided by remember { mutableStateOf(editor != null) }
    var guidedInitial by remember { mutableStateOf(editor?.initialDraft) }
    val latestRead by rememberUpdatedState(readLive)
    val latestCommit by rememberUpdatedState(commit)
    fun parseSource(): SequenceParseResult {
        return MermaidSequenceAdapter.parseKeepingUnchangedIdentity(source, guidedInitial)
    }

    fun close() {
        if (saving) return
        if (commitAttempted) { onDismiss(); return }
        if ((source.isEmpty() && !guided) || discard) onDismiss() else discard = true
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.widthIn(max = 900.dp).fillMaxWidth(.94f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(if (editor == null) Strings.sequence.createTitle() else Strings.sequence.editTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                BasicText(Strings.sequence.authoringHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                if (editor != null) BasicText(Strings.sequence.editHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                if (saving) BasicText(Strings.status.savingChanges(1), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                problem?.let { BasicText(it, style = TextStyle(color = colors.danger)) }
                if (commitAttempted && !saving) onReviewOriginalSubmission?.let { reviewOriginal ->
                    ShellButton(Strings.draftReview.originalTransactions(), onClick = { onDismiss(); reviewOriginal() })
                }
                ShellButton(if (discard) Strings.sequence.discard() else Strings.common.close(), enabled = !saving, onClick = ::close)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (guided) {
                    SequenceGuidedEditor(guidedInitial, onReview = { draft ->
                        source = MermaidSequenceAdapter.export(draft)
                        guidedInitial = draft.validatedSnapshot()
                        parsed = guidedInitial; guided = false; confirmed = false; problem = null; discard = false
                    }, onCancel = { guided = false })
                } else if (parsed == null) {
                    ShellButton(Strings.sequence.guidedOpen(), enabled = !saving && prepared == null, onClick = {
                        if (source.isBlank()) { guidedInitial = null; guided = true }
                        else when (val result = parseSource()) {
                            is SequenceParseResult.Parsed -> { guidedInitial = result.draft; guided = true; problem = null }
                            is SequenceParseResult.Rejected -> problem = Strings.sequence.parseFailure(result.diagnostic.line, result.diagnostic.code.name)
                        }
                    })
                    BasicTextField(
                        source,
                        enabled = !saving,
                        onValueChange = {
                            if (it.length <= 65536) {
                                source = it
                                problem = null
                                discard = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                        textStyle = TextStyle(color = colors.contentText),
                        cursorBrush = SolidColor(colors.contentText),
                    )
                    ShellButton(Strings.sequence.loadExample(), enabled = !saving, onClick = {
                        source = sequenceCreationExample
                        problem = null
                        discard = false
                    })
                    ShellButton(Strings.sequence.validatePreview(), enabled = !saving && source.isNotBlank(), onClick = {
                        when (val result = parseSource()) {
                            is SequenceParseResult.Parsed -> {
                                parsed = result.draft
                                confirmed = false
                                problem = null
                            }

                            is SequenceParseResult.Rejected -> {
                                problem =
                                    Strings.sequence.parseFailure(result.diagnostic.line, result.diagnostic.code.name)
                            }
                        }
                    })
                } else {
                    val draft = checkNotNull(parsed)
                    val scene = rememberSequenceScene(draft)
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
                        androidx.compose.foundation.layout.BoxWithConstraints {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val width = with(density) { maxWidth.toPx() }
                            val height = with(density) { maxHeight.toPx() }
                            val zoom =
                                minOf(
                                    (width - 16f) / scene.layout.size.width,
                                    (height - 16f) / scene.layout.size.height,
                                ).coerceAtLeast(.001f)
                            SequenceDiagramScene(scene, CanvasTransform(Vec2.Zero, scene.layout.size), Viewport(Vec2(8f, 8f), zoom))
                        }
                    }
                    BasicText(
                        Strings.sequence.reviewSummary(draft.participants.size, draft.messages().size),
                        style = TextStyle(color = colors.contentMuted),
                    )
                    if (maximumAtomicOperations != null && plannedOperationCount != null) BasicText(
                        Strings.sequence.operationBudget(checkNotNull(plannedOperationCount), maximumAtomicOperations),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    ShellButton(if (confirmed) Strings.sequence.confirmed() else Strings.sequence.confirm(), enabled = !saving && prepared == null, onClick = {
                        confirmed =
                            !confirmed
                    })
                    ShellButton(if (editor == null) Strings.sequence.insert() else Strings.sequence.saveEdit(), enabled = !saving && confirmed && !commitAttempted, onClick = {
                        val live = latestRead()
                        if (!live.ready ||
                            !isCurrent(live)
                        ) {
                            problem = Strings.sequence.stale()
                            return@ShellButton
                        }
                        if (editor?.isUnchanged(draft) == true) {
                            onDismiss()
                            return@ShellButton
                        }
                        val op =
                            prepared ?: runCatching {
                                if (editor != null) editor.operation(draft, scene.layout, ::randomUuid)
                                else checkNotNull(capture).operation(draft, Strings.sequence.defaultTitle(), scene.layout, ::randomUuid)
                            }.getOrNull()
                        if (op == null) {
                            problem = if (editor == null) Strings.sequence.invalidSize() else Strings.sequence.editBlocked()
                            return@ShellButton
                        }
                        val count = submissionOperationCount(op)
                        plannedOperationCount = count
                        if (maximumAtomicOperations != null && (count == null || count > maximumAtomicOperations)) {
                            problem = Strings.sequence.operationBudget(count ?: "—", maximumAtomicOperations)
                            return@ShellButton
                        }
                        prepared = op
                        val target = editor?.containerId ?: (op.operations.first() as CreateObjectsOperation).objects.first().id
                        if (saving) return@ShellButton
                        saving = true
                        scope.launch {
                            try {
                                val fresh = latestRead()
                                if (!fresh.ready || !isCurrent(fresh)) {
                                    problem = Strings.sequence.stale()
                                } else {
                                    commitAttempted = true
                                    if (latestCommit(op, target)) onDismiss()
                                    else problem = Strings.sequence.publicationUnconfirmed()
                                }
                            } catch (cancelled: CancellationException) {
                                if (commitAttempted) problem = Strings.sequence.publicationUnconfirmed()
                                throw cancelled
                            }
                            catch (_: Exception) {
                                problem = if (commitAttempted) Strings.sequence.publicationUnconfirmed() else Strings.sequence.saveFailed()
                            }
                            finally { saving = false }
                        }
                    })
                    ShellButton(Strings.sequence.editSource(), enabled = !saving && prepared == null, onClick = {
                        parsed = null
                        confirmed = false
                        problem = null
                        plannedOperationCount = null
                    })
                }
                }
            }
        }
        }
    }
}

internal const val sequenceCreationExample = """sequenceDiagram
participant Client
participant Transport
participant Stateful
Client->>Transport: Request
Transport->>Stateful: Load state
Stateful-->>Transport: State
alt Existing state
Transport->>Transport: Prepare response
Transport-->>Client: Existing response
else Missing state
loop Retry budget
Transport->>Stateful: Create state
Stateful-->>Transport: Created
end
Transport-->>Client: New response
end"""
