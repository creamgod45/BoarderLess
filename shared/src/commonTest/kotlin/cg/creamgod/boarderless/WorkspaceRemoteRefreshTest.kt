package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WorkspaceRemoteRefreshTest {
    private val opened =
        WorkspaceSession(
            "user",
            "client",
            WorkspaceMemberRole.Editor,
            3,
            8,
            Workspace(WorkspaceId("w"), "Shared"),
        )
    private val newer = opened.copy(workspaceVersion = 4, lastServerSeq = 12)

    @Test fun sameOwnerMonotonicRefreshAndMetadataChangesCanBeApplied() {
        assertTrue(shouldApplyRemoteRefresh(opened, opened, newer))
        assertTrue(shouldApplyRemoteRefresh(opened, opened, opened.copy(role = WorkspaceMemberRole.Viewer)))
        assertTrue(shouldApplyRemoteRefresh(opened, opened, opened.copy(workspace = opened.workspace.copy(title = "Renamed"))))
        assertFalse(shouldApplyRemoteRefresh(opened, opened, opened))
    }

    @Test fun advancedCheckpointAndChangedIdentityNeverAdoptLateRefresh() {
        val changed =
            listOf(
                opened.copy(userId = "other"),
                opened.copy(clientId = "other"),
                opened.copy(workspace = Workspace(WorkspaceId("other"), "Other")),
                opened.copy(workspaceVersion = 4),
                opened.copy(lastServerSeq = 9),
                opened.copy(role = WorkspaceMemberRole.Viewer),
                opened.copy(workspace = opened.workspace.copy(title = "Renamed locally")),
            )
        changed.forEach { assertFalse(shouldApplyRemoteRefresh(opened, it, newer)) }
        changed.take(3).forEach {
            assertFalse(
                shouldApplyRemoteRefresh(
                    opened,
                    opened,
                    newer.copy(
                        userId = it.userId,
                        clientId = it.clientId,
                        workspace = it.workspace,
                    ),
                ),
            )
        }
    }

    @Test fun roleOrTitleChangesCannotSmuggleRegressedSequenceOrVersion() {
        for (snapshot in listOf(opened.copy(lastServerSeq = 7), opened.copy(workspaceVersion = 2))) {
            assertFalse(snapshot.copy(role = WorkspaceMemberRole.Viewer).hasRemoteChangesComparedTo(opened))
            assertFalse(snapshot.copy(workspace = opened.workspace.copy(title = "Changed")).hasRemoteChangesComparedTo(opened))
        }
        assertFalse(newer.copy(lastServerSeq = 7).hasRemoteChangesComparedTo(opened))
    }

    @Test fun nonCooperativeReturnOrErrorAfterCancellationCannotPublishState() =
        runTest {
            for (throws in listOf(false, true)) {
                var published = false
                var failed = false
                var cancelled = false
                launch {
                    try {
                        awaitActiveWorkspaceRefresh {
                            currentCoroutineContext().cancel()
                            if (throws) error("Late transport failure")
                            newer
                        }
                        published = true
                    } catch (_: CancellationException) {
                        cancelled = true
                    } catch (_: Exception) {
                        failed = true
                    }
                }.join()
                assertTrue(cancelled)
                assertFalse(published || failed)
            }
        }

    @Test fun alreadyCancelledRefreshNeverStartsAndActiveErrorsStayOriginal() =
        runTest {
            var started = false
            launch {
                currentCoroutineContext().cancel()
                assertFailsWith<CancellationException> {
                    awaitActiveWorkspaceRefresh {
                        started = true
                        newer
                    }
                }
            }.join()
            assertFalse(started)
            val failure = IllegalStateException("Unavailable")
            assertSame(failure, assertFailsWith<IllegalStateException> { awaitActiveWorkspaceRefresh { throw failure } })
            assertEquals(newer, awaitActiveWorkspaceRefresh { newer })
        }
}
