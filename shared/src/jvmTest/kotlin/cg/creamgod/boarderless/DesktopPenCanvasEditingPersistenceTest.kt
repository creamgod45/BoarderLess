package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopPenCanvasEditingPersistenceTest {
    private val draft =
        PenPathDraft(
            CanvasSize(100f, 100f),
            listOf(
                PenAnchor(Vec2(10f, 10f), outgoing = Vec2(20f, 0f)),
                PenAnchor(Vec2(70f, 60f), incoming = Vec2(80f, 20f)),
                PenAnchor(Vec2(10f, 80f)),
            ),
            closed = true,
        )
    private val node =
        TextNode(
            CanvasObjectId("pen"),
            transform = CanvasTransform(Vec2(300f, 50f), CanvasSize(200f, 100f), 90f),
            text = "標籤🙂",
            vectorPath = draft.toVectorPath(),
        )
    private val loop =
        Relation(
            RelationId("loop"),
            sourceObjectId = node.id,
            targetObjectId = node.id,
            geometry = RelationGeometry(route = RelationRoute.Loop()),
        )

    @Test fun reopenedPathReeditKeepsIdentityEdgesAndSavesActualUndoRedoAndCancel() =
        runTest {
            val root = Files.createTempDirectory("boarderless-pen-edit")
            try {
                var repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                repo.retainDraft(
                    session,
                    session.workspace,
                    TransactionOperation(
                        "create",
                        listOf(CreateObjectsOperation("node", listOf(node)), CreateRelationsOperation("loop", listOf(loop))),
                    ),
                )
                repo = DesktopFileWorkspaceRepository(root)
                session = repo.openOrCreateWorkspace()
                val original = session.workspace.objects.getValue(node.id) as TextNode
                val captured = PenCanvasEditing(session, session.workspace, original)
                val before =
                    Files.walk(root).use { paths ->
                        paths.filter { Files.isRegularFile(it) }.toList().associateWith(Files::readAllBytes)
                    }
                val changed = captured.draft.replace(1, captured.draft.anchors[1].translated(Vec2(20f, 30f))).toVectorPath()
                val operation = assertNotNull(captured.operation(changed, "edit"))
                before.forEach { (file, bytes) -> assertContentEquals(bytes, Files.readAllBytes(file)) }
                assertEquals(
                    original,
                    repo
                        .refresh(session)
                        .workspace.objects
                        .getValue(node.id),
                ) // Preview/cancel are read-only.
                assertNull(captured.operation(captured.draft.toVectorPath(), "unchanged"))
                var history = WorkspaceHistory(session.workspace)

                fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                }
                save(history.execute(operation))
                val after = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace
                assertEquals(history.workspace, after)
                assertEquals(mapOf(loop.id to loop), after.relations)
                assertEquals(setOf(node.id), after.objects.keys)
                assertEquals("標籤🙂", (after.objects.getValue(node.id) as TextNode).text)
                save(history.undo())
                val undone =
                    DesktopFileWorkspaceRepository(root)
                        .openOrCreateWorkspace()
                        .workspace.objects
                        .getValue(node.id) as TextNode
                assertEquals(original.vectorPath, undone.vectorPath)
                assertEquals(original.transform, undone.transform)
                save(history.redo())
                val redone = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace
                assertEquals(history.workspace, redone)
                assertEquals(mapOf(loop.id to loop), redone.relations)
                assertEquals(4, repo.listRecentActivity(session).size)
            } finally {
                Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }

    @Test fun lostFileConfirmationOfReeditRetriesOneExactTransactionWithoutReplacingNodeOrEdges() =
        runTest {
            for (stage in AtomicDraftStage.entries) {
                val root = Files.createTempDirectory("boarderless-pen-edit-fault")
                try {
                    var armed = false
                    val repo =
                        DesktopFileWorkspaceRepository(DesktopAtomicDraftStore(root) { if (armed && it == stage) error("File checkpoint") })
                    var session = repo.openOrCreateWorkspace()
                    repo.retainDraft(
                        session,
                        session.workspace,
                        TransactionOperation(
                            "create",
                            listOf(CreateObjectsOperation("node", listOf(node)), CreateRelationsOperation("loop", listOf(loop))),
                        ),
                    )
                    session = repo.refresh(session)
                    val capture = PenCanvasEditing(session, session.workspace, session.workspace.objects.getValue(node.id) as TextNode)
                    val changed = capture.draft.copy(style = capture.draft.style.copy(strokeWidth = 4f)).toVectorPath()
                    val operation = assertNotNull(capture.operation(changed, "edit"))
                    armed = true
                    assertFails { repo.retainDraft(session, session.workspace, operation) }
                    armed = false
                    val reopened = DesktopFileWorkspaceRepository(root)
                    reopened.retainDraft(session, session.workspace, operation)
                    val after = reopened.refresh(session)
                    assertEquals((operation.applyTo(session.workspace) as OperationResult.Applied).workspace, after.workspace)
                    assertEquals(mapOf(loop.id to loop), after.workspace.relations)
                    assertEquals(setOf(node.id), after.workspace.objects.keys)
                    assertEquals(2, reopened.listRecentActivity(after).size)
                    assertEquals(SubmitOutcome.Accepted(2, 0), reopened.submit(session, operation))
                    val different =
                        assertNotNull(
                            capture.operation(
                                capture.draft.copy(style = capture.draft.style.copy(strokeWidth = 8f)).toVectorPath(),
                                "edit",
                            ),
                        )
                    assertFails { reopened.retainDraft(session, session.workspace, different) }
                    assertEquals(after, reopened.refresh(after))
                } finally {
                    Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                }
            }
        }
}
