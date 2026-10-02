package cg.creamgod.boarderless

import kotlin.test.*

class DesktopMediaWindowLifecycleTest {
    @Test fun minimizeRequiresNewActivationEvenWhenRestoredBeforeCollectorRuns() {
        val lifecycle = DesktopMediaWindowLifecycle()
        assertFalse(lifecycle.activity.value.available)
        lifecycle.setAvailable(true)
        val before = lifecycle.activity.value
        lifecycle.setAvailable(false)
        lifecycle.setAvailable(true)
        assertTrue(lifecycle.activity.value.available)
        assertEquals(before.epoch + 1, lifecycle.activity.value.epoch)
        assertNotEquals(before, lifecycle.activity.value)
    }

    @Test fun duplicateEventsDoNotResetActiveWindowAndCloseIsTerminal() {
        val lifecycle = DesktopMediaWindowLifecycle()
        lifecycle.setAvailable(true)
        val active = lifecycle.activity.value
        lifecycle.setAvailable(true)
        assertEquals(active, lifecycle.activity.value)
        lifecycle.setAvailable(false)
        val hidden = lifecycle.activity.value
        lifecycle.setAvailable(false)
        assertEquals(hidden, lifecycle.activity.value)
        lifecycle.setAvailable(true)
        lifecycle.close()
        val closed = lifecycle.activity.value
        assertFalse(closed.available)
        lifecycle.close()
        lifecycle.setAvailable(true)
        assertEquals(closed, lifecycle.activity.value)
    }

    @Test fun closingAWindowThatOnlyHidesCanBeShownAgain() {
        val lifecycle = DesktopMediaWindowLifecycle()
        lifecycle.setAvailable(true)
        lifecycle.closeRequested(hidesWindow = true)
        assertFalse(lifecycle.activity.value.available)
        lifecycle.setAvailable(true)
        assertTrue(lifecycle.activity.value.available)

        lifecycle.closeRequested(hidesWindow = false)
        lifecycle.setAvailable(true)
        assertFalse(lifecycle.activity.value.available)
    }
}
