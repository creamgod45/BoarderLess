package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.VideoAudioRouteMonitor
import cg.creamgod.boarderless.data.VideoAudioRouteSource
import kotlin.test.*

class VideoAudioRouteMonitorTest {
    @Test fun monitorIsIdleUntilAudibleLeaseStartsAndStopsExactlyOnce() {
        val source = Source()
        val monitor = VideoAudioRouteMonitor(source)
        var interruptions = 0
        monitor.stop()
        assertEquals(0, source.subscriptions)
        monitor.start { interruptions++ }
        assertEquals(1, source.subscriptions)
        source.callbacks[0]()
        assertEquals(1, interruptions)
        monitor.stop()
        monitor.stop()
        assertEquals(1, source.closes)
        source.callbacks[0]()
        assertEquals(1, interruptions)
    }

    @Test fun oldReceiverCannotInterruptNewAudibleLease() {
        val source = Source()
        val monitor = VideoAudioRouteMonitor(source)
        var oldInterruptions = 0
        var currentInterruptions = 0
        monitor.start { oldInterruptions++ }
        monitor.start { currentInterruptions++ }
        assertEquals(1, source.closes)
        source.callbacks[0]()
        assertEquals(0, oldInterruptions)
        assertEquals(0, currentInterruptions)
        source.callbacks[1]()
        assertEquals(1, currentInterruptions)
        monitor.stop()
        assertEquals(2, source.closes)
    }

    @Test fun failedCloseInvalidatesCallbackAndDoesNotCloseTwice() {
        val source = Source().apply { closeError = IllegalStateException("Unregister failed") }
        val monitor = VideoAudioRouteMonitor(source)
        var interruptions = 0
        monitor.start { interruptions++ }
        assertFailsWith<IllegalStateException> { monitor.stop() }
        source.callbacks[0]()
        assertEquals(0, interruptions)
        monitor.stop()
        assertEquals(1, source.closes)
    }

    @Test fun failedSubscriptionCannotPublishLateCallback() {
        val source = Source().apply { subscribeError = IllegalStateException("Register denied") }
        val monitor = VideoAudioRouteMonitor(source)
        var interruptions = 0
        assertFailsWith<IllegalStateException> { monitor.start { interruptions++ } }
        source.callbacks[0]()
        assertEquals(0, interruptions)
        monitor.stop()
        assertEquals(0, source.closes)
    }

    private class Source : VideoAudioRouteSource {
        val callbacks = mutableListOf<() -> Unit>()
        var subscriptions = 0
        var closes = 0
        var closeError: Exception? = null
        var subscribeError: Exception? = null

        override fun subscribe(onNoisy: () -> Unit): AutoCloseable {
            subscriptions++
            callbacks += onNoisy
            subscribeError?.let { throw it }
            return AutoCloseable {
                closes++
                closeError?.let { throw it }
            }
        }
    }
}
