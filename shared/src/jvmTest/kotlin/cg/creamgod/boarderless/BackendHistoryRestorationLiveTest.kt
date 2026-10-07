package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.*

/** Opt-in, new QA workspace only. Keeps test artifacts for human inspection. */
class BackendHistoryRestorationLiveTest {
    @Test fun createUndoRedoAndDeleteUndoRedoAgainstRunningBackend() =
        runBlocking {
            assumeTrue(System.getenv("BOARDERLESS_RESTORE_LIVE") == "true")
            val base = requireNotNull(System.getenv("BOARDERLESS_RESTORE_BASE"))
            require(base in setOf("http://localhost:3000", "http://127.0.0.1:3000"))
            val owner = requireNotNull(System.getenv("BOARDERLESS_RESTORE_OWNER"))
            UUID.fromString(owner)
            val preferences = SessionPreferences(InMemorySettings())
            val repo = BackendWorkspaceRepository(base, preferences)
            try {
                withTimeout(60_000) {
                    val seed =
                        WorkspaceSession(
                            owner,
                            preferences.clientId,
                            WorkspaceMemberRole.Owner,
                            0,
                            0,
                            Workspace(WorkspaceId(UUID.randomUUID().toString()), "QA seed (not submitted)"),
                        )
                    var session = repo.createWorkspace(seed, "QA Undo Restore 2026-10-05 ${UUID.randomUUID()}")
                    println("RESTORE_QA workspace=${session.workspace.id.value}")
                    var history = WorkspaceHistory(session.workspace)

                    suspend fun submit(result: HistoryResult) {
                        assertTrue(result.succeeded)
                        val operation = checkNotNull(result.appliedOperation)
                        repo.retainDraft(session, history.workspace, operation)
                        val accepted = assertIs<SubmitOutcome.Accepted>(repo.submit(session, operation))
                        session =
                            repo.refresh(
                                session.copy(
                                    workspace = result.history.workspace,
                                    workspaceVersion = accepted.workspaceVersion,
                                    lastServerSeq = accepted.lastServerSeq,
                                ),
                            )
                        history = result.history.copy(workspace = session.workspace)
                    }
                    val transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(260f, 132f))
                    val group = GroupFrame(CanvasObjectId(UUID.randomUUID().toString()), transform = transform, title = "QA group")
                    val child =
                        TextNode(
                            CanvasObjectId(UUID.randomUUID().toString()),
                            parentId = group.id,
                            transform = transform,
                            text = "QA child",
                        )
                    val peer = child.copy(id = CanvasObjectId(UUID.randomUUID().toString()), parentId = null, text = "QA peer")
                    val relation =
                        Relation(
                            RelationId(UUID.randomUUID().toString()),
                            sourceObjectId = child.id,
                            targetObjectId = peer.id,
                            label = "QA cascade",
                        )
                    submit(
                        history.execute(
                            TransactionOperation(
                                "qa-create",
                                listOf(
                                    CreateObjectsOperation("qa-nodes", listOf(group, child, peer)),
                                    CreateRelationsOperation("qa-relation", listOf(relation)),
                                ),
                            ),
                        ),
                    )
                    submit(history.undo(remoteRestoration = true))
                    assertTrue(listOf(group, child, peer).none { it.id in session.workspace.objects })
                    submit(history.redo(remoteRestoration = true))
                    assertEquals(
                        3L,
                        session.workspace.objects
                            .getValue(child.id)
                            .version,
                    )
                    assertEquals(
                        3L,
                        session.workspace.relations
                            .getValue(relation.id)
                            .version,
                    )
                    submit(
                        history.execute(
                            DeleteObjectsOperation(
                                "qa-delete-group",
                                listOf(session.workspace.objects.getValue(group.id), session.workspace.objects.getValue(child.id)),
                                listOf(session.workspace.relations.getValue(relation.id)),
                            ),
                        ),
                    )
                    submit(history.undo(remoteRestoration = true))
                    assertEquals(
                        5L,
                        session.workspace.objects
                            .getValue(child.id)
                            .version,
                    )
                    assertEquals(
                        5L,
                        session.workspace.relations
                            .getValue(relation.id)
                            .version,
                    )
                    submit(history.redo(remoteRestoration = true))
                    submit(history.undo(remoteRestoration = true))
                    assertEquals(
                        7L,
                        session.workspace.objects
                            .getValue(child.id)
                            .version,
                    )
                    assertEquals(
                        7L,
                        session.workspace.relations
                            .getValue(relation.id)
                            .version,
                    )
                    assertEquals(
                        group.id,
                        session.workspace.objects
                            .getValue(child.id)
                            .parentId,
                    )
                    println(
                        "RESTORE_QA passed createUndoRedo=true groupCascade=true repeatedUndoRedo=true objectVersion=7 relationVersion=7 throughSeq=${session.lastServerSeq}",
                    )
                }
            } finally {
                repo.close()
            }
        }
}
