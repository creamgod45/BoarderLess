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
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Confirmation stages this exact frozen plan locally. It does not apply content or send HTTP. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LocalSyncMergeDialog(
    snapshot: LocalWorkspaceSyncSnapshot,
    remote: WorkspaceSession,
    canContinue: () -> Boolean,
    onStage: suspend (LocalWorkspaceSyncProposal) -> Unit,
    onDismiss: () -> Unit,
    heading: String? = null,
    hint: String? = null,
    initialChoices: Map<String, LocalSyncMergeChoice>? = null,
) {
    val colors = BoarderLessTheme.colors
    val plan = remember(snapshot, remote) { LocalWorkspaceSyncMergePlan(snapshot, remote) }
    val saved = remember(snapshot, remote) {
        snapshot.proposal?.takeIf { snapshot.proposalIsCurrent && it.remoteSession() == remote }
    }
    var choices by remember(plan) { mutableStateOf(initialChoices ?: saved?.choices.orEmpty()) }
    val target = remember(plan, choices) { runCatching { plan.resolve(choices) } }
    var confirmation by remember(plan) { mutableStateOf<LocalWorkspaceSyncProposal?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val stillCurrent by rememberUpdatedState(canContinue)
    val save by rememberUpdatedState(onStage)

    fun decide(id: String, choice: LocalSyncMergeChoice) {
        if (busy) return
        choices = choices + (id to choice)
        confirmation = null; message = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 960.dp).fillMaxWidth(.94f).heightIn(max = 820.dp).fillMaxHeight(.92f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(heading ?: Strings.localSync.mergeTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(hint ?: Strings.localSync.mergeHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    if (snapshot.proposal != null) BasicText(
                        if (saved != null) Strings.localSync.proposalReopened() else Strings.localSync.proposalStale(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    message?.let { BasicText(it, style = TextStyle(color = colors.accent, fontSize = 12.sp)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val prepared = confirmation
                        if (prepared == null) {
                            ShellButton(Strings.localSync.prepareMerge(), enabled = !busy && target.isSuccess, onClick = {
                                runCatching { plan.proposal(randomUuid(), choices) }
                                    .onSuccess { confirmation = it }
                                    .onFailure { message = Strings.localSync.mergeInvalid() }
                            })
                        } else {
                            ShellButton(Strings.localSync.confirmMerge(), enabled = !busy, accent = true, onClick = {
                                if (!stillCurrent()) return@ShellButton
                                busy = true; message = null
                                scope.launch {
                                    try {
                                        check(stillCurrent())
                                        save(prepared)
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Throwable) { if (stillCurrent()) message = Strings.localSync.proposalFailed() }
                                    finally { busy = false }
                                }
                            })
                            ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { confirmation = null })
                        }
                        ShellButton(Strings.common.close(), onClick = onDismiss)
                    }
                    if (confirmation != null) BasicText(Strings.localSync.confirmMergeHint(),
                        style = TextStyle(color = colors.accent, fontSize = 12.sp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { WorkspaceComparisonPreview(plan.review, target.getOrNull()) }
                        if (target.isFailure) item {
                            BasicText(Strings.localSync.mergeInvalid(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                        }
                        items(plan.rows, key = { it.id }) { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BasicText(mergeRowLabel(row, plan.review), style = TextStyle(color = colors.contentText, fontSize = 14.sp))
                                BasicText(row.id, style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                                BasicText(if (row.requiresChoice) Strings.localSync.choiceRequired() else Strings.localSync.choiceAutomatic(),
                                    style = TextStyle(color = if (row.requiresChoice) colors.accent else colors.contentMuted, fontSize = 11.sp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val effective = choices[row.id] ?: row.automatic?.takeUnless { row.requiresChoice }
                                    ShellButton(Strings.localSync.chooseLocal(), accent = effective == LocalSyncMergeChoice.Local,
                                        enabled = !busy, onClick = { decide(row.id, LocalSyncMergeChoice.Local) })
                                    ShellButton(Strings.localSync.chooseRemote(), accent = effective == LocalSyncMergeChoice.Remote,
                                        enabled = !busy, onClick = { decide(row.id, LocalSyncMergeChoice.Remote) })
                                }
                                listOf(Strings.draftReview.baseline() to row.baseline, Strings.draftReview.proposed() to row.local,
                                    Strings.draftReview.remote() to row.remote).forEach { (label, value) ->
                                    BasicText(label, style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                                    SelectionContainer { BasicText(value, style = TextStyle(color = colors.contentText, fontSize = 11.sp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mergeRowLabel(row: LocalSyncMergeRow, review: WorkspaceDraftReview): String = when (row.id) {
    "document:title" -> Strings.localSync.mergeDocumentTitle()
    "document:canvasStyle" -> Strings.localSync.mergeCanvasStyle()
    "document:sequenceDiagrams" -> Strings.localSync.mergeSequences()
    else -> {
        val raw = row.id.substringAfter(':')
        if (row.id.startsWith("object:")) {
            val id = CanvasObjectId(raw)
            val objectValue = review.proposed.objects[id] ?: review.current.objects[id] ?: review.baseline.objects[id]
            val name = when (objectValue) {
                is TextNode -> objectValue.text
                is GroupFrame -> objectValue.title
                is MediaNode -> objectValue.altText.ifBlank { objectValue.assetId }
                is ExternalMediaNode -> objectValue.altText.ifBlank { objectValue.reference.id }
                null -> raw
            }
            Strings.localSync.mergeObject(name.take(80))
        } else {
            val id = RelationId(raw)
            val relation = review.proposed.relations[id] ?: review.current.relations[id] ?: review.baseline.relations[id]
            Strings.localSync.mergeRelation(relation?.label?.takeIf { it.isNotBlank() }?.take(80) ?: raw)
        }
    }
}
