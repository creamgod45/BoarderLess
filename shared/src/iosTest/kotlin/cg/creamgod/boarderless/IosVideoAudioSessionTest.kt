package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.IosVideoAudioSession
import cg.creamgod.boarderless.data.IosVideoAudioSessionPort
import kotlin.test.*

class IosVideoAudioSessionTest {
    @Test fun sameOwnerDoesNotReactivateAndReleaseIsIdempotent() {
        val port = Port()
        val session = IosVideoAudioSession(port)
        val owner = Any()
        session.release(owner)
        assertTrue(session.acquire(owner) { fail("Not an interruption") })
        assertTrue(session.acquire(owner) { fail("Not an interruption") })
        assertEquals(1, port.activations)
        session.release(owner)
        session.release(owner)
        assertEquals(1, port.deactivations)
        port.callbacks.single()()
        assertEquals(1, port.deactivations)
    }

    @Test fun newOwnerPausesOldBeforeDeactivationAndOldReleaseCannotCloseNewOwner() {
        val port = Port()
        val session = IosVideoAudioSession(port)
        val first = Any()
        val second = Any()
        var interrupted = 0
        assertTrue(
            session.acquire(first) {
                interrupted++
                port.events += "pause"
            },
        )
        assertTrue(session.acquire(second) { interrupted++ })
        assertEquals(listOf("activate", "pause", "deactivate", "activate"), port.events)
        assertEquals(1, interrupted)
        session.release(first)
        port.callbacks.first()()
        assertEquals(1, port.deactivations)
        assertEquals(1, interrupted)
        session.release(second)
        assertEquals(2, port.deactivations)
    }

    @Test fun nativeLossIsOnceAndRequiresExplicitNewAcquisition() {
        val port = Port()
        val session = IosVideoAudioSession(port)
        val owner = Any()
        var losses = 0
        session.acquire(owner) { losses++ }
        val previous = port.callbacks.single()
        previous()
        previous()
        assertEquals(1, losses)
        assertEquals(1, port.deactivations)
        assertEquals(1, port.activations)
        assertTrue(session.acquire(owner) { losses++ })
        previous()
        assertEquals(1, losses)
        port.callbacks.last()()
        assertEquals(2, losses)
        assertEquals(2, port.deactivations)
    }

    @Test fun deniedActivationCleansPartialPortAndAllowsExplicitRetry() {
        val port = Port().apply { granted = false }
        val session = IosVideoAudioSession(port)
        val owner = Any()
        assertFalse(session.acquire(owner) { fail("Denial is not a notification") })
        assertEquals(1, port.deactivations)
        port.callbacks.first()()
        port.granted = true
        assertTrue(session.acquire(owner) {})
        assertEquals(2, port.activations)
        session.release(owner)
    }

    @Test fun activationFailurePreservesErrorAndInvalidatesCallbackEvenWhenCleanupFails() {
        val original = IllegalStateException("activate")
        val cleanup = IllegalArgumentException("deactivate")
        val port =
            Port().apply {
                activationError = original
                deactivationError = cleanup
            }
        val session = IosVideoAudioSession(port)
        val owner = Any()
        var losses = 0
        val thrown = assertFailsWith<IllegalStateException> { session.acquire(owner) { losses++ } }
        assertSame(original, thrown)
        assertEquals(listOf(cleanup), thrown.suppressedExceptions)
        port.callbacks.single()()
        session.release(owner)
        assertEquals(0, losses)
        assertEquals(1, port.deactivations)
    }

    @Test fun lossCallbackFailureStillDeactivatesAndMakesOldLeaseTerminal() {
        val port = Port()
        val session = IosVideoAudioSession(port)
        val first = Any()
        val second = Any()
        session.acquire(first) { error("pause") }
        assertFailsWith<IllegalStateException> { session.acquire(second) {} }
        assertEquals(1, port.deactivations)
        assertEquals(1, port.activations)
        session.release(first)
        assertTrue(session.acquire(second) {})
        session.release(second)
    }

    private class Port : IosVideoAudioSessionPort {
        var granted = true
        var activations = 0
        var deactivations = 0
        var activationError: Exception? = null
        var deactivationError: Exception? = null
        val callbacks = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()

        override fun activate(onLoss: () -> Unit): Boolean {
            activations++
            events += "activate"
            callbacks += onLoss
            activationError?.let { throw it }
            return granted
        }

        override fun deactivate() {
            deactivations++
            events += "deactivate"
            deactivationError?.let { throw it }
        }
    }
}
