package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.test.*

/** Invoked with actual native record stores, not a memory repository. */
internal suspend fun verifyNativeLocalDocumentReopen(createRepository: () -> CanvasWorkspaceRepository) {
    val repo = createRepository()
    var session = repo.openOrCreateWorkspace()
    var history = WorkspaceHistory(session.workspace)
    fun save(result: HistoryResult) {
        assertTrue(result.succeeded)
        val operation = assertNotNull(result.appliedOperation)
        repo.retainDraft(session, history.workspace, operation)
        history = result.history
        session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
    }
    val node = TextNode(CanvasObjectId("native-node"), transform = CanvasTransform(Vec2(30f, 50f), CanvasSize(100f, 60f)), text = "離線🙂")
    val create = CreateObjectsOperation("native-create", listOf(node))
    save(history.execute(create))
    val edge = Relation(RelationId("native-loop"), sourceObjectId = node.id, targetObjectId = node.id,
        label = "再試🙂", geometry = RelationGeometry(route = RelationRoute.Loop(extent = 144f)))
    save(history.execute(CreateRelationsOperation("native-edge", listOf(edge))))
    val style = CanvasStyle(backgroundToken = "#204060", gridStyle = CanvasGridStyle("dots"))
    save(history.execute(UpdateCanvasStyleOperation("native-style", 0, CanvasStyle(), style)))
    var reopened = createRepository()
    val restored = reopened.openOrCreateWorkspace()
    assertEquals(session, restored)
    assertEquals(SubmitOutcome.Accepted(1, 0), reopened.submit(restored, create))
    assertFails { reopened.submit(restored, create.copy(objects = listOf(node.copy(text = "changed")))) }
    save(history.undo(remoteRestoration = true))
    assertEquals(CanvasStyle(), createRepository().openOrCreateWorkspace().workspace.canvasStyle)
    save(history.redo(remoteRestoration = true))
    assertEquals(history.workspace, createRepository().openOrCreateWorkspace().workspace)
    val second = repo.createWorkspace(session, "第二份🙂")
    assertEquals(second, createRepository().openOrCreateWorkspace())
    repo.renameWorkspace(second, second.workspace.id, "改名🙂")
    reopened = createRepository()
    val active = reopened.openOrCreateWorkspace()
    assertEquals("改名🙂", active.workspace.title)
    assertEquals(history.workspace, reopened.openWorkspace(active, history.workspace.id).workspace)
    assertTrue(repo.isLocalOnly && repo.supportsCanvasStyle && repo.supportsRelationGeometry && repo.supportsVectorPaths && repo.supportsSequenceDiagrams)
    repo.close()
    reopened.close()
}
