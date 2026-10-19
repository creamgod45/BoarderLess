package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlin.test.*

class ControllerMediaActivityTest {
    @Test fun appActivationCannotOverrideControllerSceneOrMissingWindow() {
        val tracker = MediaActivityTracker()
        val scoped = ControllerMediaActivity(tracker)
        scoped.update(true, true, true, null)
        assertFalse(tracker.activity.value.available)
        scoped.update(true, true, true, "scene-a")
        assertTrue(tracker.activity.value.available)
        scoped.update(true, true, false, "scene-a")
        assertEquals(MediaPlaybackActivity(false, 1), tracker.activity.value)
        scoped.update(true, true, false, "scene-a") // Another app/scene activation does not grant this gate.
        assertFalse(tracker.activity.value.available)
        scoped.update(true, false, true, "scene-a")
        assertFalse(tracker.activity.value.available)
        scoped.update(false, true, true, "scene-a")
        assertFalse(tracker.activity.value.available)
        scoped.update(true, true, true, "scene-a")
        assertEquals(MediaPlaybackActivity(true, 1), tracker.activity.value)
    }

    @Test fun independentWindowsSceneMovesAndLateCallbacksRetainConsentIsolation() {
        val first = MediaActivityTracker()
        val second = MediaActivityTracker()
        val scopeA = ControllerMediaActivity(first)
        val scopeB = ControllerMediaActivity(second)
        scopeA.update(true, true, true, "a")
        scopeB.update(true, true, true, "b")
        scopeA.update(true, true, false, "a")
        assertFalse(first.activity.value.available)
        assertEquals(MediaPlaybackActivity(true, 0), second.activity.value)
        scopeB.update(true, true, true, "c") // No transient false frame is required to reset consent.
        assertEquals(MediaPlaybackActivity(true, 1), second.activity.value)
        scopeB.update(true, true, true, "c")
        assertEquals(1L, second.activity.value.epoch)
        scopeA.close()
        scopeA.close()
        scopeA.update(true, true, true, "a")
        assertFalse(first.activity.value.available)
        assertTrue(second.activity.value.available)
        scopeB.update(true, true, true, null)
        assertEquals(MediaPlaybackActivity(false, 2), second.activity.value)
        scopeB.close()
    }
}
