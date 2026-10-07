package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiSequenceSafetyTest {
    private fun workspace(): Workspace {
        val empty = Workspace(WorkspaceId("private_workspace"), "Board")
        val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, 0, 0, empty)
        val source = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse("""
            sequenceDiagram
            actor Client as 使用者
            participant Server as 私密服務
            Client->>Server: request
            alt ready
            Server-->>Client: response
            else missing
            loop retry
            Server->>Server: prepare
            end
            end
            par first
            Client-)Server: async
            and second
            Server->Client: signal
            end
        """.trimIndent())).draft
        var n = 0
        val op = SequenceCanvasCreation(owner, empty, Vec2.Zero).operation(source, "Sequence") { "private_object_${++n}" }
        val graph = assertIs<OperationResult.Applied>(op.applyTo(empty)).workspace
        val outside = TextNode(CanvasObjectId("outside"), transform = CanvasTransform(Vec2.Zero, CanvasSize(50f, 50f)), text = "Outside")
        return graph.copy(objects = graph.objects + (outside.id to outside))
    }

    private fun packet(w: Workspace, ids: Set<CanvasObjectId> = w.objects.keys) =
        aiCanvasPacket(w, ids, false, Viewport(), CanvasSize(800f, 600f))

    private fun answer(packet: AiCanvasPacket): JsonObject = buildJsonObject {
        val nodes = packet.flowchart.inventory.getValue("nodes").jsonArray
        put("nodeIds", JsonArray(nodes.map { it.jsonObject.getValue("id") }))
        put("links", packet.flowchart.inventory.getValue("relations"))
        put("parents", JsonArray(nodes.map { buildJsonObject {
            put("id", it.jsonObject.getValue("id"))
            put("parentId", it.jsonObject.getValue("parentId"))
        } }))
        put("summary", "時序結構")
        put("uncertainties", JsonArray(emptyList()))
        put("sequenceContexts", packet.sequenceContexts)
    }

    @Test fun explicitOrderBranchesRolesAndKindsUseOnlyScopedAliasesAndAreChecked() {
        val w = workspace()
        val p = packet(w)
        assertFalse("private_object" in p.json)
        assertFalse("private_workspace" in p.json)
        val context = p.sequenceContexts.single().jsonObject
        assertTrue(context.getValue("complete").jsonPrimitive.boolean)
        assertEquals("Actor", context.getValue("participants").jsonArray.first().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(listOf("Call", "Return", "Call", "Async", "Signal"), context.getValue("messages").jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content })
        assertEquals(listOf("Alt", "Loop", "Parallel"), context.getValue("blocks").jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content })
        assertTrue(checkAiCanvasAnswer(p, answer(p).toString()).isEmpty())
        assertEquals(listOf("sequenceContexts"), checkAiCanvasAnswer(p, JsonObject(answer(p) - "sequenceContexts").toString()))
        val messages = context.getValue("messages").jsonArray
        val wrong = JsonObject(context + ("messages" to JsonArray(messages.reversed())))
        assertEquals(listOf("sequenceContexts"), checkAiCanvasAnswer(p, JsonObject(answer(p) + ("sequenceContexts" to JsonArray(listOf(wrong)))).toString()))
        val reversed = w.copy(objects = w.objects.entries.reversed().associate { it.toPair() }, relations = w.relations.entries.reversed().associate { it.toPair() })
        assertEquals(p.json, packet(reversed).json)
    }

    @Test fun partialApprovalOmitsAllHiddenSequenceSemanticsAndOrdinaryScopeStaysOrdinary() {
        val w = workspace()
        val participant = w.sequenceDiagrams.values.single().participants.first().objectId
        val p = packet(w, setOf(participant))
        val context = p.sequenceContexts.single().jsonObject
        assertFalse(context.getValue("complete").jsonPrimitive.boolean)
        assertNull(context["messages"])
        assertNull(context["participants"])
        assertNull(context["blocks"])
        assertFalse("私密服務" in p.json)
        assertFalse("prepare" in p.json)
        assertTrue(checkAiCanvasAnswer(p, answer(p).toString()).isEmpty())
        val ordinary = packet(w, setOf(CanvasObjectId("outside")))
        assertTrue(ordinary.sequenceContexts.isEmpty())
        assertFalse("EXPLICIT_SEQUENCE_CONTEXTS" in ordinary.json)
    }

    @Test fun duplicateAnswerKeysAndMalformedUnicodeAreRejected() {
        val p = packet(workspace())
        val response = answer(p).toString()
        assertFailsWith<IllegalArgumentException> { checkAiCanvasAnswer(p, response.dropLast(1) + ",\"summary\":\"duplicate\"}") }
        assertFails { checkAiCanvasAnswer(p, response.replace("時序結構", "\\uD800")) }
    }

    @Test fun toolsRefuseSequenceMutationParentingAndRelationsButAllowOutsideEdits() {
        val w = workspace()
        val refs = w.objects.keys.sortedBy { it.value }.mapIndexed { n, id -> id to "N${n + 1}" }.toMap()
        val document = w.sequenceDiagrams.values.single()
        val root = refs.getValue(document.containerId)
        val participant = refs.getValue(document.participants.first().objectId)
        val outside = refs.getValue(CanvasObjectId("outside"))
        val engine = AiDiagramDraft("request", "session", w, w.objects.keys,
            AiDiagramCapabilities(setOf(NodeShape.Rectangle), setOf("paper"), setOf("group"), selfLoops = true))
        var n = 0
        fun call(name: String, args: String = "{}") = engine.execute(AiDiagramToolCall("request", "session", "c${++n}", name, args))
        assertFalse(call("list_diagram_capabilities").data.getValue("sequenceDiagrams").jsonPrimitive.boolean)
        val graph = call("read_approved_graph").data
        assertEquals(packet(w).sequenceContexts, graph.getValue("sequenceContexts"))
        val badCalls = listOf(
            "update_nodes" to """{"nodes":[{"id":"$participant","text":"changed"}]}""",
            "layout_nodes" to """{"nodes":[{"id":"$root","x":1,"y":2,"width":100,"height":200}]}""",
            "create_nodes" to """{"nodes":[{"id":"A","text":"new","x":1,"y":2,"width":100,"height":40,"shape":"rectangle","color":"paper","parent":"$root"}]}""",
            "create_groups" to """{"groups":[{"id":"G","title":"new","x":1,"y":2,"width":100,"height":200,"color":"group","children":["$root"]}]}""",
            "create_relations" to """{"relations":[{"id":"R","source":"$participant","target":"$outside","direction":"Forward"}]}""",
        )
        badCalls.forEach { (name, args) ->
            val result = call(name, args)
            assertEquals(AiDiagramToolStatus.Rejected, result.status, name)
            assertEquals(AiDiagramToolFailure.Unsupported, result.failure, name)
            assertNull(engine.proposal())
        }
        assertEquals(AiDiagramToolStatus.Proposed, call("update_nodes", """{"nodes":[{"id":"$outside","text":"updated"}]}""").status)
        assertTrue(aiSequenceUnchanged(w, assertIs<AiProposalPreview.Ready>(engine.proposal()!!.preview(w)).workspace))
    }

    @Test fun finalApplyGateRefusesHandBuiltSequenceLayoutEvenWhenDomainAllowsIt() {
        val w = workspace()
        val root = w.objects.getValue(w.sequenceDiagrams.values.single().containerId)
        val op = TransformObjectsOperation("move", listOf(TransformChange(root.id, root.version, root.transform, root.transform.copy(position = Vec2(25f, 30f)))))
        val proposal = AiProposal("p", w.version, listOf(AiProposalItem("move", "Move", "", op)))
        assertIs<AiProposalPreview.Ready>(proposal.preview(w))
        val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, w.version, 0, w)
        assertEquals(AiDiagramApplyFailure.Unsupported,
            assertIs<AiDiagramApplyReview.Rejected>(reviewAiDiagramApply(proposal, w, w, owner, owner, owner)).reason)
    }
}
