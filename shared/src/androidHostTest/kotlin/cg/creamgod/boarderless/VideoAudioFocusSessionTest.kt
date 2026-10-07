package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.VideoAudioFocusPort
import cg.creamgod.boarderless.data.VideoAudioFocusSession
import kotlin.test.*

class VideoAudioFocusSessionTest {
    @Test fun silentPlaybackDoesNotRequestFocusAndReleaseIsIdempotent() {
        val port = Port()
        val session = VideoAudioFocusSession(port) { error("Unexpected interruption") }
        assertTrue(session.update(false))
        assertEquals(0, port.requests)
        session.release(); session.release()
        assertEquals(1, port.abandons)
        assertFailsWith<IllegalStateException> { session.update(true) }
    }

    @Test fun audiblePlaybackRequestsOnceAndMuteOrPauseAbandons() {
        val port = Port()
        val session = VideoAudioFocusSession(port) { }
        assertTrue(session.update(true)); assertTrue(session.update(true))
        assertEquals(1, port.requests)
        session.update(false)
        assertFalse(port.active)
        assertTrue(session.update(true))
        assertEquals(2, port.requests)
        session.release()
        assertFalse(port.active)
    }

    @Test fun denialDoesNotWaitOrAutoResumeButExplicitRetryCanAcquire() {
        val port = Port().apply { granted = false }
        var interruptions = 0
        val session = VideoAudioFocusSession(port) { interruptions++ }
        assertFalse(session.update(true))
        assertFalse(port.active)
        assertEquals(1, port.abandons)
        port.granted = true
        assertEquals(1, port.requests)
        assertEquals(0, interruptions)
        assertTrue(session.update(true))
        assertEquals(2, port.requests)
        session.release()
    }

    @Test fun lossPausesOnceAndRequiresExplicitReacquisition() {
        val port = Port()
        var interruptions = 0
        val session = VideoAudioFocusSession(port) { interruptions++ }
        session.update(true)
        val oldCallback = checkNotNull(port.loss)
        oldCallback(); oldCallback()
        assertEquals(1, interruptions)
        assertFalse(port.active)
        assertEquals(1, port.requests)
        session.update(true)
        assertEquals(2, port.requests)
        oldCallback()
        assertEquals(1, interruptions)
        assertTrue(port.active)
        session.release()
        checkNotNull(port.loss).invoke()
        assertEquals(1, interruptions)
    }

    @Test fun requestExceptionCleansPendingRequestAndReleaseFailureStaysTerminal() {
        val port = Port().apply { requestError = IllegalArgumentException("Request failed") }
        val session = VideoAudioFocusSession(port) { }
        assertFailsWith<IllegalArgumentException> { session.update(true) }
        assertEquals(1, port.abandons)
        port.abandonError = IllegalStateException("Abandon failed")
        assertFailsWith<IllegalStateException> { session.release() }
        val count = port.abandons
        session.release()
        assertEquals(count, port.abandons)
        assertFailsWith<IllegalStateException> { session.update(true) }
    }

    @Test fun abandonFailureDuringLossStillNotifiesOwnerWithoutThrowingIntoSystemCallback() {
        val port = Port()
        var interruptions = 0
        val session = VideoAudioFocusSession(port) { interruptions++ }
        session.update(true)
        port.abandonError = IllegalStateException("Device gone")
        checkNotNull(port.loss).invoke()
        assertEquals(1, interruptions)
        port.abandonError = null
        session.release()
    }

    private class Port : VideoAudioFocusPort {
        var requests = 0; var abandons = 0
        var granted = true; var active = false
        var loss: (() -> Unit)? = null
        var requestError: Exception? = null; var abandonError: Exception? = null
        override fun request(onLoss: () -> Unit): Boolean {
            requests++; loss = onLoss
            requestError?.let { throw it }
            active = granted
            return granted
        }
        override fun abandon() {
            abandons++; active = false
            abandonError?.let { throw it }
        }
    }
}
