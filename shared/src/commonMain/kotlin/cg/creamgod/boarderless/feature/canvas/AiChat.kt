package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.ai.AiDiagramRequestReview
import cg.creamgod.boarderless.data.ai.AiProbeDiagnostic
import cg.creamgod.boarderless.data.ai.AiProbeStage
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.i18n.Strings

internal enum class AiRequestState { Pending, Approved, Denied }

internal data class AiToolLine(
    val name: String,
    val status: AiDiagramToolStatus,
    val failure: AiDiagramToolFailure?,
    val input: String = "",
    val output: String = "",
)

/** Transcript of one AI conversation. Everything shown was already visible to the user or typed by them. */
internal sealed interface AiChatEntry {
    val id: Int

    data class User(override val id: Int, val text: String) : AiChatEntry

    data class Assistant(override val id: Int, val round: Int, val text: String) : AiChatEntry

    data class Tools(override val id: Int, val round: Int, val lines: List<AiToolLine>) : AiChatEntry

    data class Request(
        override val id: Int,
        val round: Int,
        val endpoint: String,
        val model: String,
        val body: String,
        val state: AiRequestState,
        val diagnostic: AiProbeDiagnostic? = null,
    ) : AiChatEntry
}

internal fun aiToolLabel(name: String): String =
    when (name) {
        "list_diagram_capabilities" -> Strings.aiTools.toolListCapabilities()
        "read_approved_graph" -> Strings.aiTools.toolReadGraph()
        "validate_diagram" -> Strings.aiTools.toolValidate()
        "create_nodes" -> Strings.aiTools.createNodes()
        "create_groups" -> Strings.aiTools.createGroups()
        "create_relations" -> Strings.aiTools.createRelations()
        "update_nodes" -> Strings.aiTools.updateNodes()
        "layout_nodes" -> Strings.aiTools.layoutNodes()
        "insert_node_on_relation" -> Strings.aiTools.insertRelationNode()
        else -> name
    }

internal fun aiToolLines(
    calls: List<AiDiagramToolCall>,
    results: List<AiDiagramToolResult>,
): List<AiToolLine> =
    results.mapIndexed { index, result ->
        AiToolLine(
            calls.getOrNull(index)?.name ?: "?",
            result.status,
            result.failure,
            calls.getOrNull(index)?.arguments.orEmpty(),
            result.data.toString(),
        )
    }

@Composable
private fun Bubble(
    mine: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (mine) colors.accent.copy(alpha = .18f) else colors.contentSurface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiChatEntryView(
    entry: AiChatEntry,
    pending: AiDiagramRequestReview?,
    canRespond: Boolean,
    onApprove: (AiDiagramRequestReview) -> Unit,
    onDeny: (AiDiagramRequestReview) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val small = TextStyle(color = colors.contentMuted, fontSize = 11.sp)
    val body = TextStyle(color = colors.contentText, fontSize = 13.sp)
    when (entry) {
        is AiChatEntry.User ->
            Bubble(mine = true) {
                BasicText(Strings.aiTools.chatYou(), style = small)
                SelectionContainer { BasicText(entry.text, style = body) }
            }

        is AiChatEntry.Assistant ->
            Bubble(mine = false) {
                BasicText(Strings.aiTools.chatAssistant(), style = small)
                SelectionContainer { BasicText(entry.text, style = body) }
            }

        is AiChatEntry.Tools ->
            Bubble(mine = false) {
                entry.lines.forEachIndexed { index, line ->
                    var details by remember(entry.id, index) { mutableStateOf(false) }
                    BasicText(
                        Strings.aiTools.toolCalled(aiToolLabel(line.name)) + " · " + line.name +
                            " — " +
                            when {
                                line.failure != null -> Strings.aiTools.toolRejected(line.failure.name)
                                line.status == AiDiagramToolStatus.Read -> Strings.aiTools.toolRead()
                                else -> Strings.aiTools.toolProposed()
                            },
                        style = TextStyle(color = if (line.failure != null) colors.danger else colors.contentText, fontSize = 12.sp),
                    )
                    ShellButton(
                        if (details) Strings.aiTools.toolDetailsHide() else Strings.aiTools.toolDetails(),
                        compact = true,
                        onClick = { details = !details },
                    )
                    if (details) {
                        val limit = 4000
                        fun shown(text: String) = if (text.length > limit) text.take(limit) + "…" else text
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                BasicText(Strings.aiTools.toolInput(), style = small)
                                BasicText(shown(line.input), Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                                BasicText(Strings.aiTools.toolOutput(), style = small)
                                BasicText(shown(line.output), Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }

        is AiChatEntry.Request -> {
            var showBody by remember(entry.id) { mutableStateOf(false) }
            val live = entry.state == AiRequestState.Pending && pending != null && pending.round == entry.round &&
                pending.body == entry.body && pending.server.endpoint == entry.endpoint && pending.server.body.model == entry.model
            Bubble(mine = false) {
                BasicText(
                    when (entry.state) {
                        AiRequestState.Pending -> Strings.aiTools.requestTitle(entry.round)
                        AiRequestState.Approved -> Strings.aiTools.requestApproved(entry.round)
                        AiRequestState.Denied -> Strings.aiTools.requestDenied(entry.round)
                    },
                    style = TextStyle(color = colors.contentText, fontSize = 13.sp),
                )
                BasicText(Strings.aiTools.requestTarget(entry.endpoint, entry.model), style = small)
                entry.diagnostic?.let { event ->
                    BasicText(aiProbeDiagnosticLabel(event) + (event.httpStatus?.let { " · HTTP $it" } ?: ""), style = small)
                    if (event.stage == AiProbeStage.Failed) {
                        BasicText(aiProbeDiagnosticHint(event), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    }
                }
                if (live) BasicText(Strings.aiTools.waiting(), style = small)
                ShellButton(if (showBody) Strings.aiTools.hideBody() else Strings.aiTools.showBody(), compact = true, onClick = { showBody = !showBody })
                if (showBody) {
                    SelectionContainer {
                        BasicText(entry.body, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                    }
                }
                if (live && pending != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.aiTools.approve(), accent = true, enabled = canRespond, onClick = { onApprove(pending) })
                        ShellButton(Strings.aiTools.deny(), onClick = { onDeny(pending) })
                    }
                }
            }
        }
    }
}
