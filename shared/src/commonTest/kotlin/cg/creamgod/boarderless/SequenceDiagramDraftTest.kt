package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class SequenceDiagramDraftTest {
    private val fixture =
        """
        sequenceDiagram
        actor Client as 使用者
        participant Transport
        participant Stateful as 狀態服務
        Client->>Transport: request
        Transport->>Stateful: load
        Stateful-->>Transport: state
        alt state exists
            Transport->>Transport: prepare response
            Transport-->>Client: existing
        else state absent
            loop retry budget
                Transport-)Stateful: create
                Stateful-->>Transport: created
            end
            Transport-->>Client: new
        end
        par audit
            Transport--)Stateful: audit
        and metrics
            Transport->Stateful: metric
        end
        """.trimIndent()

    private fun parse(text: String = fixture) = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse(text)).draft

    private fun rejected(
        text: String,
        code: SequenceDiagnosticCode,
        line: Int? = null,
    ) {
        val d = assertIs<SequenceParseResult.Rejected>(MermaidSequenceAdapter.parse(text)).diagnostic
        assertEquals(code, d.code)
        line?.let { assertEquals(it, d.line) }
    }

    @Test fun transportStatefulReturnAndTwoBranchesAreExplicitAndSerializeWithIdentities() {
        val d = parse()
        assertEquals(listOf("Client", "Transport", "Stateful"), d.participants.map { it.id })
        assertEquals(SequenceParticipantRole.Actor, d.participants.first().role)
        assertEquals(10, d.messages().size)
        assertEquals(SequenceMessageKind.Return, d.messages()[2].kind)
        val alt = assertIs<SequenceBlock>(d.steps[3])
        assertEquals(SequenceBlockKind.Alt, alt.kind)
        assertEquals(listOf("state exists", "state absent"), alt.branches.map { it.label })
        assertTrue(assertIs<SequenceMessage>(alt.branches[0].steps[0]).isSelfMessage)
        val loop = assertIs<SequenceBlock>(alt.branches[1].steps[0])
        assertEquals(SequenceBlockKind.Loop, loop.kind)
        assertEquals(SequenceBlockKind.Parallel, assertIs<SequenceBlock>(d.steps.last()).kind)
        assertEquals(d, Json.decodeFromString<SequenceDiagramDraft>(Json.encodeToString(d)))
        assertEquals(d, parse(MermaidSequenceAdapter.export(d)))
    }

    @Test fun repeatedEndpointsHaveDistinctStepIdentitiesAndImplicitOrderIsPreserved() {
        val d = parse("sequenceDiagram\nB->>A: one\nA-->>B: two\nB->>A: three\nB->>B: self")
        assertEquals(listOf("B", "A"), d.participants.map { it.id })
        assertEquals(
            4,
            d
                .messages()
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(d, parse(MermaidSequenceAdapter.export(d)))
        rejected("sequenceDiagram\nB->>A: one\nparticipant C", SequenceDiagnosticCode.LateParticipant, 3)
    }

    @Test fun autonumberPrefixesMessagesInSourceOrderAndMustPrecedeBody() {
        val parsed =
            MermaidSequenceAdapter.parse("sequenceDiagram\nautonumber\nA->>B: go\nalt ok\nB-->>A: back\nelse no\nB-->>A: fail\nend")
        val texts = (parsed as SequenceParseResult.Parsed).draft.messages().map { it.text }
        assertEquals(listOf("1. go", "2. back", "3. fail"), texts)
        rejected("sequenceDiagram\nA->>B: go\nautonumber", SequenceDiagnosticCode.UnsupportedStatement, 3)
        rejected("sequenceDiagram\nautonumber\nautonumber", SequenceDiagnosticCode.UnsupportedStatement, 3)
    }

    @Test fun rejectsUnknownSyntaxConfigLinksMarkupAndTruncatedBlocksWithoutPartialDrafts() {
        for (statement in listOf(
            "activate A",
            "Note over A: note",
            "click A href https://example.com",
            "%%{init: {}}%%",
            "rect red",
            "create participant A",
        )) {
            rejected(
                "sequenceDiagram\n$statement",
                if (statement.contains(':') &&
                    !statement.startsWith("%%{")
                ) {
                    SequenceDiagnosticCode.InvalidMessage
                } else {
                    SequenceDiagnosticCode.UnsupportedStatement
                },
                2,
            )
        }
        rejected("sequenceDiagram\nA->>B: <script>", SequenceDiagnosticCode.InvalidLabel, 2)
        rejected("sequenceDiagram\nA->>B: raw;statement", SequenceDiagnosticCode.InvalidLabel, 2)
        rejected("sequenceDiagram\nA->>B: text %% comment", SequenceDiagnosticCode.InvalidLabel, 2)
        rejected("sequenceDiagram\nloop retry\nA->>B: go", SequenceDiagnosticCode.UnclosedBlock, 2)
        rejected("sequenceDiagram\nend", SequenceDiagnosticCode.UnexpectedEnd, 2)
        rejected("sequenceDiagram\nelse other", SequenceDiagnosticCode.UnexpectedBranch, 2)
        rejected("sequenceDiagram\nalt first\nA->>B: go\nand second\nB->>A: back\nend", SequenceDiagnosticCode.UnexpectedBranch, 4)
        rejected("sequenceDiagram\nloop retry\nend", SequenceDiagnosticCode.EmptyBranch, 3)
        rejected("sequenceDiagram\nalt first\nA->>B: go\nend", SequenceDiagnosticCode.InvalidBlock, 2)
    }

    @Test fun rejectsMalformedUnicodeIdsDuplicatesUnknownWireAndResourceLimits() {
        // Kotlin/JS can replace an isolated surrogate string literal while emitting UTF-8 JS.
        // Construct the actual invalid UTF-16 code unit at runtime on every target.
        val dangling = CharArray(1) { 0xD800.toChar() }.concatToString()
        rejected("sequenceDiagram\nA->>B: " + dangling, SequenceDiagnosticCode.InvalidUnicode)
        rejected("sequenceDiagram\nparticipant A\nparticipant A", SequenceDiagnosticCode.DuplicateParticipant, 3)
        rejected("sequenceDiagram\nA<<->>B: both", SequenceDiagnosticCode.InvalidMessage, 2)
        rejected("sequenceDiagram\nA->>+B: activated", SequenceDiagnosticCode.InvalidMessage, 2)
        rejected("sequenceDiagram\n" + "loop x\n".repeat(17) + "A->>B: x\n" + "end\n".repeat(17), SequenceDiagnosticCode.NestingLimit, 18)
        rejected("sequenceDiagram\n" + (1..33).joinToString("\n") { "participant P$it" }, SequenceDiagnosticCode.CapacityLimit, 34)
        rejected("sequenceDiagram\n" + "A->>B: x\n".repeat(513), SequenceDiagnosticCode.CapacityLimit, 514)
        rejected("x".repeat(65537), SequenceDiagnosticCode.TooLarge)
        rejected("flowchart LR\nA-->B", SequenceDiagnosticCode.MissingHeader, 1)
        assertFails { SequenceParticipant("end", "x") }
        assertFails { SequenceMessage("s", "A", "B", "x", SequenceMessageKind.Call, true) }
    }

    @Test fun modelRejectsDanglingEndpointsDuplicateIdsAndUnknownSchemasRatherThanInferring() {
        val d = parse()
        assertFails { d.copy(participants = d.participants.dropLast(1)) }
        assertFails { d.copy(steps = d.steps + d.steps.first()) }
        assertFails { d.copy(schema = "future") }
        assertFails {
            SequenceBlock(
                "block",
                SequenceBlockKind.Loop,
                listOf(SequenceBranch("x", listOf(d.steps.first())), SequenceBranch("y", listOf(d.steps.first()))),
            )
        }
        assertFails { SequenceBranch("x", emptyList()) }
        val json = Json.encodeToString(d)
        assertFails { Json.decodeFromString<SequenceDiagramDraft>(json.replace("\"kind\":\"Return\"", "\"kind\":\"future\"")) }
        assertFails { Json.decodeFromString<SequenceDiagramDraft>(json.dropLast(1) + ",\"unknown\":true}") }
    }

    @Test fun editingInNamedBranchAndReorderingRetainIdentityAndNeverGuessChronology() {
        val d = parse()
        val alt = assertIs<SequenceBlock>(d.steps[3])
        val location = SequenceStepLocation(alt.id, 0)
        val extra = SequenceMessage("added", "Transport", "Stateful", "validate")
        val edited = d.insertMessage(location, 1, extra).moveStep(location, "added", 0)
        assertEquals(extra, assertIs<SequenceBlock>(edited.steps[3]).branches[0].steps.first())
        assertEquals(d.messages().map { it.id }.toSet() + "added", edited.messages().map { it.id }.toSet())
        assertEquals(d.steps[0], edited.steps[0])
        assertEquals(d.steps[4], edited.steps[4])
        assertEquals(
            listOf(
                "Stateful",
                "Transport",
                "Client",
            ),
            edited.reorderParticipants(listOf("Stateful", "Transport", "Client")).participants.map {
                it.id
            },
        )
        assertFails { d.reorderParticipants(listOf("Client", "Client", "Stateful")) }
        assertFails { d.insertMessage(SequenceStepLocation("missing", 0), 0, extra) }
        assertFails { d.insertMessage(location, 0, d.messages().first()) }
        assertFails { d.editSteps(location) { emptyList() } }
    }

    @Test fun layoutKeepsLifelinesSeparateMessagesOrderedSelfPortsNonzeroAndBlocksContained() {
        val d = parse()
        val layout = sequenceDiagramLayout(d)
        assertEquals(d.participants, layout.participants.map { it.participant })
        assertTrue(layout.participants.zipWithNext().all { (a, b) -> a.lifelineStart.x < b.lifelineStart.x })
        assertEquals(d.messages(), layout.messages.map { it.message })
        assertTrue(layout.messages.zipWithNext().all { (a, b) -> a.points.first().y < b.points.first().y })
        val self = layout.messages.single { it.message.isSelfMessage }
        assertEquals(4, self.points.size)
        assertNotEquals(self.points.first(), self.points.last())
        assertTrue(self.points.zipWithNext().all { (a, b) -> a != b })
        val alt = assertIs<SequenceBlock>(d.steps[3])
        val bounds = layout.blocks.single { it.blockId == alt.id }
        assertEquals(2, bounds.branches.size)
        for (message in layout.messages.filter { it.containingBranches.any { pair -> pair.first == alt.id } }) {
            assertTrue(message.points.first().y > bounds.top && message.points.last().y < bounds.bottom)
        }
        assertEquals(1, layout.blocks.single { it.kind == SequenceBlockKind.Loop }.depth)
        assertTrue(layout.messages.flatMap { it.points }.all { it.x in 0f..layout.size.width && it.y in 0f..layout.size.height })
        // Participant reorder changes columns, never message IDs, branch identity or row chronology.
        val swapped = sequenceDiagramLayout(d.reorderParticipants(listOf("Stateful", "Client", "Transport")))
        assertEquals(
            layout.messages.map { it.message.id to it.points.first().y },
            swapped.messages.map {
                it.message.id to
                    it.points.first().y
            },
        )
    }

    @Test fun frozenSnapshotIsolatedFromCallerListsAndCorruptionCannotReachExporter() {
        val messages = mutableListOf<SequenceStep>(SequenceMessage("s", "A", "B", "go"))
        val participants = mutableListOf(SequenceParticipant("A", "A"), SequenceParticipant("B", "B"))
        val d = SequenceDiagramDraft(participants, messages)
        val frozen = d.validatedSnapshot()
        participants.clear()
        messages.clear()
        assertEquals(2, frozen.participants.size)
        assertEquals(1, frozen.messages().size)
        assertFails { MermaidSequenceAdapter.export(d) }
        assertFails { sequenceDiagramLayout(d) }
    }
}
