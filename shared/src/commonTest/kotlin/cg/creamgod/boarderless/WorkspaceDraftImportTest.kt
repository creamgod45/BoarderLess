package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WorkspaceDraftImportTest {
    private val workspace = Workspace(WorkspaceId("workspace"), "Private 中文")
    private val opened = WorkspaceSession("user", "client", WorkspaceMemberRole.Viewer, 2, 5, workspace)
    private val saved =
        WorkspaceDraftReview(
            "draft",
            1,
            2,
            1,
            2,
            workspace,
            workspace,
            workspace.copy(title = "Historical remote"),
            emptyList(),
            true,
            true,
        )

    @Test fun explicitlyInspectsViewerBackupAgainstFreshRemoteNotSavedAuthority() =
        runTest {
            var reads = 0
            val fresh = opened.copy(workspaceVersion = 3, lastServerSeq = 6, workspace = workspace.copy(title = "Fresh remote"))
            val result =
                inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { true }) {
                    reads++
                    fresh
                }
            assertEquals(1, reads)
            assertEquals(fresh.workspace, result.current)
            assertEquals(3, result.currentVersion)
            assertEquals(6, result.currentServerSeq)
            assertEquals(saved.baseline, result.baseline)
            assertTrue(result.quarantined && result.hasUnconfirmedSubmission)
            assertEquals("Private 中文", opened.workspace.title)
        }

    @Test fun malformedOrClosedInputNeverRequestsAuthority() =
        runTest {
            var reads = 0
            assertFailsWith<IllegalArgumentException> {
                inspectWorkspaceDraftBackup("private invalid", opened, { true }) {
                    reads++
                    opened
                }
            }
            assertFailsWith<IllegalStateException> {
                inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { false }) {
                    reads++
                    opened
                }
            }
            assertEquals(0, reads)
        }

    @Test fun deniesWrongIdentityBackwardSnapshotAccessFailureAndLatePublication() =
        runTest {
            listOf(
                opened.copy(userId = "other"),
                opened.copy(clientId = "other"),
                opened.copy(workspace = workspace.copy(id = WorkspaceId("other"))),
                opened.copy(workspaceVersion = 1),
                opened.copy(lastServerSeq = 4),
            ).forEach { fresh ->
                assertFailsWith<IllegalStateException> {
                    inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { true }) { fresh }
                }
            }
            var active = true
            assertFailsWith<IllegalStateException> {
                inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { active }) {
                    active = false
                    opened
                }
            }
            assertFailsWith<IllegalStateException> {
                inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { true }) { error("Read access denied") }
            }
        }

    @Test fun cancelledRefreshCannotPublishEvenIfItReturnsASnapshot() =
        runTest {
            val job =
                async {
                    inspectWorkspaceDraftBackup(saved.toBackupJson(), opened, { true }) {
                        currentCoroutineContext().cancel()
                        opened
                    }
                }
            assertFailsWith<CancellationException> { job.await() }
        }
}
