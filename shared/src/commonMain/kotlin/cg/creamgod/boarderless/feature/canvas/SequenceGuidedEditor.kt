package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.i18n.Strings

/** Isolated semantic editor: no Workspace/HTTP/history mutation. Imported nested blocks remain
 * intact; explicit actions modify one selected branch, never infer order from geometry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SequenceGuidedEditor(initial: SequenceDiagramDraft?, onReview: (SequenceDiagramDraft) -> Unit,
    onCancel: () -> Unit) {
    val colors = BoarderLessTheme.colors
    var participants by remember { mutableStateOf(initial?.participants ?: emptyList()) }
    var steps by remember { mutableStateOf(initial?.steps ?: emptyList()) }
    var label by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf(false) }
    var editedParticipant by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableStateOf(initial?.participants?.firstOrNull()?.id) }
    var to by remember { mutableStateOf(initial?.participants?.lastOrNull()?.id) }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SequenceMessageKind.Call) }
    var dashed by remember { mutableStateOf(false) }
    var editedMessage by remember { mutableStateOf<String?>(null) }
    var insertBeforeStep by remember { mutableStateOf<String?>(null) }
    var branchLabel by remember { mutableStateOf("") }
    var otherLabel by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf(0) }
    var location by remember { mutableStateOf(SequenceStepLocation()) }
    var locationLabel by remember { mutableStateOf("") }

    fun usedStepIds(items: List<SequenceStep>): Set<String> = buildSet {
        items.forEach { step -> add(step.id); if (step is SequenceBlock) step.branches.forEach { addAll(usedStepIds(it.steps)) } }
    }
    fun nextId(prefix: String): String {
        val used = participants.map { it.id }.toSet() + usedStepIds(steps)
        var id: String
        do { id = "${prefix}_${++counter}" } while (id in used)
        return id
    }
    fun publish(nextParticipants: List<SequenceParticipant>, nextSteps: List<SequenceStep>): Boolean {
        return try {
            if (nextSteps.isNotEmpty()) SequenceDiagramDraft(nextParticipants, nextSteps).validatedSnapshot()
            require(nextParticipants.size <= 32 && nextParticipants.map { it.id }.distinct().size == nextParticipants.size)
            participants = nextParticipants; steps = nextSteps; problem = false; true
        } catch (_: IllegalArgumentException) { problem = true; false }
    }
    fun currentSteps(): List<SequenceStep> = if (location.blockId == null) steps
        else SequenceDiagramDraft(participants, steps).stepsAt(location)
    fun editCurrent(edit: (List<SequenceStep>) -> List<SequenceStep>): Boolean = try {
        val next = if (steps.isEmpty()) edit(emptyList()) else SequenceDiagramDraft(participants, steps).editSteps(location, edit).steps
        publish(participants, next)
    } catch (_: IllegalArgumentException) { problem = true; false }
    fun chooseBranch(next: SequenceStepLocation, name: String) {
        location = next; locationLabel = name; editedMessage = null; insertBeforeStep = null; text = ""; problem = false
    }
    val visibleSteps = currentSteps()
    @Composable fun Field(value: String, change: (String) -> Unit) {
        BasicTextField(value, onValueChange = { if (it.length <= 4096) change(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), textStyle = TextStyle(color = colors.contentText),
            cursorBrush = SolidColor(colors.contentText))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(Strings.sequence.guidedHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        BasicText(Strings.sequence.guidedParticipants(), style = TextStyle(color = colors.contentText))
        participants.forEachIndexed { index, participant ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShellButton("${participant.id} · ${participant.label}", compact = true, accent = editedParticipant == participant.id, onClick = {
                    editedParticipant = participant.id; label = participant.label; actor = participant.role == SequenceParticipantRole.Actor
                })
                ShellButton("←", compact = true, enabled = index > 0, onClick = {
                    val reordered = participants.toMutableList(); reordered.add(index - 1, reordered.removeAt(index)); publish(reordered, steps)
                })
                ShellButton("→", compact = true, enabled = index < participants.lastIndex, onClick = {
                    val reordered = participants.toMutableList(); reordered.add(index + 1, reordered.removeAt(index)); publish(reordered, steps)
                })
                ShellButton(Strings.toolbar.delete(), compact = true, onClick = {
                    if (publish(participants.filterNot { it.id == participant.id }, steps)) {
                        if (from == participant.id) from = participants.firstOrNull()?.id
                        if (to == participant.id) to = participants.lastOrNull()?.id
                        if (editedParticipant == participant.id) { editedParticipant = null; label = "" }
                    }
                })
            }
        }
        Field(label) { label = it; problem = false }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellButton(Strings.sequence.guidedActor(), compact = true, accent = actor, onClick = { actor = !actor })
            ShellButton(Strings.sequence.guidedSaveParticipant(), enabled = label.isNotBlank(), onClick = {
                try {
                    val id = editedParticipant ?: nextId("participant")
                    val value = SequenceParticipant(id, label, if (actor) SequenceParticipantRole.Actor else SequenceParticipantRole.Participant)
                    val next = if (editedParticipant == null) participants + value else participants.map { if (it.id == id) value else it }
                    if (publish(next, steps)) {
                        from = from ?: id; to = to ?: id; editedParticipant = null; label = ""
                    }
                } catch (_: IllegalArgumentException) { problem = true }
            })
            if (editedParticipant != null) ShellButton(Strings.sequence.guidedNew(), compact = true, onClick = { editedParticipant = null; label = "" })
        }
        BasicText(Strings.sequence.guidedMessages(), style = TextStyle(color = colors.contentText))
        BasicText(Strings.sequence.guidedBranchHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        ShellButton(Strings.sequence.guidedRoot(), compact = true, accent = location.blockId == null,
            onClick = { chooseBranch(SequenceStepLocation(), "") })
        @Composable fun Branches(items: List<SequenceStep>, depth: Int) {
            items.filterIsInstance<SequenceBlock>().forEach { block ->
                block.branches.forEachIndexed { index, branch ->
                    val target = SequenceStepLocation(block.id, index)
                    ShellButton("${"· ".repeat(depth)}${block.id} / ${index + 1}: ${branch.label}", compact = true,
                        accent = location == target, onClick = { chooseBranch(target, branch.label) })
                    Branches(branch.steps, depth + 1)
                }
            }
        }
        Branches(steps, 0)
        if (location.blockId != null) {
            Field(locationLabel) { locationLabel = it }
            ShellButton(Strings.sequence.guidedRenameBranch(), enabled = locationLabel.isNotBlank(), onClick = {
                try { publish(participants, SequenceDiagramDraft(participants, steps).renameBranch(location, locationLabel).steps) }
                catch (_: IllegalArgumentException) { problem = true }
            })
        }
        BasicText(Strings.sequence.guidedFrom(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            participants.forEach { participant -> ShellButton(participant.label, compact = true, accent = from == participant.id, onClick = { from = participant.id }) }
        }
        BasicText(Strings.sequence.guidedTo(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            participants.forEach { participant -> ShellButton(participant.label, compact = true, accent = to == participant.id, onClick = { to = participant.id }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SequenceMessageKind.entries.forEach { value ->
                val title = when (value) {
                    SequenceMessageKind.Call -> Strings.sequence.guidedCall()
                    SequenceMessageKind.Return -> Strings.sequence.guidedReturn()
                    SequenceMessageKind.Async -> Strings.sequence.guidedAsync()
                    SequenceMessageKind.Signal -> Strings.sequence.guidedSignal()
                }
                ShellButton(title, compact = true, accent = kind == value, onClick = { kind = value; dashed = value == SequenceMessageKind.Return })
            }
            ShellButton(Strings.sequence.guidedDashed(), compact = true, accent = dashed,
                enabled = kind == SequenceMessageKind.Async || kind == SequenceMessageKind.Signal, onClick = { dashed = !dashed })
        }
        Field(text) { text = it; problem = false }
        ShellButton(Strings.sequence.guidedAppend(), compact = true, accent = editedMessage == null && insertBeforeStep == null,
            onClick = { editedMessage = null; insertBeforeStep = null; problem = false })
        insertBeforeStep?.let { target ->
            BasicText(Strings.sequence.guidedInsertionTarget(target), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        }
        ShellButton(Strings.sequence.guidedSaveMessage(), enabled = from != null && to != null && text.isNotBlank(), onClick = {
            try {
                val value = SequenceMessage(editedMessage ?: nextId("message"), requireNotNull(from), requireNotNull(to), text,
                    kind, dashed = dashed)
                if (editCurrent { items ->
                    if (editedMessage == null) {
                        val index = insertBeforeStep?.let { target ->
                            items.indexOfFirst { it.id == target }.also { require(it >= 0) { "Insertion target changed" } }
                        } ?: items.size
                        items.toMutableList().also { it.add(index, value) }
                    } else {
                        require(items.any { it.id == editedMessage })
                        items.map { if (it.id == editedMessage) value else it }
                    }
                }) {
                    editedMessage = null; insertBeforeStep = null; text = ""
                }
            } catch (_: IllegalArgumentException) { problem = true }
        })
        visibleSteps.forEachIndexed { index, step ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val title = when (step) {
                    is SequenceMessage -> "${index + 1}. ${step.sourceId} → ${step.targetId}: ${step.text}"
                    is SequenceBlock -> "${index + 1}. ${step.kind.name} · ${step.branches.joinToString { it.label }}"
                }
                ShellButton(title, compact = true, enabled = step is SequenceMessage, onClick = {
                    val message = step as? SequenceMessage ?: return@ShellButton
                    editedMessage = message.id; insertBeforeStep = null; from = message.sourceId; to = message.targetId; text = message.text; kind = message.kind; dashed = message.dashed
                })
                ShellButton(Strings.sequence.guidedInsertBefore(), compact = true, accent = editedMessage == null && insertBeforeStep == step.id,
                    onClick = { editedMessage = null; insertBeforeStep = step.id; problem = false })
                ShellButton("↑", compact = true, enabled = index > 0, onClick = {
                    editCurrent { items -> items.toMutableList().also { it.add(index - 1, it.removeAt(index)) } }
                })
                ShellButton("↓", compact = true, enabled = index < visibleSteps.lastIndex, onClick = {
                    editCurrent { items -> items.toMutableList().also { it.add(index + 1, it.removeAt(index)) } }
                })
                ShellButton(Strings.toolbar.delete(), compact = true, enabled = visibleSteps.size > 1, onClick = {
                    if (editCurrent { it.filterNot { item -> item.id == step.id } } && editedMessage == step.id) { editedMessage = null; text = "" }
                })
            }
        }
        BasicText(Strings.sequence.guidedBlocks(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        Field(branchLabel) { branchLabel = it }
        Field(otherLabel) { otherLabel = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SequenceBlockKind.entries.forEach { blockKind ->
                ShellButton(blockKind.name, compact = true,
                    enabled = branchLabel.isNotBlank() && visibleSteps.size >= (if (blockKind == SequenceBlockKind.Loop) 1 else 2) &&
                        (blockKind == SequenceBlockKind.Loop || otherLabel.isNotBlank()) && editedMessage == null,
                    onClick = {
                        try {
                            val count = if (blockKind == SequenceBlockKind.Loop) 1 else 2
                            val branches = listOf(SequenceBranch(branchLabel, listOf(visibleSteps[visibleSteps.size - count]))) +
                                if (count == 2) listOf(SequenceBranch(otherLabel, listOf(visibleSteps.last()))) else emptyList()
                            val value = SequenceBlock(nextId("block"), blockKind, branches)
                            if (editCurrent { it.dropLast(count) + value }) { branchLabel = ""; otherLabel = "" }
                        } catch (_: IllegalArgumentException) { problem = true }
                    })
            }
        }
        if (problem) BasicText(Strings.sequence.guidedInvalid(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
        ShellButton(Strings.sequence.validatePreview(), enabled = participants.isNotEmpty() && steps.isNotEmpty(), onClick = {
            try { onReview(SequenceDiagramDraft(participants, steps).validatedSnapshot()) }
            catch (_: IllegalArgumentException) { problem = true }
        })
        ShellButton(Strings.sequence.guidedCancel(), onClick = onCancel)
    }
}
