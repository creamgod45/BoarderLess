package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class AiDraftChangeKind { Added, Changed, Removed }
internal data class AiDraftChange(val id: String, val kind: AiDraftChangeKind, val before: String?, val after: String?)
private val AiReviewJson = Json { encodeDefaults = true; allowStructuredMapKeys = true }

/** Presentation only. Exact proposal operations remain the sole Apply input. Normalize only
 * entity revision for meaningful diff; do not omit geometry, parents or media references. */
internal fun aiDraftChanges(before: Workspace, after: Workspace): List<AiDraftChange> {
    require(before.id == after.id)
    return buildList {
        fun addDifference(id: String, old: String?, new: String?) {
            if (old == new) return
            add(AiDraftChange(id, when { old == null -> AiDraftChangeKind.Added; new == null -> AiDraftChangeKind.Removed
                else -> AiDraftChangeKind.Changed }, old, new))
        }
        (before.objects.keys + after.objects.keys).sortedBy { it.value }.forEach { id ->
            fun encode(value: CanvasObject?) = value?.let {
                AiReviewJson.encodeToString(CanvasObject.serializer(), it.withVersion(1))
            }
            addDifference("node:${id.value}", encode(before.objects[id]), encode(after.objects[id]))
        }
        (before.relations.keys + after.relations.keys).sortedBy { it.value }.forEach { id ->
            fun encode(value: Relation?) = value?.let { AiReviewJson.encodeToString(it.withVersion(1)) }
            addDifference("relation:${id.value}", encode(before.relations[id]), encode(after.relations[id]))
        }
        addDifference("document:title", AiReviewJson.encodeToString(before.title), AiReviewJson.encodeToString(after.title))
        addDifference("document:canvasStyle", AiReviewJson.encodeToString(before.canvasStyle), AiReviewJson.encodeToString(after.canvasStyle))
        addDifference("document:sequenceDiagrams", AiReviewJson.encodeToString(before.sequenceDiagrams.entries.sortedBy { it.key.value }.associate { it.toPair() }),
            AiReviewJson.encodeToString(after.sequenceDiagrams.entries.sortedBy { it.key.value }.associate { it.toPair() }))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiProposalChangeReview(before: Workspace, after: Workspace, expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit) {
    val colors = BoarderLessTheme.colors
    val changes = remember(before, after) { aiDraftChanges(before, after) }
    val clipboard = LocalClipboardManager.current
    var page by remember(changes) { mutableStateOf(0) }
    val pages = (changes.size + 19) / 20
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(Strings.aiTools.changeCounts(changes.count { it.kind == AiDraftChangeKind.Added }.toString(),
            changes.count { it.kind == AiDraftChangeKind.Changed }.toString(), changes.count { it.kind == AiDraftChangeKind.Removed }.toString()),
            style = TextStyle(color = colors.contentText, fontSize = 13.sp))
        ShellButton(if (expanded) Strings.aiTools.hideChanges() else Strings.aiTools.showChanges(), compact = true,
            onClick = { onExpandedChange(!expanded) })
        if (expanded) {
            BasicText(Strings.aiTools.changeReviewHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            changes.drop(page * 20).take(20).forEach { change ->
                key(change.id) {
                    var detail by remember { mutableStateOf(false) }
                    val label = when (change.kind) {
                        AiDraftChangeKind.Added -> Strings.aiTools.changeAdded()
                        AiDraftChangeKind.Changed -> Strings.aiTools.changeUpdated()
                        AiDraftChangeKind.Removed -> Strings.aiTools.changeRemoved()
                    }
                    ShellButton("$label · ${change.id}", compact = true, accent = detail, onClick = { detail = !detail })
                    if (detail) SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            fun bounded(value: String?): String {
                                if (value == null) return "∅"
                                val cut = minOf(4096, value.length)
                                val safeCut = if (cut < value.length && cut > 0 && value[cut - 1].code in 0xD800..0xDBFF) cut - 1 else cut
                                return value.substring(0, safeCut)
                            }
                            BasicText(Strings.aiTools.changeBefore(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            BasicText(bounded(change.before), style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                            BasicText(Strings.aiTools.changeAfter(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            BasicText(bounded(change.after), style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                            if ((change.before?.length ?: 0) > 4096 || (change.after?.length ?: 0) > 4096)
                                BasicText(Strings.aiTools.changeTruncated(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(Strings.aiTools.copyChangeBefore(), compact = true, enabled = change.before != null,
                                    onClick = { clipboard.setText(AnnotatedString(requireNotNull(change.before))) })
                                ShellButton(Strings.aiTools.copyChangeAfter(), compact = true, enabled = change.after != null,
                                    onClick = { clipboard.setText(AnnotatedString(requireNotNull(change.after))) })
                            }
                        }
                    }
                }
            }
            if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(Strings.localSync.materializationPages((page + 1).toString(), pages.toString()),
                    style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                ShellButton(Strings.localSync.previousMaterializationPage(), compact = true, enabled = page > 0, onClick = { page-- })
                ShellButton(Strings.localSync.nextMaterializationPage(), compact = true, enabled = page + 1 < pages, onClick = { page++ })
            }
        }
    }
}
