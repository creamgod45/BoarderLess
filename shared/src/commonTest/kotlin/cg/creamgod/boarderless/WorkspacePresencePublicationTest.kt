package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspacePresencePublicationTest {
    private val a = CanvasObjectId("a")
    private val b = CanvasObjectId("b")
    private val empty = WorkspacePresenceIntent(null, emptySet())
    private fun intent(x: Float, ids: Set<CanvasObjectId> = emptySet()) = WorkspacePresenceIntent(Vec2(x, 0f), ids)
    private class Publisher : WorkspacePresencePublisher {
        val updates = mutableListOf<WorkspacePresenceUpdate>()
        var closes = 0
        var send: suspend (WorkspacePresenceUpdate) -> Unit = { updates.add(it) }
        override suspend fun publish(update: WorkspacePresenceUpdate) = send(update)
        override fun close() { closes++ }
    }

    @Test fun normalizesWorldCoordinatesAndOnlyBoundedAuthoritativeIdsWithoutMutatingInputs() {
        val nodes = (0..139).map {
            TextNode(CanvasObjectId("id-${it.toString().padStart(3, '0')}"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "PRIVATE")
        }
        val workspace = Workspace(WorkspaceId("w"), "PRIVATE", objects = nodes.associateBy { it.id })
        val session = WorkspaceSession("user", "client", WorkspaceMemberRole.Viewer, 1, 2, workspace)
        val selected = (nodes.reversed().map { it.id } + CanvasObjectId("optimistic-only")).toMutableSet()
        val value = workspacePresenceIntent(session, Viewport(Vec2(10f, -10f), 2f), Vec2(50f, 30f), selected, true)
        assertEquals(Vec2(20f, 20f), value.cursor)
        assertEquals(nodes.take(128).map { it.id }.toSet(), value.selectedIds)
        selected.clear()
        assertEquals(128, value.selectedIds.size)
        assertEquals(140, workspace.objects.size)
        assertEquals(empty, workspacePresenceIntent(session, Viewport(), Vec2.Zero, value.selectedIds, false))
        assertNull(workspacePresenceIntent(session, Viewport(zoom = Float.MIN_VALUE), Vec2(Float.MAX_VALUE, 0f), emptySet(), true).cursor)
        assertEquals(Vec2(10f, 20f), workspacePresenceIntent(session, Viewport(Vec2(30f, -10f), 2f), Vec2(50f, 30f), emptySet(), true).cursor)
    }

    @Test fun coalescesLatestCursorAndThrottlesSelectionWithMonotonicSequence() = runTest {
        val publisher = Publisher()
        var latest: WorkspacePresenceIntent? = intent(1f, setOf(a))
        val times = mutableListOf<Long>()
        publisher.send = { times.add(currentTime); publisher.updates.add(it) }
        val job = launch { publishWorkspacePresence(publisher, { latest }, { currentTime }) }
        runCurrent()
        advanceTimeBy(10); latest = intent(2f, setOf(a))
        advanceTimeBy(10); latest = intent(3f, setOf(a))
        advanceTimeBy(80); runCurrent()
        assertEquals(listOf(1f, 3f), publisher.updates.map { it.cursor!!.x })
        latest = intent(4f, setOf(b))
        advanceTimeBy(100); runCurrent()
        assertEquals(2, publisher.updates.size)
        latest = intent(5f, setOf(b))
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf(0L, 100L, 300L), times)
        assertEquals(setOf(b), publisher.updates.last().selectedIds)
        assertEquals(listOf(1L, 2L, 3L), publisher.updates.map { it.presenceSeq })
        latest = null
        advanceTimeBy(100); job.join()
        assertEquals(1, publisher.closes)
    }

    @Test fun disabledInitiallyIsSilentAndDisablingClearsOnceButScopeLossDoesNotPublish() = runTest {
        val publisher = Publisher()
        var latest: WorkspacePresenceIntent? = empty
        val job = launch { publishWorkspacePresence(publisher, { latest }, { currentTime }) }
        advanceTimeBy(500); runCurrent()
        assertTrue(publisher.updates.isEmpty())
        latest = intent(1f, setOf(a))
        advanceTimeBy(100); runCurrent()
        latest = empty
        advanceTimeBy(400); runCurrent()
        assertEquals(2, publisher.updates.size)
        assertNull(publisher.updates.last().cursor)
        assertTrue(publisher.updates.last().selectedIds.isEmpty())
        advanceTimeBy(500); runCurrent()
        assertEquals(2, publisher.updates.size)
        latest = intent(2f, setOf(b))
        advanceTimeBy(100); runCurrent()
        latest = null // New account/room/role/epoch or an expired trusted lease.
        advanceTimeBy(100); job.join()
        assertEquals(3, publisher.updates.size)
        assertEquals(1, publisher.closes)
    }

    @Test fun slowPublisherHasNoBacklogAndUsesLatestAfterBackpressure() = runTest {
        val publisher = Publisher()
        var latest: WorkspacePresenceIntent? = intent(1f)
        var active = 0
        var maximumActive = 0
        val times = mutableListOf<Long>()
        publisher.send = {
            active++; maximumActive = maxOf(maximumActive, active)
            times.add(currentTime)
            delay(350)
            publisher.updates.add(it)
            active--
        }
        val job = launch { publishWorkspacePresence(publisher, { latest }, { currentTime }) }
        runCurrent()
        repeat(10) { index -> advanceTimeBy(20); latest = intent((index + 2).toFloat()) }
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(0L, 450L), times)
        advanceTimeBy(350); runCurrent()
        assertEquals(listOf(1f, 11f), publisher.updates.map { it.cursor!!.x })
        assertEquals(1, maximumActive)
        latest = null
        advanceTimeBy(100); job.join()
        assertEquals(1, publisher.closes)
    }

    @Test fun cancelledReaderCannotPublishAndNonCooperativeSendCannotStartNextUpdate() = runTest {
        val before = Publisher()
        val first = launch {
            val context = currentCoroutineContext()
            publishWorkspacePresence(before, { context.cancel(); intent(1f) }, { currentTime })
        }
        runCurrent(); first.join()
        assertTrue(before.updates.isEmpty())
        assertEquals(1, before.closes)
        // Cancellation occurring inside a transport callback cannot be ignored by the loop.
        val publisher = Publisher()
        publisher.send = { currentCoroutineContext().cancel(); publisher.updates.add(it) }
        val job = launch { publishWorkspacePresence(publisher, { intent(1f) }, { currentTime }) }
        runCurrent(); job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, publisher.updates.size)
        assertEquals(1, publisher.closes)
    }

    @Test fun failureIsNotRetriedAndCleanupCannotReplaceOriginalFailure() = runTest {
        var sends = 0
        var closes = 0
        val error = IllegalStateException("send failed")
        val publisher = object : WorkspacePresencePublisher {
            override suspend fun publish(update: WorkspacePresenceUpdate) { sends++; throw error }
            override fun close() { closes++; throw IllegalArgumentException("cleanup failed") }
        }
        val caught = assertFailsWith<IllegalStateException> {
            publishWorkspacePresence(publisher, { intent(1f) }, { currentTime })
        }
        assertSame(error, caught)
        assertEquals(1, sends)
        assertEquals(1, closes)
        val cancelled = Publisher()
        assertFailsWith<CancellationException> {
            publishWorkspacePresence(cancelled, { throw CancellationException("scope cancelled") }, { currentTime })
        }
        assertTrue(cancelled.updates.isEmpty())
        assertEquals(1, cancelled.closes)
    }

    @Test fun liveOwnerGateRejectsAccountRoleSubscriptionRoomAndLeaseChangesBeforeCancellation() {
        val session = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 1, 2, Workspace(WorkspaceId("w"), "W"))
        val presence = assertNotNull(acceptWorkspacePresence(session, "subscription", WorkspacePresenceState(),
            WorkspacePresenceSnapshot(session.workspace.id, session.userId, session.clientId, "subscription", "room", 1, emptyList(), ttlMs = 1_000), 100))
        fun live(current: WorkspaceSession? = session, currentSubscription: String = "subscription",
            room: String = "room", now: Long = 100, state: WorkspacePresenceState = presence) =
            presencePublicationIsLive(session, current, "subscription", currentSubscription, room, state, now)
        assertTrue(live())
        assertTrue(live(session.copy(workspaceVersion = 2, lastServerSeq = 3)))
        listOf(session.copy(userId = "other"), session.copy(clientId = "other"),
            session.copy(workspace = Workspace(WorkspaceId("other"), "Other")),
            session.copy(role = WorkspaceMemberRole.Viewer), null).forEach { assertFalse(live(it)) }
        assertFalse(live(currentSubscription = "returned-new-subscription"))
        assertFalse(live(room = "new-room"))
        assertFalse(live(room = ""))
        assertFalse(live(now = 1_100))
        assertFalse(live(state = presence.copy(connected = false)))
    }
}
