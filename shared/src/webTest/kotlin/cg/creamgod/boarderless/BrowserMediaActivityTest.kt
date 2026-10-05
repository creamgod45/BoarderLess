package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlin.test.*

class BrowserMediaActivityTest {
    @Test fun visibilityPageCacheFreezeAndDisposalDoNotReactivateOldOwner() {
        val tracker = MediaActivityTracker()
        val owner = "activity-regression"
        browserBindMediaActivity(owner, tracker::setAvailable)
        try {
            browserActivityHidden(false)
            assertTrue(tracker.activity.value.available)
            val initialEpoch = tracker.activity.value.epoch
            browserActivityEvent("blur"); browserActivityEvent("focus")
            assertEquals(initialEpoch, tracker.activity.value.epoch)
            browserActivityHidden(true)
            assertFalse(tracker.activity.value.available)
            browserActivityHidden(false)
            assertEquals(initialEpoch + 1, tracker.activity.value.epoch)
            browserActivityEvent("pagehide")
            assertFalse(tracker.activity.value.available)
            browserActivityHidden(false) // Visibility alone cannot undo pagehide.
            assertFalse(tracker.activity.value.available)
            browserActivityEvent("pageshow")
            assertTrue(tracker.activity.value.available)
            browserActivityEvent("freeze")
            assertFalse(tracker.activity.value.available)
            browserActivityEvent("pageshow")
            assertFalse(tracker.activity.value.available)
            browserActivityEvent("resume")
            assertTrue(tracker.activity.value.available)
            assertEquals(initialEpoch + 3, tracker.activity.value.epoch)
            browserUnbindMediaActivity(owner)
            browserUnbindMediaActivity(owner)
            browserActivityEvent("pagehide")
            assertTrue(tracker.activity.value.available, "Listener must have been removed")
            tracker.close()
            browserActivityEvent("pageshow")
            assertFalse(tracker.activity.value.available)
        } finally {
            browserUnbindMediaActivity(owner)
            tracker.close()
            browserActivityRestoreVisibility()
        }
    }
}

internal expect fun browserActivityEvent(type: String)
internal expect fun browserActivityHidden(hidden: Boolean)
internal expect fun browserActivityRestoreVisibility()
