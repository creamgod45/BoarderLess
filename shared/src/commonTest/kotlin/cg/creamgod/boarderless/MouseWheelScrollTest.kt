package cg.creamgod.boarderless

import cg.creamgod.boarderless.designsystem.wheelDeltaToPixels
import kotlin.test.Test
import kotlin.test.assertEquals

class MouseWheelScrollTest {
    @Test
    fun webPixelDeltasScaleByDensity() {
        assertEquals(200f, wheelDeltaToPixels(delta = 100f, density = 2f, lineStepPx = 112f))
        assertEquals(-200f, wheelDeltaToPixels(delta = -100f, density = 2f, lineStepPx = 112f))
    }

    @Test
    fun desktopLineDeltasUseTheLineStep() {
        assertEquals(112f, wheelDeltaToPixels(delta = 1f, density = 2f, lineStepPx = 112f))
        assertEquals(-56f, wheelDeltaToPixels(delta = -0.5f, density = 2f, lineStepPx = 112f))
    }
}
