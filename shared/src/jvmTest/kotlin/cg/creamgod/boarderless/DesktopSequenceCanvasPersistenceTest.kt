package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DesktopSequenceCanvasPersistenceTest {
    private fun draft() =
        assertIs<SequenceParseResult.Parsed>(
            MermaidSequenceAdapter.parse(
                """
                sequenceDiagram
                participant A as 呼叫端
                participant B as 服務
                A->>B: request
                alt existing
                B->>B: prepare
                B-->>A: state
                else missing
                B-->>A: empty
                end
                """.trimIndent(),
            ),
        ).draft

    private fun operation(
        session: WorkspaceSession,
        draft: SequenceDiagramDraft = draft(),
    ): TransactionOperation {
        var next = 0
        return SequenceCanvasCreation(session, session.workspace, Vec2(100f, 200f)).operation(draft, "Sequence") { "seq_${++next}" }
    }

    private fun remove(root: Path) {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    @Test fun trueNativeReopenHistoryUndoRedoAndWholeDeletePreserveSemanticsAndCanvasIds() =
        runTest {
            val root = Files.createTempDirectory("boarderless-sequence")
            try {
                var repo = DesktopFileWorkspaceRepository(root)
                var session = repo.openOrCreateWorkspace()
                assertTrue(repo.supportsSequenceDiagrams)
                val record = Files.list(root).use { paths -> paths.filter { it.fileName.toString().endsWith(".record") }.findFirst().get() }
                val original = Files.readAllBytes(record)
                val op = operation(session)
                assertContentEquals(original, Files.readAllBytes(record)) // Preparing a complete preview saves nothing.
                var history = WorkspaceHistory(session.workspace)

                suspend fun save(result: HistoryResult) {
                    assertTrue(result.succeeded)
                    val operation = assertNotNull(result.appliedOperation)
                    repo.retainDraft(session, history.workspace, operation)
                    history = result.history
                    session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                    assertEquals(history.workspace, DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace)
                }
                save(history.execute(op))
                val document =
                    history.workspace.sequenceDiagrams.values
                        .single()
                assertEquals(draft(), document.toDraft())
                save(history.undo(remoteRestoration = true))
                assertTrue(history.workspace.sequenceDiagrams.isEmpty())
                save(history.redo(remoteRestoration = true))
                assertEquals(
                    document,
                    history.workspace.sequenceDiagrams.values
                        .single(),
                )
                val deleting =
                    DeleteObjectsOperation(
                        "delete",
                        history.workspace.objects.values
                            .toList(),
                        history.workspace.relations.values
                            .toList(),
                    )
                save(history.execute(deleteSequenceAware(history.workspace, deleting, "remove-metadata")))
                save(history.undo(remoteRestoration = true))
                assertEquals(
                    document,
                    history.workspace.sequenceDiagrams.values
                        .single(),
                )
                save(history.redo(remoteRestoration = true))
                assertTrue(history.workspace.sequenceDiagrams.isEmpty())
                repo = DesktopFileWorkspaceRepository(root)
                session = repo.openOrCreateWorkspace()
                assertEquals(history.workspace, session.workspace)
                assertEquals(6, repo.listRecentActivity(session).size)
            } finally {
                remove(root)
            }
        }

    @Test fun everyFileCheckpointRetriesTheExactAtomicCreationOnceWithoutReallocatingIds() =
        runTest {
            for (stage in AtomicDraftStage.entries) {
                val root = Files.createTempDirectory("boarderless-sequence-fault")
                try {
                    var armed = false
                    val repo =
                        DesktopFileWorkspaceRepository(DesktopAtomicDraftStore(root) { if (armed && it == stage) error("File checkpoint") })
                    val session = repo.openOrCreateWorkspace()
                    val op = operation(session)
                    armed = true
                    assertFails { repo.retainDraft(session, session.workspace, op) }
                    armed = false
                    val reopened = DesktopFileWorkspaceRepository(root)
                    reopened.retainDraft(session, session.workspace, op)
                    val after = reopened.refresh(session)
                    assertEquals((op.applyTo(session.workspace) as OperationResult.Applied).workspace, after.workspace)
                    assertEquals(1, reopened.listRecentActivity(after).size)
                    assertEquals(SubmitOutcome.Accepted(1, 0), reopened.submit(session, op))
                    val child = op.operations.first() as CreateObjectsOperation
                    val changed =
                        child.copy(
                            objects =
                                child.objects.map {
                                    if (it is GroupFrame &&
                                        it.parentId == null
                                    ) {
                                        it.copy(title = "Different")
                                    } else {
                                        it
                                    }
                                },
                        )
                    assertFails {
                        reopened.retainDraft(
                            session,
                            session.workspace,
                            op.copy(operations = listOf(changed) + op.operations.drop(1)),
                        )
                    }
                    assertEquals(after, reopened.refresh(after))
                } finally {
                    remove(root)
                }
            }
        }

    @Test fun flatSixteenLevelBlocksSaveWithinExistingDepthGuardAndCorruptBindingsDoNotReset() =
        runTest {
            val root = Files.createTempDirectory("boarderless-sequence-deep")
            try {
                val repo = DesktopFileWorkspaceRepository(root)
                val session = repo.openOrCreateWorkspace()
                val nested =
                    assertIs<SequenceParseResult.Parsed>(
                        MermaidSequenceAdapter.parse(
                            "sequenceDiagram\n" + "loop retry\n".repeat(16) + "A->>B: call\n" + "end\n".repeat(16),
                        ),
                    ).draft
                val op = operation(session, nested)
                repo.retainDraft(session, session.workspace, op)
                val loaded = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
                assertEquals(
                    nested,
                    loaded.workspace.sequenceDiagrams.values
                        .single()
                        .toDraft(),
                )
                val files = DesktopAtomicDraftStore(root)
                val key = files.recordKeys().single()
                val current = assertNotNull(files.read(key))
                val content = assertNotNull(current.payload).decodeToString()
                requireBoundedDraftJsonDepth(content)
                val raw = Json.parseToJsonElement(content).jsonObject
                val boards = raw.getValue("boards").jsonArray
                val board = boards[0].jsonObject
                val workspace = board.getValue("workspace").jsonObject
                val corruptWorkspace = JsonObject(workspace + ("objects" to JsonArray(emptyList())))
                val corrupt =
                    JsonObject(
                        raw + ("boards" to JsonArray(listOf(JsonObject(board + ("workspace" to corruptWorkspace))))),
                    ).toString().encodeToByteArray()
                files.compareAndSet(key, current.generation, corrupt)
                assertFails { DesktopFileWorkspaceRepository(root).openOrCreateWorkspace() }
                assertContentEquals(corrupt, assertNotNull(files.read(key)?.payload))
            } finally {
                remove(root)
            }
        }
}
