package cg.creamgod.boarderless.domain.sequence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Explicit ordered semantics, independent of canvas positions and ordinary graph cycles.
 * IDs survive editing/serialization; canvas binding is a separate publication step. */
@Serializable enum class SequenceParticipantRole { Participant, Actor }

@Serializable data class SequenceParticipant(
    val id: String,
    val label: String,
    val role: SequenceParticipantRole = SequenceParticipantRole.Participant,
) {
    init {
        requireSequenceId(id)
        requireSequenceText(label)
    }
}

@Serializable enum class SequenceMessageKind { Call, Return, Async, Signal }

@Serializable enum class SequenceBlockKind { Alt, Loop, Parallel }

@Serializable sealed interface SequenceStep {
    val id: String
}

@Serializable
@SerialName("message")
data class SequenceMessage(
    override val id: String,
    val sourceId: String,
    val targetId: String,
    val text: String,
    val kind: SequenceMessageKind = SequenceMessageKind.Call,
    val dashed: Boolean = false,
) : SequenceStep {
    init {
        requireSequenceId(id)
        requireSequenceId(sourceId)
        requireSequenceId(targetId)
        requireSequenceText(text)
        require((kind != SequenceMessageKind.Return || dashed) && (kind != SequenceMessageKind.Call || !dashed)) {
            "Replies require a dotted arrow"
        }
    }

    val isSelfMessage: Boolean get() = sourceId == targetId
}

@Serializable data class SequenceBranch(
    val label: String,
    val steps: List<SequenceStep>,
) {
    init {
        requireSequenceText(label)
        require(steps.isNotEmpty() && steps.size <= 512)
    }
}

@Serializable
@SerialName("block")
data class SequenceBlock(
    override val id: String,
    val kind: SequenceBlockKind,
    val branches: List<SequenceBranch>,
) : SequenceStep {
    init {
        requireSequenceId(id)
        require(
            when (kind) {
                SequenceBlockKind.Loop -> branches.size == 1
                SequenceBlockKind.Alt, SequenceBlockKind.Parallel -> branches.size in 2..16
            },
        ) { "Invalid block branches" }
    }
}

@Serializable data class SequenceDiagramDraft(
    val participants: List<SequenceParticipant>,
    val steps: List<SequenceStep>,
    val schema: String = "boarderless.sequence-draft.v1",
) {
    init {
        require(schema == "boarderless.sequence-draft.v1")
        require(participants.size in 1..32 && steps.size in 1..512)
        val participantIds = participants.map { it.id }.toSet()
        require(participantIds.size == participants.size)
        val stepIds = mutableSetOf<String>()
        var messages = 0
        var blocks = 0
        var textBytes = participants.sumOf { it.label.encodeToByteArray().size }

        fun check(
            items: List<SequenceStep>,
            depth: Int,
        ) {
            require(depth <= 16) { "Sequence nesting limit" }
            for (item in items) {
                require(stepIds.add(item.id)) { "Duplicate sequence step" }
                when (item) {
                    is SequenceMessage -> {
                        require(++messages <= 512)
                        require(item.sourceId in participantIds && item.targetId in participantIds) { "Missing participant" }
                        textBytes += item.text.encodeToByteArray().size
                    }

                    is SequenceBlock -> {
                        require(++blocks <= 128)
                        item.branches.forEach { branch ->
                            textBytes += branch.label.encodeToByteArray().size
                            check(branch.steps, depth + 1)
                        }
                    }
                }
                require(textBytes <= 262144) { "Sequence text limit" }
            }
        }
        check(steps, 0)
        require(messages > 0)
    }

    /** Preorder preserves branch containment. It does not assign execution order across par branches. */
    fun messages(): List<SequenceMessage> =
        buildList {
            fun visit(items: List<SequenceStep>) {
                items.forEach { item ->
                    when (item) {
                        is SequenceMessage -> add(item)
                        is SequenceBlock -> item.branches.forEach { visit(it.steps) }
                    }
                }
            }
            visit(steps)
        }
}

internal fun requireSequenceId(value: String) {
    require(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}"))) { "Invalid sequence identity" }
    require(value !in setOf("end", "else", "and", "alt", "loop", "par", "participant", "actor", "sequenceDiagram"))
}

internal fun requireSequenceText(value: String) {
    require(value.isNotBlank() && value.encodeToByteArray().size <= 4096)
    // Plain labels only: no Mermaid entities, markup, statements or escaped source lines.
    require(!value.contains("%%"))
    require(value.none { it < ' ' || it in "<>&;`\\" || it == '\u007f' })
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        when (c.code) {
            in 0xD800..0xDBFF -> require(i < value.length && value[i++].code in 0xDC00..0xDFFF)
            in 0xDC00..0xDFFF -> error("Invalid sequence Unicode")
        }
    }
}

/** Copy caller-owned collections only after bounded validation, including recursive/cyclic input. */
fun SequenceDiagramDraft.validatedSnapshot(): SequenceDiagramDraft {
    SequenceDiagramDraft(participants, steps, schema)

    fun copy(items: List<SequenceStep>): List<SequenceStep> =
        items.map { step ->
            when (step) {
                is SequenceMessage -> step.copy()
                is SequenceBlock -> step.copy(branches = step.branches.map { it.copy(steps = copy(it.steps)) })
            }
        }
    return SequenceDiagramDraft(participants.map { it.copy() }, copy(steps), schema)
}
