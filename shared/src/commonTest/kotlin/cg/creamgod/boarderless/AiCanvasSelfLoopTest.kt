package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiCanvasSelfLoopTest {
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2(20f, 30f), CanvasSize(120f, 60f)), text = "匯入")
    private val baseline = Workspace(WorkspaceId("w"), "Board", objects = mapOf(node.id to node))
    private fun call(id: String, name: String, args: String = "{}") = AiDiagramToolCall("request", "session", id, name, args)
    private val loops = """{"relations":[{"id":"loop1","source":"N1","target":"N1","direction":"Forward","label":"重試"},{"id":"loop2","source":"N1","target":"N1","direction":"Backward"}]}"""

    @Test fun nativeCanvasCapabilityCreatesReviewablePortableLoopsWithUndoRedo() {
        val draft = AiDiagramDraft("request", "session", baseline, setOf(node.id), aiCanvasToolCapabilities(true))
        assertTrue(draft.execute(call("caps", "list_diagram_capabilities")).data.getValue("selfLoops").jsonPrimitive.boolean)
        assertEquals(AiDiagramToolStatus.Proposed, draft.execute(call("loops", "create_relations", loops)).status)
        assertTrue(baseline.relations.isEmpty())
        val owner = WorkspaceSession("local", "client", WorkspaceMemberRole.Owner, baseline.version, 0, baseline)
        val review = assertIs<AiDiagramApplyReview.Ready>(reviewAiDiagramApply(assertNotNull(draft.proposal()), baseline, baseline, owner, owner, owner))
        val applied = WorkspaceHistory(baseline).execute(review.operation)
        assertTrue(applied.succeeded)
        val graph = applied.history.workspace
        assertEquals(2, graph.relations.size)
        val edges = graph.relations.values.toList()
        assertEquals(listOf(80f, 112f), edges.map { (assertNotNull(it.geometry).route as RelationRoute.Loop).extent })
        for (edge in edges) {
            assertEquals(node.id, edge.sourceObjectId)
            assertEquals(node.id, edge.targetObjectId)
            assertTrue(assertNotNull(relationWorldRoute(edge, graph.objects)).size >= 4)
        }
        val copied = assertNotNull(workspaceClipboardPayload(graph, setOf(node.id)))
        assertNull(validateClipboardPayload(copied))
        assertEquals(2, clipboardGeometryRoutes(copied).size)
        val roundtrip = ClipboardJson.decodeFromString(ClipboardPayload.serializer(), ClipboardJson.encodeToString(ClipboardPayload.serializer(), copied))
        assertEquals(copied, roundtrip)
        val undone = applied.history.undo()
        assertTrue(undone.succeeded)
        assertTrue(undone.history.workspace.relations.isEmpty())
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        assertEquals(edges.map { it.geometry }, redone.history.workspace.relations.values.map { it.geometry })
    }

    @Test fun backendCapabilityStillRejectsSelfLoopWithoutChangingDraft() {
        val draft = AiDiagramDraft("request", "session", baseline, setOf(node.id), aiCanvasToolCapabilities(false))
        assertFalse(draft.execute(call("caps", "list_diagram_capabilities")).data.getValue("selfLoops").jsonPrimitive.boolean)
        assertEquals(AiDiagramToolStatus.Rejected, draft.execute(call("loops", "create_relations", loops)).status)
        assertNull(draft.proposal())
        assertTrue(baseline.relations.isEmpty())
    }
}
