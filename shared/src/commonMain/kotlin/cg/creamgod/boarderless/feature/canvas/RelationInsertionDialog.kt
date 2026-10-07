package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** Explicit preview and confirmation. No operation is staged by merely selecting a relation. */
@Composable internal fun RelationInsertionDialog(
    draft: RelationInsertionDraft,
    isCurrent: () -> Boolean,
    onConfirm: (RelationInsertionPlan) -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    var text by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(draft.original.label.orEmpty()) }
    var intent by remember { mutableStateOf(draft.original.intent.orEmpty()) }
    var x by remember { mutableStateOf((draft.center.x - 130f).toString()) }
    var y by remember { mutableStateOf((draft.center.y - 66f).toString()) }
    var width by remember { mutableStateOf("260") }
    var height by remember { mutableStateOf("132") }
    var confirmed by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val plan =
        remember(text, label, intent, x, y, width, height) {
            runCatching {
                draft.plan(
                    text,
                    label.ifEmpty {
                        null
                    },
                    intent.ifEmpty { null },
                    Vec2(x.toFloat(), y.toFloat()),
                    CanvasSize(width.toFloat(), height.toFloat()),
                )
            }.getOrNull()
        }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 680.dp).fillMaxWidth(.94f).fillMaxHeight(.86f)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.relationInsert.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.relationInsert.explanation(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    BasicText(
                        when (draft.original.direction) {
                            RelationDirection.Backward -> Strings.relationInsert.backward()
                            RelationDirection.Both -> Strings.relationInsert.both()
                            RelationDirection.None -> Strings.relationInsert.undirected()
                            RelationDirection.Forward -> Strings.relationInsert.forward()
                        },
                        style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                    )
                    AiProbeField(Strings.relationInsert.nodeText(), text, {
                        if (!attempted &&
                            it.encodeToByteArray().size <= 64 * 1024
                        ) {
                            text = it
                            confirmed = false
                        }
                    }, enabled = !attempted, multiline = true)
                    AiProbeField(Strings.relationInsert.firstLabel(), label, {
                        if (!attempted &&
                            it.encodeToByteArray().size <= 64 * 1024
                        ) {
                            label = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    AiProbeField(Strings.relationInsert.firstIntent(), intent, {
                        if (!attempted &&
                            it.encodeToByteArray().size <= 4096
                        ) {
                            intent = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    AiProbeField("X", x, {
                        if (!attempted && it.length <= 24) {
                            x = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    AiProbeField("Y", y, {
                        if (!attempted && it.length <= 24) {
                            y = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    AiProbeField(Strings.relationInsert.width(), width, {
                        if (!attempted &&
                            it.length <= 24
                        ) {
                            width = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    AiProbeField(Strings.relationInsert.height(), height, {
                        if (!attempted &&
                            it.length <= 24
                        ) {
                            height = it
                            confirmed = false
                        }
                    }, enabled = !attempted)
                    plan?.let { p ->
                        val area = p.node.transform
                        val nearby =
                            p.preview.objects.values
                                .filter { node ->
                                    node.id != p.first.sourceObjectId && node.id != p.node.id && node.id != p.second.targetObjectId &&
                                        node.transform.position.x < area.position.x + area.size.width + 160 &&
                                        node.transform.position.x + node.transform.size.width > area.position.x - 160 &&
                                        node.transform.position.y < area.position.y + area.size.height + 160 &&
                                        node.transform.position.y + node.transform.size.height > area.position.y - 160
                                }.mapIndexed { index, node -> "C${index + 1}" to node.id }
                                .toMap()
                        BasicText(Strings.relationInsert.contextPreview(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        AiDiagramDraftPreview(
                            p.preview,
                            mapOf("A" to p.first.sourceObjectId, "N" to p.node.id, "B" to p.second.targetObjectId) + nearby,
                        )
                        val start = p.preview.objects.getValue(p.first.sourceObjectId)
                        val end = p.preview.objects.getValue(p.second.targetObjectId)

                        fun title(node: CanvasObject) =
                            when (node) {
                                is TextNode -> node.text
                                is GroupFrame -> node.title
                                is MediaNode -> node.altText
                            }
                        BasicText(
                            "${title(start)}\n${if (p.first.direction == RelationDirection.Both) {
                                "↕"
                            } else if (p.first.direction == RelationDirection.None) {
                                "—"
                            } else {
                                "↓"
                            }}\n${p.node.text}\n${if (p.second.direction == RelationDirection.Both) {
                                "↕"
                            } else if (p.second.direction == RelationDirection.None) {
                                "—"
                            } else {
                                "↓"
                            }}\n${title(end)}",
                            style = TextStyle(color = colors.contentText, fontSize = 15.sp),
                        )
                    }
                    if (plan == null &&
                        text.isNotBlank()
                    ) {
                        BasicText(Strings.relationInsert.invalid(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    }
                    if (!isCurrent() || error) {
                        BasicText(
                            Strings.relationInsert.failed(),
                            style = TextStyle(color = colors.danger, fontSize = 12.sp),
                        )
                    }
                    ShellButton(Strings.relationInsert.consent(), accent = confirmed, enabled = plan != null && isCurrent(), onClick = {
                        confirmed =
                            !confirmed
                    })
                    ShellButton(
                        Strings.relationInsert.apply(),
                        enabled = confirmed && plan != null && isCurrent(),
                        onClick = apply@{
                            if (!confirmed || !isCurrent()) return@apply
                            confirmed = false
                            attempted = true
                            if (onConfirm(checkNotNull(plan))) onDismiss() else error = true
                        },
                    )
                    ShellButton(Strings.common.cancel(), onClick = onDismiss)
                }
            }
        }
    }
}
