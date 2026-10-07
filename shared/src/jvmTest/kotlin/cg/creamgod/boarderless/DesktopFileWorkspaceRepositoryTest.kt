package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DesktopFileWorkspaceRepositoryTest {
    private inline fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-local-documents")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2(34f, 80f), CanvasSize(260f, 132f)), text = "離線🙂")
    private val create = CreateObjectsOperation("create", listOf(node))

    @Test fun saveIsDurableBeforeSubmitAndRestartRestoresLatestQueuedEditsAndSameIdentity() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root)
                val session = repo.openOrCreateWorkspace()
                assertTrue(repo.isLocalOnly)
                val first = (create.applyTo(session.workspace) as OperationResult.Applied).workspace
                repo.retainDraft(session, session.workspace, create)
                val edit = EditTextOperation("edit", listOf(TextChange(node.id, 1, node.text, "已保存中文🙂")))
                repo.retainDraft(session, first, edit)
                repo.close() // No submit or network ACK was needed to preserve either edit.
                val reopened = DesktopFileWorkspaceRepository(root)
                val restored = reopened.openOrCreateWorkspace()
                assertEquals(session.userId, restored.userId)
                assertEquals(session.clientId, restored.clientId)
                assertEquals(session.workspace.id, restored.workspace.id)
                assertEquals("已保存中文🙂", (restored.workspace.objects.getValue(node.id) as TextNode).text)
                assertEquals(2L, restored.workspaceVersion)
                assertEquals(0L, restored.lastServerSeq)
                assertEquals(SubmitOutcome.Accepted(1, 0), reopened.submit(session, create))
                assertEquals(SubmitOutcome.Accepted(2, 0), reopened.submit(session, edit))
                assertEquals(restored, reopened.refresh(restored))
                assertEquals(2, reopened.listRecentActivity(restored).size)
                assertTrue(reopened.listAssets(restored).isEmpty())
            }
        }

    @Test fun realHistoryUndoRedoAndMultipleDocumentsAreSavedAndRestored() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                var history = WorkspaceHistory(session.workspace)

                fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                }
                save(history.execute(create))
                save(history.undo())
                assertTrue(
                    repo
                        .refresh(session)
                        .workspace.objects
                        .isEmpty(),
                )
                save(history.redo())
                val original = session
                val second = repo.createWorkspace(session, "第二張圖")
                assertTrue(second.workspace.objects.isEmpty())
                repo.renameWorkspace(second, second.workspace.id, "重新命名🙂")
                val reopened = DesktopFileWorkspaceRepository(root)
                assertEquals("重新命名🙂", reopened.openOrCreateWorkspace().workspace.title)
                assertEquals(2, reopened.listWorkspaces(second).size)
                val restored = reopened.openWorkspace(second, original.workspace.id)
                assertEquals(history.workspace, restored.workspace)
                assertEquals(node.text, (restored.workspace.objects.getValue(node.id) as TextNode).text)
            }
        }

    @Test fun canvasStyleApplyUndoRedoAndRestartAreSavedWithTheDocument() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                var history = WorkspaceHistory(session.workspace)

                fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    val operation = assertNotNull(result.appliedOperation)
                    repo.retainDraft(session, history.workspace, operation)
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                }
                val style = CanvasStyle(backgroundToken = "#204060", gridStyle = CanvasGridStyle("dots"))
                save(history.execute(UpdateCanvasStyleOperation("style", 0, CanvasStyle(), style)))
                save(history.undo(remoteRestoration = true))
                assertEquals(CanvasStyle(), DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace.canvasStyle)
                save(history.redo(remoteRestoration = true))
                val restored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                assertEquals(style, restored.workspace.canvasStyle)
                assertEquals(3L, restored.workspace.canvasStyleVersion)
                assertEquals(history.workspace, restored.workspace)
                assertTrue(repo.supportsCanvasStyle)
            }
        }

    @Test fun selfLoopGeometryEditsAndNodeCascadeUndoReopenWithoutLosingPaths() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                var history = WorkspaceHistory(session.workspace)

                fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                }
                save(history.execute(create))
                val loop =
                    Relation(
                        RelationId("loop"),
                        sourceObjectId = node.id,
                        targetObjectId = node.id,
                        label = "自循環🙂",
                        geometry = RelationGeometry(route = RelationRoute.Loop("left", 128f)),
                    )
                save(history.execute(CreateRelationsOperation("loop-create", listOf(loop))))
                val before = RelationAttributes(loop.direction, loop.intent, loop.label, loop.colorToken, loop.geometry)
                val geometry =
                    loop.geometry!!.copy(
                        route = RelationRoute.Loop("top", 160f),
                        labelPlacement = RelationLabelPlacement.Manual(0.5f, 0f, 40f),
                    )
                save(
                    history.execute(
                        UpdateRelationAttributesOperation(
                            "loop-edit",
                            listOf(RelationAttributesChange(loop.id, 1, before, before.copy(geometry = geometry))),
                        ),
                    ),
                )
                val restored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                assertEquals(history.workspace, restored.workspace)
                assertEquals(
                    geometry,
                    restored.workspace.relations
                        .getValue(loop.id)
                        .geometry,
                )
                val current = history.workspace.relations.getValue(loop.id)
                save(history.execute(DeleteObjectsOperation("cascade", listOf(node), listOf(current))))
                assertTrue(
                    DesktopFileWorkspaceRepository(root)
                        .openOrCreateWorkspace()
                        .workspace.relations
                        .isEmpty(),
                )
                save(history.undo(remoteRestoration = true))
                val reopened = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                assertEquals(history.workspace, reopened.workspace)
                assertEquals(1, reopened.workspace.relations.size)
                assertEquals(
                    geometry,
                    reopened.workspace.relations
                        .getValue(loop.id)
                        .geometry,
                )
                assertTrue(repo.supportsRelationGeometry)
            }
        }

    @Test fun completeGeometryGestureSavesOnceAndReopensWithUndoResult() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                repo.retainDraft(session, session.workspace, create)
                session = repo.refresh(session)
                val loop =
                    Relation(
                        RelationId("drag-loop"),
                        sourceObjectId = node.id,
                        targetObjectId = node.id,
                        geometry = RelationGeometry(route = RelationRoute.Loop()),
                    )
                repo.retainDraft(session, session.workspace, CreateRelationsOperation("loop", listOf(loop)))
                session = repo.refresh(session)
                val base = session.workspace
                val gesture =
                    cg.creamgod.boarderless.feature.canvas.RelationGeometryGesture.begin(
                        session,
                        base,
                        loop,
                        Viewport(),
                        cg.creamgod.boarderless.feature.canvas.RelationGeometryHandle.LoopExtent,
                        "drag",
                    )
                val preview = gesture.move(Vec2(20f, 0f)).move(Vec2(64f, 0f))
                assertEquals(base, repo.refresh(session).workspace)
                val operation = assertNotNull(preview.operation(session, base, Viewport()))
                val result = WorkspaceHistory(base).execute(operation)
                repo.retainDraft(session, base, operation)
                val restored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                assertEquals(result.history.workspace, restored.workspace)
                assertEquals(3, repo.listRecentActivity(restored).size)
                val undone = result.history.undo()
                repo.retainDraft(restored, result.history.workspace, assertNotNull(undone.appliedOperation))
                assertEquals(
                    loop.geometry,
                    DesktopFileWorkspaceRepository(root)
                        .openOrCreateWorkspace()
                        .workspace.relations
                        .getValue(loop.id)
                        .geometry,
                )
            }
        }

    @Test fun writeFailuresLeaveOldOrNewCompleteDocumentReadableAfterRestart() {
        for (stage in AtomicDraftStage.entries) {
            temporary { root ->
                runTest {
                    var armed = false
                    val repo =
                        DesktopFileWorkspaceRepository(
                            DesktopAtomicDraftStore(root) {
                                if (armed &&
                                    it == stage
                                ) {
                                    error("File checkpoint")
                                }
                            },
                        )
                    val session = repo.openOrCreateWorkspace()
                    armed = true
                    assertFails { repo.retainDraft(session, session.workspace, create) }
                    val restored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                    assertEquals(session.clientId, restored.clientId)
                    assertEquals(session.workspace.id, restored.workspace.id)
                    if (stage == AtomicDraftStage.DataForced) {
                        assertEquals(session.workspace, restored.workspace)
                    } else {
                        assertEquals(node, restored.workspace.objects.getValue(node.id))
                    }
                }
            }
        }
    }

    @Test fun staleWriterAndChangedOperationIdentityCannotOverwriteSavedContent() =
        temporary { root ->
            runTest {
                val first = DesktopFileWorkspaceRepository(root)
                val second = DesktopFileWorkspaceRepository(root)
                val session = first.openOrCreateWorkspace()
                val stale = second.openOrCreateWorkspace()
                assertFails { first.retainDraft(session, session.workspace, create.copy(operationId = "")) }
                assertEquals(session, first.refresh(session))
                first.retainDraft(session, session.workspace, create)
                val expected = first.refresh(session)
                assertFails {
                    second.retainDraft(
                        stale,
                        stale.workspace,
                        CreateObjectsOperation("other", listOf(node.copy(id = CanvasObjectId("other")))),
                    )
                }
                assertFails { first.submit(session, create.copy(objects = listOf(node.copy(text = "Changed")))) }
                assertEquals(expected, second.refresh(stale))
                assertFails { second.refresh(stale.copy(clientId = "foreign")) }
            }
        }

    @Test fun corruptOrUnknownFileIsReportedWithoutResettingOrReplacingIt() {
        for (mode in listOf("corrupt", "schema")) {
            temporary { root ->
                runTest {
                    DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                    val files = DesktopAtomicDraftStore(root)
                    val key = files.recordKeys().single()
                    val path = root.resolve("$key.record")
                    if (mode == "corrupt") {
                        val bytes = Files.readAllBytes(path)
                        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
                        Files.write(path, bytes)
                    } else {
                        val current = files.read(key)!!
                        val d = Json.parseToJsonElement(current.payload!!.decodeToString()).jsonObject
                        files.compareAndSet(
                            key,
                            current.generation,
                            JsonObject(d + ("version" to JsonPrimitive(2))).toString().encodeToByteArray(),
                        )
                    }
                    val original = Files.readAllBytes(path)
                    assertFails { DesktopFileWorkspaceRepository(root).openOrCreateWorkspace() }
                    assertContentEquals(original, Files.readAllBytes(path))
                }
            }
        }
    }
}
