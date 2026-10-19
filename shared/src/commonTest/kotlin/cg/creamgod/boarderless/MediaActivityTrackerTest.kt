package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Vec2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MediaActivityTrackerTest {
    @Test fun startsUnavailableAndQuickHideShowRetainsAnEpochChange() {
        val tracker = MediaActivityTracker()
        assertEquals(MediaPlaybackActivity(false, 0), tracker.activity.value)
        assertFalse(mediaActivityAllowsPublication(tracker.activity.value, 0))
        tracker.setAvailable(true)
        assertEquals(MediaPlaybackActivity(true, 0), tracker.activity.value)
        assertTrue(mediaActivityAllowsPublication(tracker.activity.value, 0))
        tracker.setAvailable(true)
        tracker.setAvailable(false)
        tracker.setAvailable(false)
        tracker.setAvailable(true)
        assertEquals(MediaPlaybackActivity(true, 1), tracker.activity.value)
        assertFalse(mediaActivityAllowsPublication(tracker.activity.value, 0))
        tracker.setAvailable(false)
        tracker.setAvailable(true)
        assertEquals(MediaPlaybackActivity(true, 2), tracker.activity.value)
    }

    @Test fun closeIsTerminalAndIdempotentSoLateCallbacksCannotReactivate() {
        val tracker = MediaActivityTracker()
        tracker.setAvailable(true)
        tracker.close()
        assertEquals(MediaPlaybackActivity(false, 1), tracker.activity.value)
        tracker.setAvailable(true)
        tracker.close()
        assertEquals(MediaPlaybackActivity(false, 1), tracker.activity.value)
    }

    @Test fun publicationOwnerCannotResumeAfterConflatedHideShowOrDisposal() =
        runTest {
            val tracker = MediaActivityTracker()
            tracker.setAvailable(true)
            val openedEpoch = tracker.activity.value.epoch
            var closes = 0
            val updates = mutableListOf<WorkspacePresenceUpdate>()
            val publisher =
                object : WorkspacePresencePublisher {
                    override suspend fun publish(update: WorkspacePresenceUpdate) {
                        updates.add(update)
                    }

                    override fun close() {
                        closes++
                    }
                }
            val job =
                launch {
                    publishWorkspacePresence(publisher, {
                        val state = tracker.activity.value
                        if (mediaActivityAllowsPublication(state, openedEpoch)) WorkspacePresenceIntent(Vec2(1f, 2f), emptySet()) else null
                    }, { currentTime })
                }
            runCurrent()
            tracker.setAvailable(false)
            tracker.setAvailable(true)
            advanceTimeBy(100)
            job.join()
            assertEquals(1, updates.size)
            assertEquals(1, closes)
            tracker.close()
            tracker.setAvailable(true)
            assertFalse(tracker.activity.value.available)
        }
}
