package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.i18n.Strings

/** Explicit source -> validation/preview -> confirmation. No operation exists until Insert. */
@Composable internal fun SequenceCreationDialog(
    owner: WorkspaceSession,
    baseline: Workspace,
    position: Vec2,
    readLive: () -> PenCanvasLive,
    commit: (WorkspaceOperation, CanvasObjectId) -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val capture = remember { SequenceCanvasCreation(owner, baseline, position) }
    var source by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<SequenceDiagramDraft?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<TransactionOperation?>(null) }
    var discard by remember { mutableStateOf(false) }
    val latestRead by rememberUpdatedState(readLive)
    val latestCommit by rememberUpdatedState(commit)

    fun close() {
        if (source.isEmpty() || discard) onDismiss() else discard = true
    }
    Dialog(onDismissRequest = ::close) {
        GlassSurface(Modifier.widthIn(max = 900.dp).fillMaxWidth().heightIn(max = 800.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(Strings.sequence.createTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                BasicText(Strings.sequence.authoringHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                if (parsed == null) {
                    BasicTextField(
                        source,
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
                    ShellButton(Strings.sequence.loadExample(), onClick = {
                        source = sequenceCreationExample
                        problem = null
                        discard = false
                    })
                    ShellButton(Strings.sequence.validatePreview(), enabled = source.isNotBlank(), onClick = {
                        when (val result = MermaidSequenceAdapter.parse(source)) {
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
                    ShellButton(if (confirmed) Strings.sequence.confirmed() else Strings.sequence.confirm(), enabled = prepared == null, onClick = {
                        confirmed =
                            !confirmed
                    })
                    ShellButton(Strings.sequence.insert(), enabled = confirmed, onClick = {
                        val live = latestRead()
                        if (!live.ready ||
                            !capture.isCurrent(live.session, live.workspace)
                        ) {
                            problem = Strings.sequence.stale()
                            return@ShellButton
                        }
                        val op =
                            prepared ?: runCatching {
                                capture.operation(
                                    draft,
                                    Strings.sequence.defaultTitle(),
                                    scene.layout,
                                    ::randomUuid,
                                )
                            }.getOrNull()
                                ?.also { prepared = it }
                        if (op == null) {
                            problem = Strings.sequence.invalidSize()
                            return@ShellButton
                        }
                        val target = (op.operations.first() as CreateObjectsOperation).objects.first().id
                        if (runCatching { latestCommit(op, target) }.getOrDefault(false)) {
                            onDismiss()
                        } else {
                            problem =
                                Strings.sequence.saveFailed()
                        }
                    })
                    ShellButton(Strings.sequence.editSource(), enabled = prepared == null, onClick = {
                        parsed = null
                        confirmed = false
                        problem = null
                    })
                }
                problem?.let { BasicText(it, style = TextStyle(color = colors.danger)) }
                ShellButton(if (discard) Strings.sequence.discard() else Strings.common.close(), onClick = ::close)
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
