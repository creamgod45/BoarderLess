package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRemoteNotificationsTest {
    private val session =
        WorkspaceSession(
            "user",
            "client",
            WorkspaceMemberRole.Editor,
            2,
            5,
            Workspace(WorkspaceId("w"), "Collaboration"),
        )

    @Test fun absentTransportKeepsThreeSecondRestFallback() =
        runTest {
            val reasons = workspaceCatchUpRequests(session, null).take(2).toList()
            assertEquals(listOf(WorkspaceCatchUpReason.Poll, WorkspaceCatchUpReason.Poll), reasons)
            assertEquals(6000, testScheduler.currentTime)
        }

    @Test fun joinAndAtomicTransactionWakeImmediatelyWithoutAdvancingAuthoritativeCheckpoint() =
        runTest {
            val source =
                flow {
                    emit(WorkspaceRemoteNotification.Joined(session.workspace.id))
                    delay(10)
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 6, 9, 3))
                    awaitCancellation()
                }
            val reasons = workspaceCatchUpRequests(session, source).take(2).toList()
            assertEquals(listOf(WorkspaceCatchUpReason.Reconnected, WorkspaceCatchUpReason.Notification), reasons)
            assertEquals(10, testScheduler.currentTime)
            assertEquals(5, session.lastServerSeq)
            assertEquals(2, session.workspaceVersion)
        }

    @Test fun foreignStaleAndMalformedSignalsCannotTriggerCatchUp() =
        runTest {
            val source =
                flow {
                    emit(WorkspaceRemoteNotification.Joined(WorkspaceId("foreign")))
                    emit(WorkspaceRemoteNotification.MetadataChanged(WorkspaceId("foreign")))
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 1, 5, 2))
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 10, 6, 3))
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 0, 8, 3))
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 6, 8, -1))
                    emit(WorkspaceRemoteNotification.Committed(session.workspace.id, 6, 8, 1))
                    awaitCancellation()
                }
            assertEquals(WorkspaceCatchUpReason.Poll, workspaceCatchUpRequests(session, source).first())
            assertEquals(3000, testScheduler.currentTime)
        }

    @Test fun transportEndOrFailureWakesRefreshThenContinuesPolling() =
        runTest {
            for (source in listOf(emptyFlow(), flow<WorkspaceRemoteNotification> { error("Socket disconnected") })) {
                val reasons = workspaceCatchUpRequests(session, source).take(2).toList()
                assertEquals(listOf(WorkspaceCatchUpReason.TransportEnded, WorkspaceCatchUpReason.Poll), reasons)
            }
        }

    @Test fun burstDuringSlowRefreshCoalescesAndCancelsObserverWhenOwnerLeaves() =
        runTest {
            val firstRefresh = CompletableDeferred<Unit>()
            var observerStopped = false
            val source =
                flow {
                    try {
                        emit(WorkspaceRemoteNotification.Joined(session.workspace.id))
                        firstRefresh.await()
                        repeat(100) {
                            emit(
                                WorkspaceRemoteNotification.Committed(
                                    session.workspace.id,
                                    6 + it.toLong(),
                                    6 + it.toLong(),
                                    3 + it.toLong(),
                                ),
                            )
                        }
                        awaitCancellation()
                    } finally {
                        observerStopped = true
                    }
                }
            val reasons = mutableListOf<WorkspaceCatchUpReason>()
            workspaceCatchUpRequests(session, source).take(2).collect {
                reasons += it
                if (reasons.size == 1) {
                    firstRefresh.complete(Unit)
                    delay(100)
                }
            }
            assertEquals(listOf(WorkspaceCatchUpReason.Reconnected, WorkspaceCatchUpReason.Notification), reasons)
            assertTrue(observerStopped)
            assertEquals(100, testScheduler.currentTime)
        }

    @Test fun invalidPollIntervalFailsWithoutStartingTransport() =
        runTest {
            var started = false
            val source = flow<WorkspaceRemoteNotification> { started = true }
            assertFailsWith<IllegalArgumentException> { workspaceCatchUpRequests(session, source, 0).first() }
            assertFalse(started)
        }
}
