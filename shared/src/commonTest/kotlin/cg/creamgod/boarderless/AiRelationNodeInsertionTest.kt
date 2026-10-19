package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.data.remote.toExpandedDtos
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiRelationNodeInsertionTest {
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f)), text = "A")
    private val b = a.copy(id = CanvasObjectId("b"), transform = a.transform.copy(position = Vec2(600f, 0f)), text = "B")
    private val edge = Relation(RelationId("edge"), version = 3, sourceObjectId = a.id, targetObjectId = b.id, label = "條件🙂", intent = "supports")
    private val baseline = Workspace(WorkspaceId("w"), "Canvas", 7, mapOf(a.id to a, b.id to b), mapOf(edge.id to edge))
    private val args = """{"relation":"E1","node":{"id":"M","text":"中繼🙂","x":300,"y":100,"width":120,"height":60,"shape":"rectangle","color":"paper"},"firstRelation":"R1","secondRelation":"R2"}"""
    private fun call(id: String = "insert", body: String = args) = AiDiagramToolCall("request", "session", id, "insert_node_on_relation", body)
    private fun draft(workspace: Workspace = baseline, approved: Set<CanvasObjectId> = setOf(a.id, b.id)) =
        AiDiagramDraft("request", "session", workspace, approved, aiCanvasToolCapabilities(false))

    @Test fun scopeReviewAliasesMatchToolGraphForParallelAndOutsideRelations() {
        val outside = a.copy(id = CanvasObjectId("outside"), text = "private")
        val parallel = edge.copy(id = RelationId("a-parallel"), direction = RelationDirection.Backward)
        val foreign = edge.copy(id = RelationId("foreign"), targetObjectId = outside.id)
        val before = baseline.copy(objects = mapOf(outside.id to outside, b.id to b, a.id to a),
            relations = mapOf(foreign.id to foreign, edge.id to edge, parallel.id to parallel))
        val approved = linkedSetOf(b.id, a.id)
        val refs = aiDiagramObjectReferences(approved)
        val edges = aiDiagramRelationReferences(before, approved)
        assertEquals(mapOf("N1" to a.id, "N2" to b.id), refs)
        assertEquals(mapOf("E1" to parallel.id, "E2" to edge.id), edges)
        val engine = draft(before, approved)
        val result = engine.execute(AiDiagramToolCall("request", "session", "read", "read_approved_graph", "{}"))
        assertEquals(AiDiagramToolStatus.Read, result.status)
        val graph = result.data.jsonObject
        assertEquals(refs.keys, graph.getValue("objects").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
        for (relation in graph.getValue("relations").jsonArray) {
            val value = relation.jsonObject
            val original = before.relations.getValue(edges.getValue(value.getValue("id").jsonPrimitive.content))
            assertEquals(refs.entries.single { it.value == original.sourceObjectId }.key, value.getValue("source").jsonPrimitive.content)
            assertEquals(refs.entries.single { it.value == original.targetObjectId }.key, value.getValue("target").jsonPrimitive.content)
            assertEquals(original.direction.name, value.getValue("direction").jsonPrimitive.content)
        }
        assertEquals(2, graph.getValue("relations").jsonArray.size)
        assertFalse("private" in graph.toString())
    }

    @Test fun allDirectionsProduceOneReviewableTransactionAndExistingFourOperationWireContract() {
        for (direction in RelationDirection.entries) {
            val before = baseline.copy(relations = mapOf(edge.id to edge.copy(direction = direction)))
            val engine = draft(before)
            assertEquals(AiDiagramToolStatus.Proposed, engine.execute(call()).status)
            val owner = WorkspaceSession("actor", "client", WorkspaceMemberRole.Owner, before.version, 0, before)
            val reviewed = assertIs<AiDiagramApplyReview.Ready>(reviewAiDiagramApply(assertNotNull(engine.proposal()), before, before, owner, owner, owner))
            val applied = WorkspaceHistory(before).execute(reviewed.operation)
            assertTrue(applied.succeeded)
            val after = applied.history.workspace
            val first = after.relations.values.single { it.label != null }
            val second = after.relations.values.single { it.label == null }
            val inserted = after.objects.getValue(first.targetObjectId) as TextNode
            assertEquals("中繼🙂", inserted.text)
            assertEquals(Vec2(300f, 100f), inserted.transform.position)
            assertEquals(if (direction == RelationDirection.Backward) b.id else a.id, first.sourceObjectId)
            assertEquals(if (direction == RelationDirection.Backward) a.id else b.id, second.targetObjectId)
            assertEquals(inserted.id, second.sourceObjectId)
            assertEquals(if (direction == RelationDirection.Backward) RelationDirection.Forward else direction, first.direction)
            assertEquals(first.direction, second.direction)
            assertEquals(edge.label, first.label)
            assertEquals(edge.intent, first.intent)
            assertNull(second.intent)
            val wire = reviewed.operation.toExpandedDtos(10)
            assertEquals(listOf("create_object", "delete_relations", "create_relation", "create_relation"), wire.map { it.kind })
            assertEquals(mapOf(edge.id.value to 3L), wire[1].expectedObjectVersions)
            val undo = applied.history.undo(remoteRestoration = true)
            assertTrue(undo.succeeded)
            assertEquals(before.objects, undo.history.workspace.objects)
            assertEquals(edge.copy(direction = direction, version = 5), undo.history.workspace.relations.getValue(edge.id))
            val redo = undo.history.redo(remoteRestoration = true)
            assertTrue(redo.succeeded)
            assertEquals(after.relations.keys, redo.history.workspace.relations.keys)
            assertEquals(1, baseline.relations.size)
        }
    }

    @Test fun scopeLocksInvalidShapeAndAliasCollisionsLeaveDraftUnchanged() {
        val locked = baseline.copy(objects = baseline.objects + (a.id to a.copy(locked = true)))
        for ((engine, body) in listOf(
            draft(baseline, setOf(a.id)) to args,
            draft(locked) to args,
            draft() to args.replace("\"R2\"", "\"R1\""),
            draft() to args.replace("rectangle", "not-supported"),
            draft() to args.replace("\"x\":300", "\"x\":1000001"),
            draft() to args.replace("\"E1\"", "\"foreign\""),
        )) {
            val references = engine.localReferences()
            assertEquals(AiDiagramToolStatus.Rejected, engine.execute(call(body = body)).status)
            assertEquals(references, engine.localReferences())
            assertNull(engine.proposal())
        }
    }

    @Test fun deletedLogicalEdgeAliasCannotBeReusedAndStaleOriginalCannotApply() {
        val engine = draft()
        assertEquals(AiDiagramToolStatus.Proposed, engine.execute(call()).status)
        val proposal = assertNotNull(engine.proposal())
        val reuse = engine.execute(AiDiagramToolCall("request", "session", "reuse", "create_relations",
            """{"relations":[{"id":"E1","source":"N1","target":"M","direction":"Forward"}]}"""))
        assertEquals(AiDiagramToolStatus.Rejected, reuse.status)
        assertEquals(proposal, engine.proposal())
        assertIs<AiProposalPreview.Conflict>(proposal.preview(baseline.copy(relations = mapOf(edge.id to edge.copy(version = 4)))))
    }

    @Test fun insertedNodeUsesTopLayerAndUnrepresentableInsertionLeavesDraftUnchanged() {
        val top = b.copy(zIndex = 42)
        val before = baseline.copy(objects = baseline.objects + (b.id to top))
        val engine = draft(before)
        assertEquals(AiDiagramToolStatus.Proposed, engine.execute(call()).status)
        val ready = assertIs<AiProposalPreview.Ready>(assertNotNull(engine.proposal()).preview(before))
        val after = assertIs<OperationResult.Applied>(ready.operation.applyTo(before)).workspace
        assertEquals(43L, after.objects.values.single { it.id !in before.objects }.zIndex)
        for (invalid in listOf(
            before.copy(objects = before.objects + (b.id to top.copy(zIndex = 9_007_199_254_738_990L))),
            before.copy(objects = before.objects + (b.id to top.copy(transform = top.transform.copy(position = Vec2(1_000_001f, 0f))))),
            before.copy(relations = mapOf(edge.id to edge.copy(version = 9_007_199_254_738_991L))),
        )) {
            val rejected = draft(invalid)
            val references = rejected.localReferences()
            assertEquals(AiDiagramToolStatus.Rejected, rejected.execute(call()).status)
            assertEquals(references, rejected.localReferences())
            assertNull(rejected.proposal())
        }
    }

    @Test fun explicitCapabilityControlsBothProviderSchemasAndSubsequentToolResults() {
        val caps = aiCanvasToolCapabilities(false)
        val engine = draft()
        val result = engine.execute(call())
        for (dialect in AiRequestDialect.entries) {
            val names = aiDiagramToolDefinitions(dialect, caps).map {
                val definition = if (dialect == AiRequestDialect.OpenAiChat) it.jsonObject.getValue("function").jsonObject else it.jsonObject
                definition.getValue("name").jsonPrimitive.content
            }
            assertEquals(9, names.size)
            assertContains(names, "insert_node_on_relation")
            val body = aiDiagramToolRequestBody(AiRequestBodyProfile(dialect, "fixture", 2048), "request", "session", "Insert a node", caps,
                listOf(AiDiagramToolTurn("Insert", listOf(call()), listOf(result))))
            assertContains(body, "insert_node_on_relation")
            assertFalse("\"edge\"" in body)
            val disabled = caps.copy(relationNodeInsertion = false)
            assertEquals(8, aiDiagramToolDefinitions(dialect, disabled).size)
            assertFailsWith<AiRequestValidationException> {
                aiDiagramToolRequestBody(AiRequestBodyProfile(dialect, "fixture"), "request", "session", "Insert", disabled,
                    listOf(AiDiagramToolTurn("Insert", listOf(call()), listOf(result))))
            }
        }
    }

    @Test fun nativeLoopInsertionKeepsOriginalGeometryForUndoAndProducesPortableNewEdges() {
        val loop = edge.copy(targetObjectId = a.id, geometry = RelationGeometry(route = RelationRoute.Loop(extent = 180f)))
        val before = baseline.copy(relations = mapOf(loop.id to loop))
        val engine = AiDiagramDraft("request", "session", before, setOf(a.id), aiCanvasToolCapabilities(true))
        assertEquals(AiDiagramToolStatus.Proposed, engine.execute(call()).status)
        val preview = assertIs<AiProposalPreview.Ready>(assertNotNull(engine.proposal()).preview(before))
        val applied = WorkspaceHistory(before).execute(preview.operation)
        assertTrue(applied.succeeded)
        val copied = assertNotNull(workspaceClipboardPayload(applied.history.workspace, applied.history.workspace.objects.keys))
        assertNull(validateClipboardPayload(copied))
        assertEquals(2, copied.relations.size)
        val undo = applied.history.undo()
        assertTrue(undo.succeeded)
        assertEquals(loop.geometry, undo.history.workspace.relations.getValue(loop.id).geometry)
    }
}
