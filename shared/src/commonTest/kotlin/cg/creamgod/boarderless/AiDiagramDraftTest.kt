package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiDiagramDraftTest {
    private val approved =
        TextNode(CanvasObjectId("approved-uuid"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "Read me")
    private val hidden = approved.copy(id = CanvasObjectId("hidden-private-uuid"), text = "private-secret")
    private val baseline =
        Workspace(WorkspaceId("workspace"), "Fixture", version = 8, objects = listOf(approved, hidden).associateBy { it.id })
    private val caps = AiDiagramCapabilities(setOf(NodeShape.Rectangle, NodeShape.RoundedRectangle), setOf("paper", "blue"), setOf("group"))

    private fun draft(
        workspace: Workspace = baseline,
        ids: Set<CanvasObjectId> = setOf(approved.id),
    ) = AiDiagramDraft("request", "session", workspace, ids, caps)

    private fun call(
        id: String,
        name: String,
        args: String = "{}",
    ) = AiDiagramToolCall("request", "session", id, name, args)

    private fun node(
        id: String,
        text: String = "New",
    ) = """{"id":"$id","text":"$text","x":10,"y":20,"width":100,"height":40,"shape":"rectangle","color":"paper"}"""

    private fun create(
        engine: AiDiagramDraft,
        callId: String = "create",
        id: String = "A",
    ) = engine.execute(call(callId, "create_nodes", """{"nodes":[${node(id)}]}"""))

    private fun ready(engine: AiDiagramDraft): AiProposalPreview.Ready = assertIs(engine.proposal()!!.preview(baseline))

    @Test fun approvedReadIsSanitizedAndCapabilitiesDoNotInventMediaPenOrSelfLoops() {
        val engine = draft()
        val graph = engine.execute(call("read", "read_approved_graph"))
        assertEquals(AiDiagramToolStatus.Read, graph.status)
        val content = graph.data.toString()
        assertContains(content, "Read me")
        for (secret in listOf("hidden-private-uuid", "private-secret", "approved-uuid", "workspace")) assertFalse(secret in content)
        val capabilities = engine.execute(call("caps", "list_diagram_capabilities"))
        assertEquals(
            false,
            capabilities.data
                .getValue("selfLoops")
                .jsonPrimitive.boolean,
        )
        assertFalse("pen" in capabilities.data.toString())
        val media =
            MediaNode(
                CanvasObjectId("media"),
                transform = approved.transform,
                assetId = "private-asset",
                mediaKind = MediaKind.Image,
                thumbnailAssetId = "private-thumbnail",
                altText = "Caption",
            )
        val mediaGraph =
            draft(
                baseline.copy(objects = mapOf(media.id to media)),
                setOf(media.id),
            ).execute(call("read", "read_approved_graph")).data.toString()
        assertContains(mediaGraph, "Caption")
        assertFalse("private-asset" in mediaGraph)
        assertFalse("private-thumbnail" in mediaGraph)
    }

    @Test fun callerOwnedContextAndCapabilitiesAreFrozenAndLogicalEdgeReferencesAreStable() {
        val objects = mutableMapOf(approved.id to approved, hidden.id to hidden)
        val ids = mutableSetOf(approved.id, hidden.id)
        val shapes = mutableSetOf(NodeShape.Rectangle)
        val edge = Relation(RelationId("private-edge-uuid"), sourceObjectId = approved.id, targetObjectId = hidden.id)
        val engine =
            AiDiagramDraft(
                "request",
                "session",
                baseline.copy(objects = objects, relations = mapOf(edge.id to edge)),
                ids,
                caps.copy(shapes = shapes),
            )
        objects.clear()
        ids.clear()
        shapes.clear()
        val graph = engine.execute(call("read", "read_approved_graph"))
        assertEquals(
            2,
            graph.data
                .getValue("objects")
                .jsonArray.size,
        )
        assertEquals(
            "E1",
            graph.data
                .getValue("relations")
                .jsonArray
                .single()
                .jsonObject
                .getValue("id")
                .jsonPrimitive.content,
        )
        assertFalse("private-edge-uuid" in graph.data.toString())
        assertEquals(AiDiagramToolStatus.Proposed, create(engine).status)
        assertEquals(AiDiagramToolStatus.Rejected, create(engine, "collision", "E1").status)
    }

    @Test fun completeDiagramToolsProduceOnlyIsolatedProposalAndExcludingDependenciesConflicts() {
        val engine = draft()
        assertEquals(AiDiagramToolStatus.Proposed, create(engine).status)
        assertEquals(
            AiDiagramToolStatus.Proposed,
            engine
                .execute(
                    call(
                        "group",
                        "create_groups",
                        """{"groups":[{"id":"G","title":"Flow","x":0,"y":0,"width":300,"height":200,"color":"group","children":["A"]}]}""",
                    ),
                ).status,
        )
        assertEquals(
            AiDiagramToolStatus.Proposed,
            engine
                .execute(
                    call(
                        "edge",
                        "create_relations",
                        """{"relations":[{"id":"R","source":"N1","target":"A","direction":"Forward","label":"Then"}]}""",
                    ),
                ).status,
        )
        assertEquals(
            AiDiagramToolStatus.Proposed,
            engine
                .execute(
                    call(
                        "update",
                        "update_nodes",
                        """{"nodes":[{"id":"A","text":"Edited","color":"blue","shape":"rounded"}]}""",
                    ),
                ).status,
        )
        assertEquals(
            AiDiagramToolStatus.Proposed,
            engine
                .execute(
                    call(
                        "layout",
                        "layout_nodes",
                        """{"nodes":[{"id":"A","x":50,"y":60,"width":120,"height":80,"rotation":10}]}""",
                    ),
                ).status,
        )
        val proposal = engine.proposal()!!
        val preview = ready(engine)
        assertEquals(4, preview.workspace.objects.size)
        assertEquals(1, preview.workspace.relations.size)
        assertEquals(hidden, preview.workspace.objects[hidden.id])
        val new =
            preview.workspace.objects.values
                .filterIsInstance<TextNode>()
                .single { it.text == "Edited" }
        assertEquals(NodeShape.RoundedRectangle, new.shape)
        assertEquals("blue", new.colorToken)
        assertNotNull(new.parentId)
        assertEquals(50f, new.transform.position.x)
        assertEquals(2, baseline.objects.size)
        assertTrue(baseline.relations.isEmpty())
        assertEquals(AiProposalStatus.Ready, proposal.status)
        val history = WorkspaceHistory(baseline).execute(preview.operation).history
        val undone = history.undo().history
        assertEquals(baseline.objects, undone.workspace.objects)
        assertEquals(baseline.relations, undone.workspace.relations)
        val redone = undone.redo().history.workspace
        assertEquals(4, redone.objects.size)
        assertEquals(1, redone.relations.size)
        assertTrue(
            redone.objects.values
                .filterIsInstance<TextNode>()
                .any { it.text == "Edited" },
        )
        assertIs<AiProposalPreview.Conflict>(proposal.withItemIncluded(proposal.items.first().itemId, false).preview(baseline))
        assertIs<AiProposalPreview.Conflict>(proposal.preview(baseline.copy(version = 9)))
        val validated = engine.execute(call("validate", "validate_diagram"))
        assertEquals(
            false,
            validated.data
                .getValue("committed")
                .jsonPrimitive.boolean,
        )
    }

    @Test fun completeCallSchemaRejectsUnknownFieldsDuplicateEscapedKeysAndInvalidNumbersWithoutChangingDraft() {
        val engine = draft()
        create(engine)
        val original = engine.proposal()
        val malformed =
            listOf(
                """{"nodes":[${node("B").dropLast(1)},"locked":false}]}""",
                """{"nodes":[],"\u006eodes":[${node("B")}] }""",
                """{"nodes":[${node("B").replace("\"x\":10", "\"x\":\"10\"")}]}""",
                """{"nodes":[${node("B").replace("\"width\":100", "\"width\":0")}]}""",
                """{"nodes":[${node("B").replace("\"x\":10", "\"x\":1e99")}]}""",
                """{"nodes":[${node("B").replace("rectangle", "unknown-shape")}]}""",
                """{"nodes":[${node("B").replace("paper", "unknown-color")}]}""",
                """{"nodes":[${node("B")}]} trailing""",
            )
        malformed.forEachIndexed { i, args ->
            assertEquals(AiDiagramToolStatus.Rejected, engine.execute(call("bad$i", "create_nodes", args)).status)
            assertEquals(original, engine.proposal())
        }
        assertEquals(AiDiagramToolFailure.UnknownTool, engine.execute(call("unknown", "delete_objects")).failure)
        assertEquals(
            AiDiagramToolFailure.DuplicateCall,
            engine.execute(call("bad0", "create_nodes", """{"nodes":[${node("B")}] }""")).failure,
        )
    }

    @Test fun scopeLockedAncestorsAndHiddenGroupDescendantsCannotBeModifiedOrRead() {
        val engine = draft()
        assertEquals(AiDiagramToolFailure.Scope, engine.execute(call("other", "read_approved_graph").copy(sessionId = "other")).failure)
        assertEquals(
            AiDiagramToolFailure.Scope,
            engine
                .execute(
                    call(
                        "private",
                        "layout_nodes",
                        """{"nodes":[{"id":"hidden-private-uuid","x":1,"y":1,"width":10,"height":10}]}""",
                    ),
                ).failure,
        )
        val group = GroupFrame(CanvasObjectId("group"), locked = true, transform = approved.transform)
        val child = approved.copy(parentId = group.id)
        val locked = draft(baseline.copy(objects = mapOf(group.id to group, child.id to child)), setOf(child.id))
        assertEquals(
            AiDiagramToolFailure.Locked,
            locked.execute(call("edit", "update_nodes", """{"nodes":[{"id":"N1","text":"Changed"}]}""")).failure,
        )
        val openGroup = group.copy(locked = false)
        val hiddenChild = hidden.copy(parentId = group.id)
        val partial = draft(baseline.copy(objects = mapOf(group.id to openGroup, hidden.id to hiddenChild)), setOf(group.id))
        assertEquals(
            AiDiagramToolFailure.Scope,
            partial
                .execute(
                    call(
                        "move",
                        "layout_nodes",
                        """{"nodes":[{"id":"N1","x":1,"y":1,"width":10,"height":10}]}""",
                    ),
                ).failure,
        )
        assertNull(partial.proposal())
    }

    @Test fun batchGroupCyclesInvalidEdgesAndSelfLoopsLeaveNoPartialObjectsOrMapping() {
        val engine = draft()
        val cycle = """{"groups":[{"id":"G1","title":"One","x":0,"y":0,"width":100,"height":100,"color":"group","parent":"G2"},{"id":"G2","title":"Two","x":0,"y":0,"width":100,"height":100,"color":"group","parent":"G1"}]}"""
        assertEquals(AiDiagramToolFailure.Dependency, engine.execute(call("cycle", "create_groups", cycle)).failure)
        assertNull(engine.proposal())
        assertEquals(AiDiagramToolStatus.Proposed, create(engine, "reuse", "G1").status)
        val before = engine.proposal()
        assertEquals(
            AiDiagramToolFailure.Unsupported,
            engine
                .execute(
                    call(
                        "self",
                        "create_relations",
                        """{"relations":[{"id":"R","source":"G1","target":"G1","direction":"Forward"}]}""",
                    ),
                ).failure,
        )
        assertEquals(
            AiDiagramToolFailure.Scope,
            engine
                .execute(
                    call(
                        "unknown",
                        "create_relations",
                        """{"relations":[{"id":"R","source":"G1","target":"private","direction":"Forward"}]}""",
                    ),
                ).failure,
        )
        assertEquals(before, engine.proposal())
        assertEquals(AiDiagramToolStatus.Proposed, create(engine, "reuseedge", "R").status)
    }

    @Test fun utf8DepthCallAndWireBudgetsAreBoundedAndCloseDiscardsAllPrivateState() {
        val engine = draft()
        assertEquals(
            AiDiagramToolFailure.Limit,
            engine.execute(call("large", "create_nodes", """{"nodes":[${node("A", "漢".repeat(30_000))}]}""")).failure,
        )
        assertNull(engine.proposal())
        val deep = "{\"extra\":" + "[".repeat(80) + "0" + "]".repeat(80) + "}"
        assertEquals(AiDiagramToolStatus.Rejected, engine.execute(call("depth", "create_nodes", deep)).status)
        repeat(62) { engine.execute(call("read$it", "read_approved_graph")) }
        assertEquals(AiDiagramToolFailure.Limit, engine.execute(call("overflow", "read_approved_graph")).failure)
        val bounded = draft()
        val nodes = (1..128).joinToString(",") { node("A$it") }
        assertEquals(AiDiagramToolStatus.Proposed, bounded.execute(call("batch", "create_nodes", "{\"nodes\":[$nodes]}")).status)
        val updates = (1..40).joinToString(",") { "{\"id\":\"A$it\",\"text\":\"Changed\",\"color\":\"blue\"}" }
        assertEquals(AiDiagramToolFailure.Limit, bounded.execute(call("wire", "update_nodes", "{\"nodes\":[$updates]}")).failure)
        assertEquals(1, bounded.proposal()!!.items.size)
        bounded.close()
        assertNull(bounded.proposal())
        assertEquals(AiDiagramToolFailure.Closed, bounded.execute(call("closed", "read_approved_graph")).failure)
    }
}
