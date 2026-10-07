package cg.creamgod.boarderless.domain.sequence

/** Versioned plain-text Mermaid subset; no JS evaluation, config, links or best-effort truncation.
 * This is semantic extraction for this dialect, not Mermaid's full parse API. */
enum class SequenceDiagnosticCode {
    Empty,
    TooLarge,
    InvalidUnicode,
    MissingHeader,
    UnsupportedStatement,
    DuplicateParticipant,
    LateParticipant,
    InvalidLabel,
    InvalidMessage,
    MissingEndpoint,
    UnexpectedBranch,
    UnexpectedEnd,
    UnclosedBlock,
    EmptyBranch,
    InvalidBlock,
    NestingLimit,
    CapacityLimit,
}

data class SequenceDiagnostic(
    val line: Int,
    val code: SequenceDiagnosticCode,
)

sealed interface SequenceParseResult {
    data class Parsed(
        val draft: SequenceDiagramDraft,
    ) : SequenceParseResult

    data class Rejected(
        val diagnostic: SequenceDiagnostic,
    ) : SequenceParseResult
}

object MermaidSequenceAdapter {
    const val dialect = "boarderless.mermaid-sequence.v1"
    private val participant = Regex("^(participant|actor)\\s+([A-Za-z_][A-Za-z0-9_]{0,63})(?:\\s+as\\s+(.+))?$")
    private val message =
        Regex("^([A-Za-z_][A-Za-z0-9_]{0,63})\\s*(-->>|->>|-->|->|--\\)|-\\))\\s*([A-Za-z_][A-Za-z0-9_]{0,63})\\s*:\\s*(.+)$")

    private class Failure(
        val line: Int,
        val code: SequenceDiagnosticCode,
    ) : Exception()

    private class Frame(
        val id: String,
        val kind: SequenceBlockKind,
        val line: Int,
        val parent: MutableList<SequenceStep>,
    ) {
        val branches = mutableListOf<SequenceBranch>()
        var label = ""
        var current = mutableListOf<SequenceStep>()
    }

    fun parse(source: String): SequenceParseResult {
        if (source.isBlank()) return SequenceParseResult.Rejected(SequenceDiagnostic(1, SequenceDiagnosticCode.Empty))
        if (source.length > 65536 || source.encodeToByteArray().size > 65536) {
            return SequenceParseResult.Rejected(SequenceDiagnostic(1, SequenceDiagnosticCode.TooLarge))
        }
        // UTF-16 validity is checked before replacement encoding can erase malformed input.
        var p = 0
        while (p < source.length) {
            val c = source[p++]
            if (c.code in 0xD800..0xDBFF) {
                if (p >= source.length || source[p++].code !in 0xDC00..0xDFFF) {
                    return SequenceParseResult.Rejected(SequenceDiagnostic(1, SequenceDiagnosticCode.InvalidUnicode))
                }
            } else if (c.code in 0xDC00..0xDFFF) {
                return SequenceParseResult.Rejected(SequenceDiagnostic(1, SequenceDiagnosticCode.InvalidUnicode))
            }
        }
        return try {
            SequenceParseResult.Parsed(extract(source))
        } catch (
            f: Failure,
        ) {
            SequenceParseResult.Rejected(SequenceDiagnostic(f.line, f.code))
        }
    }

    private fun extract(source: String): SequenceDiagramDraft {
        val lines = source.replace("\r\n", "\n").split('\n')
        if (lines.size > 2048) throw Failure(1, SequenceDiagnosticCode.TooLarge)
        val participants = linkedMapOf<String, SequenceParticipant>()
        val root = mutableListOf<SequenceStep>()
        val stack = mutableListOf<Frame>()
        var header = false
        var body = false
        var autonumber = false
        var messages = 0
        var blocks = 0
        var serial = 0

        fun nextId() = "step_${++serial}"

        fun fail(
            line: Int,
            code: SequenceDiagnosticCode,
        ): Nothing = throw Failure(line, code)

        fun text(
            value: String,
            line: Int,
        ): String {
            try {
                requireSequenceText(value)
            } catch (_: Exception) {
                fail(line, SequenceDiagnosticCode.InvalidLabel)
            }
            return value
        }

        fun identity(
            value: String,
            line: Int,
        ) {
            try {
                requireSequenceId(value)
            } catch (_: Exception) {
                fail(line, SequenceDiagnosticCode.InvalidMessage)
            }
        }

        fun current() = stack.lastOrNull()?.current ?: root

        fun finishBranch(
            frame: Frame,
            line: Int,
        ) {
            if (frame.current.isEmpty()) fail(line, SequenceDiagnosticCode.EmptyBranch)
            frame.branches.add(SequenceBranch(frame.label, frame.current.toList()))
            frame.current = mutableListOf()
        }
        for ((index, raw) in lines.withIndex()) {
            val line = index + 1
            val s = raw.trim()
            if (s.isEmpty()) continue
            if (s.startsWith("%%{")) fail(line, SequenceDiagnosticCode.UnsupportedStatement)
            if (s.startsWith("%%")) continue
            if (!header) {
                if (s != "sequenceDiagram") fail(line, SequenceDiagnosticCode.MissingHeader)
                header = true
                continue
            }
            if (s == "autonumber") {
                // The draft model has no numbering flag, so numbers are baked into message text in source order.
                if (body || autonumber) fail(line, SequenceDiagnosticCode.UnsupportedStatement)
                autonumber = true
                continue
            }
            val declaration = participant.matchEntire(s)
            if (declaration != null) {
                if (body) fail(line, SequenceDiagnosticCode.LateParticipant)
                val id = declaration.groupValues[2]
                identity(id, line)
                if (id in participants) fail(line, SequenceDiagnosticCode.DuplicateParticipant)
                if (participants.size >= 32) fail(line, SequenceDiagnosticCode.CapacityLimit)
                val label = text(declaration.groupValues[3].ifBlank { id }, line)
                participants[id] =
                    SequenceParticipant(
                        id,
                        label,
                        if (declaration.groupValues[1] ==
                            "actor"
                        ) {
                            SequenceParticipantRole.Actor
                        } else {
                            SequenceParticipantRole.Participant
                        },
                    )
                continue
            }
            body = true
            if (s == "end") {
                val frame = stack.lastOrNull() ?: fail(line, SequenceDiagnosticCode.UnexpectedEnd)
                finishBranch(frame, line)
                val block =
                    try {
                        SequenceBlock(frame.id, frame.kind, frame.branches.toList())
                    } catch (
                        _: Exception,
                    ) {
                        fail(frame.line, SequenceDiagnosticCode.InvalidBlock)
                    }
                stack.removeAt(stack.lastIndex)
                frame.parent.add(block)
                continue
            }
            val keyword = s.substringBefore(' ')
            if (keyword in setOf("else", "and")) {
                val frame = stack.lastOrNull() ?: fail(line, SequenceDiagnosticCode.UnexpectedBranch)
                if ((keyword == "else" && frame.kind != SequenceBlockKind.Alt) ||
                    (keyword == "and" && frame.kind != SequenceBlockKind.Parallel)
                ) {
                    fail(line, SequenceDiagnosticCode.UnexpectedBranch)
                }
                if (frame.branches.size >= 15) fail(line, SequenceDiagnosticCode.CapacityLimit)
                finishBranch(frame, line)
                frame.label = text(s.substringAfter(' ', "").trim(), line)
                continue
            }
            if (keyword in setOf("alt", "loop", "par")) {
                if (stack.size >= 16) fail(line, SequenceDiagnosticCode.NestingLimit)
                if (++blocks > 128) fail(line, SequenceDiagnosticCode.CapacityLimit)
                val kind =
                    when (keyword) {
                        "alt" -> SequenceBlockKind.Alt
                        "loop" -> SequenceBlockKind.Loop
                        else -> SequenceBlockKind.Parallel
                    }
                val frame = Frame(nextId(), kind, line, current())
                frame.label = text(s.substringAfter(' ', "").trim(), line)
                stack.add(frame)
                continue
            }
            val m =
                message.matchEntire(s)
                    ?: fail(
                        line,
                        if (s.contains(':')) SequenceDiagnosticCode.InvalidMessage else SequenceDiagnosticCode.UnsupportedStatement,
                    )
            if (++messages > 512) fail(line, SequenceDiagnosticCode.CapacityLimit)
            val from = m.groupValues[1]
            val to = m.groupValues[3]
            identity(from, line)
            identity(to, line)
            // Mermaid permits implicit participants. Their first appearance is explicit list order.
            for (id in listOf(from, to)) {
                if (id !in participants) {
                    if (participants.size >= 32) fail(line, SequenceDiagnosticCode.CapacityLimit)
                    participants[id] = SequenceParticipant(id, id)
                }
            }
            val arrow = m.groupValues[2]
            val kind =
                when (arrow) {
                    "->>" -> SequenceMessageKind.Call
                    "-->>" -> SequenceMessageKind.Return
                    "-)", "--)" -> SequenceMessageKind.Async
                    else -> SequenceMessageKind.Signal
                }
            val label = text(if (autonumber) "$messages. ${m.groupValues[4].trim()}" else m.groupValues[4].trim(), line)
            current().add(SequenceMessage(nextId(), from, to, label, kind, arrow.startsWith("--")))
        }
        if (!header) fail(1, SequenceDiagnosticCode.MissingHeader)
        stack.lastOrNull()?.let { fail(it.line, SequenceDiagnosticCode.UnclosedBlock) }
        if (root.isEmpty()) fail(lines.size, SequenceDiagnosticCode.Empty)
        return try {
            SequenceDiagramDraft(participants.values.toList(), root.toList())
        } catch (
            _: Exception,
        ) {
            fail(lines.size, SequenceDiagnosticCode.CapacityLimit)
        }
    }

    /** Exports the supported semantic tree, never source snippets or renderer directives.
     * Logical IDs are retained by serialized draft; Mermaid itself has no message identity syntax. */
    fun export(source: SequenceDiagramDraft): String {
        val draft = source.validatedSnapshot()
        return buildString {
            appendLine("sequenceDiagram")
            draft.participants.forEach {
                appendLine("    ${if (it.role == SequenceParticipantRole.Actor) "actor" else "participant"} ${it.id} as ${it.label}")
            }

            fun emit(
                steps: List<SequenceStep>,
                depth: Int,
            ) {
                val prefix = "    ".repeat(depth)
                for (step in steps) {
                    when (step) {
                        is SequenceMessage -> {
                            val arrow =
                                when (step.kind) {
                                    SequenceMessageKind.Call -> "->>"
                                    SequenceMessageKind.Return -> "-->>"
                                    SequenceMessageKind.Async -> if (step.dashed) "--)" else "-)"
                                    SequenceMessageKind.Signal -> if (step.dashed) "-->" else "->"
                                }
                            appendLine("$prefix${step.sourceId}$arrow${step.targetId}: ${step.text}")
                        }

                        is SequenceBlock -> {
                            val opening =
                                when (step.kind) {
                                    SequenceBlockKind.Alt -> "alt"
                                    SequenceBlockKind.Loop -> "loop"
                                    SequenceBlockKind.Parallel -> "par"
                                }
                            step.branches.forEachIndexed { index, branch ->
                                appendLine(
                                    "$prefix${if (index == 0) {
                                        opening
                                    } else if (step.kind == SequenceBlockKind.Alt) {
                                        "else"
                                    } else {
                                        "and"
                                    }} ${branch.label}",
                                )
                                emit(branch.steps, depth + 1)
                            }
                            appendLine("${prefix}end")
                        }
                    }
                }
            }
            emit(draft.steps, 1)
        }
    }
}
