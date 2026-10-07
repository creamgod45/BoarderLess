package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class WorkspacePresenceTest {
    private val session = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 3, 8,
        Workspace(WorkspaceId("w"), "Shared"))
    private val peer = WorkspacePresencePeer("remote", "other", "other-device", "Other", Vec2(20f, -30f))
    private val snapshot = WorkspacePresenceSnapshot(session.workspace.id, session.userId, session.clientId,
        "subscription", "room", 1, listOf(peer), ttlMs = 1_000)

    private fun accept(value: WorkspacePresenceSnapshot = snapshot, previous: WorkspacePresenceState = WorkspacePresenceState(),
        now: Long = 100) = acceptWorkspacePresence(session, "subscription", previous, value, now)

    @Test fun filtersOnlyExactLocalConnectionAndDoesNotChangeWorkspace() {
        val own = peer.copy(connectionId = "own", userId = session.userId, clientId = session.clientId)
        val otherDevice = own.copy(connectionId = "second", clientId = "second-device")
        val state = assertNotNull(accept(snapshot.copy(peers = listOf(own, otherDevice, peer))))
        assertEquals(listOf(otherDevice, peer), state.peers)
        assertTrue(state.connected)
        assertEquals(8L, session.lastServerSeq)
        assertEquals(3L, session.workspaceVersion)
        assertTrue(session.workspace.objects.isEmpty())
    }

    @Test fun identityRoleAndSubscriptionChangesHideOldPeersImmediately() {
        val state = assertNotNull(accept())
        val changed = listOf(session.copy(userId = "next"), session.copy(clientId = "next"),
            session.copy(workspace = Workspace(WorkspaceId("next"), "Next")),
            session.copy(role = WorkspaceMemberRole.Viewer))
        (changed + listOf(null)).forEach {
            val visible = state.visibleFor(it, "subscription", 100)
            assertFalse(visible.connected)
            assertTrue(visible.peers.isEmpty())
        }
        assertFalse(state.visibleFor(session, "new-subscription", 100).connected)
        assertTrue(state.visibleFor(session.copy(lastServerSeq = 9), "subscription", 100).connected)
        listOf(snapshot.copy(workspaceId = WorkspaceId("wrong")), snapshot.copy(observerUserId = "wrong"),
            snapshot.copy(observerClientId = "wrong"), snapshot.copy(subscriptionId = "wrong")).forEach {
            assertNull(accept(it, state))
        }
    }

    @Test fun trustedLeaseRenewsUnchangedRoomButDuplicateOrChangedPayloadDoesNot() {
        val first = assertNotNull(accept())
        assertNull(accept(snapshot, first, 900))
        val renewed = assertNotNull(accept(snapshot.copy(leaseSequence = 2), first, 900))
        assertEquals(1L, renewed.roomRevision)
        assertEquals(1_900L, renewed.expiresAtMs)
        assertNull(accept(snapshot.copy(leaseSequence = 3, peers = listOf(peer.copy(displayName = "Changed"))), renewed))
        assertNull(accept(snapshot.copy(roomRevision = 0, leaseSequence = 3), renewed))
        assertNull(accept(snapshot.copy(roomRevision = 2, leaseSequence = 2), renewed))
        assertNotNull(accept(snapshot.copy(roomRevision = 2, leaseSequence = 3, peers = emptyList()), renewed))
    }

    @Test fun expiryClearsDisplayAndNeedsNewAuthenticatedLivenessToReturn() {
        val state = assertNotNull(accept())
        assertTrue(state.expire(1_099).connected)
        val expired = state.expire(1_100)
        assertFalse(expired.connected)
        assertTrue(expired.peers.isEmpty())
        assertTrue(state.visibleFor(session, "subscription", 1_100).peers.isEmpty())
        assertNull(accept(snapshot, expired, 1_200))
        val revived = assertNotNull(accept(snapshot.copy(leaseSequence = 2), expired, 1_200))
        assertEquals(listOf(peer), revived.peers)
    }

    @Test fun retiredEpochCannotReturnButNewSubscriptionStartsFresh() {
        val first = assertNotNull(accept())
        val second = assertNotNull(accept(snapshot.copy(roomEpoch = "new", roomRevision = 0, leaseSequence = 0), first))
        assertNull(accept(snapshot.copy(roomRevision = 10, leaseSequence = 10), second))
        val restarted = assertNotNull(acceptWorkspacePresence(session, "next", second,
            snapshot.copy(subscriptionId = "next"), 100))
        assertEquals(setOf("room"), restarted.seenRooms)
        assertEquals("next", restarted.subscriptionId)
    }

    @Test fun freezesPeerPayloadAndRejectsMalformedOrUnboundedSnapshots() {
        val selected = mutableSetOf(CanvasObjectId("n"))
        val peers = mutableListOf(peer.copy(selectedIds = selected))
        val state = assertNotNull(accept(snapshot.copy(peers = peers)))
        selected.clear()
        peers.clear()
        assertEquals(setOf(CanvasObjectId("n")), state.peers.single().selectedIds)
        assertEquals(state.peers, state.snapshotPeers)
        val bad = listOf(snapshot.copy(ttlMs = 999), snapshot.copy(ttlMs = 120_001),
            snapshot.copy(leaseSequence = -1), snapshot.copy(roomRevision = 9_007_199_254_740_992),
            snapshot.copy(roomEpoch = " "), snapshot.copy(peers = listOf(peer, peer)),
            snapshot.copy(peers = listOf(peer.copy(displayName = "unsafe\nname"))),
            snapshot.copy(peers = listOf(peer.copy(cursor = Vec2(10_000_001f, 0f)))),
            snapshot.copy(peers = listOf(peer.copy(selectedIds = (1..201).map { CanvasObjectId("n$it") }.toSet()))),
            snapshot.copy(peers = (1..257).map { peer.copy(connectionId = "c$it") }))
        bad.forEach { assertNull(accept(it)) }
        assertNull(accept(now = -1))
        assertNull(accept(now = Long.MAX_VALUE))
    }

    @Test fun cancelledObserverCannotStartOrPublishNonCooperativeSnapshot() = runTest {
        var started = false
        var published = false
        launch {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> {
                collectActiveWorkspacePresence({ started = true; flow { emit(snapshot) } }) { published = true }
            }
        }.join()
        assertFalse(started || published)
        launch {
            assertFailsWith<CancellationException> {
                collectActiveWorkspacePresence({
                    flow { currentCoroutineContext().cancel(); emit(snapshot) }
                }) { published = true }
            }
        }.join()
        assertFalse(published)
    }

    @Test fun absentStreamDoesNotFabricatePeersAndFactoryFailuresRemainFailures() = runTest {
        var published = false
        collectActiveWorkspacePresence({ null }) { published = true }
        assertFalse(published)
        val failure = IllegalStateException("Unavailable")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            collectActiveWorkspacePresence({ throw failure }) { published = true }
        })
        collectActiveWorkspacePresence({ flow { emit(snapshot) } }) { assertEquals(snapshot, it); published = true }
        assertTrue(published)
    }
}
