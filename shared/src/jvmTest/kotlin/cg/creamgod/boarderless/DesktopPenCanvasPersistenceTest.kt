package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.*

class DesktopPenCanvasPersistenceTest {
    @Test fun penPreviewInsertSchemeReopenAndActualUndoRedoPreserveStructuredPath() =
        runTest {
            val root = Files.createTempDirectory("boarderless-pen-canvas")
            try {
                val repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                var history = WorkspaceHistory(session.workspace)
                val snapshot =
                    Files.walk(root).use { paths ->
                        paths.filter { Files.isRegularFile(it) }.toList().associateWith(Files::readAllBytes)
                    }
                val capture = PenCanvasCreation(session, history.workspace, Vec2(50f, 50f))
                val editor = PenPathDraft(CanvasSize(400f, 300f)).append(PenAnchor(Vec2(-10f, 0f))).append(PenAnchor(Vec2(100f, 50f)))
                val preview = editor.replace(1, PenAnchor(Vec2(110f, 60f))).toVectorPath()
                snapshot.forEach { (file, bytes) -> assertContentEquals(bytes, Files.readAllBytes(file)) }
                assertTrue(
                    repo
                        .refresh(session)
                        .workspace.objects
                        .isEmpty(),
                )
                val operation = capture.operation(preview, "pen", "insert")

                fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                }
                save(history.execute(operation))
                val node =
                    history.workspace.objects.values
                        .single() as TextNode
                assertEquals(SubmitOutcome.Accepted(1, 0), repo.submit(session.copy(workspaceVersion = 0), operation))
                val reopened = DesktopFileWorkspaceRepository(root)
                assertTrue(reopened.supportsVectorPaths)
                assertEquals(
                    node,
                    reopened
                        .openOrCreateWorkspace()
                        .workspace.objects
                        .getValue(node.id),
                )
                val payload =
                    ClipboardPayload(
                        nodes =
                            listOf(
                                ClipboardNode(
                                    "pen",
                                    0f,
                                    0f,
                                    node.transform.size.width,
                                    node.transform.size.height,
                                    0f,
                                    "",
                                    "paper",
                                    vectorPath = node.vectorPath,
                                ),
                            ),
                    )
                val scheme = assertNotNull(repo.quickSchemeRepository).save(ClipboardJson.encodeToString(payload), "筆畫🙂", payload.version)
                assertEquals(
                    node.vectorPath,
                    decodeQuickSchemePayload(
                        assertNotNull(DesktopFileWorkspaceRepository(root).quickSchemeRepository).list().single {
                            it.id ==
                                scheme.id
                        },
                    )!!.nodes.single().vectorPath,
                )
                save(history.undo())
                assertTrue(
                    DesktopFileWorkspaceRepository(root)
                        .openOrCreateWorkspace()
                        .workspace.objects
                        .isEmpty(),
                )
                save(history.redo())
                assertEquals(
                    node.vectorPath,
                    (
                        DesktopFileWorkspaceRepository(root)
                            .openOrCreateWorkspace()
                            .workspace.objects
                            .getValue(node.id) as TextNode
                    ).vectorPath,
                )
                assertEquals(3, repo.listRecentActivity(session).size)
            } finally {
                Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }

    @Test fun explicitSamePenInsertionSettlesEveryFileFaultWithoutDuplicatingOrOverwritingLaterEdits() =
        runTest {
            for (stage in AtomicDraftStage.entries) {
                val root = Files.createTempDirectory("boarderless-pen-retry")
                try {
                    var armed = false
                    val repo =
                        DesktopFileWorkspaceRepository(DesktopAtomicDraftStore(root) { if (armed && it == stage) error("File checkpoint") })
                    val session = repo.openOrCreateWorkspace()
                    val path = PenPathDraft(CanvasSize(400f, 300f), listOf(PenAnchor(Vec2.Zero), PenAnchor(Vec2(100f, 50f)))).toVectorPath()
                    val operation = PenCanvasCreation(session, session.workspace, Vec2(50f, 50f)).operation(path, "pen", "insert")
                    armed = true
                    assertFails { repo.retainDraft(session, session.workspace, operation) }
                    armed = false
                    val reopened = DesktopFileWorkspaceRepository(root)
                    reopened.retainDraft(session, session.workspace, operation)
                    val accepted = reopened.refresh(session)
                    assertEquals(1, reopened.listRecentActivity(accepted).size)
                    assertEquals((operation.applyTo(session.workspace) as OperationResult.Applied).workspace, accepted.workspace)
                    assertEquals(SubmitOutcome.Accepted(1, 0), reopened.submit(session, operation))
                    reopened.retainDraft(session, session.workspace, operation) // Same confirmed operation is idempotent.
                    assertEquals(1, reopened.listRecentActivity(accepted).size)
                    val node = accepted.workspace.objects.getValue(CanvasObjectId("pen")) as TextNode
                    assertFails {
                        reopened.retainDraft(
                            session,
                            session.workspace,
                            operation.copy(objects = listOf(node.copy(text = "Different"))),
                        )
                    }
                    val edit = EditTextOperation("label", listOf(TextChange(node.id, node.version, "", "標籤")))
                    reopened.retainDraft(accepted, accepted.workspace, edit)
                    assertFails { reopened.retainDraft(session, session.workspace, operation) } // Never rewind a newer canvas.
                    assertEquals(
                        "標籤",
                        (
                            reopened
                                .refresh(accepted)
                                .workspace.objects
                                .getValue(node.id) as TextNode
                        ).text,
                    )
                    assertEquals(2, reopened.listRecentActivity(accepted).size)
                } finally {
                    Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                }
            }
        }
}
