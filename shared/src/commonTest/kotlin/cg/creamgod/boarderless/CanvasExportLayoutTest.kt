package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.test.*

class CanvasExportLayoutTest {
    @Test fun measuredOutsideTextAndRouteIncludedWithoutUsingVisibleViewport() {
        val layout = canvasExportLayout(listOf(
            CanvasPaintBounds(100f, 100f, 300f, 200f),
            CanvasPaintBounds(-80f, -50f, 20f, 75f),
            CanvasPaintBounds(250f, 90f, 490f, 320f),
        ), margin = 10f, scale = 2f)
        assertEquals(CanvasPaintBounds(-90f, -60f, 500f, 330f), layout.worldBounds)
        assertEquals(1180, layout.pixelWidth); assertEquals(780, layout.pixelHeight)
        assertEquals(Vec2.Zero, layout.viewport.worldToScreen(Vec2(-90f, -60f)))
        assertEquals(Vec2(1180f, 780f), layout.viewport.worldToScreen(Vec2(500f, 330f)))
    }
    @Test fun desktopSupportsLargeCanvasAtOneAndTwoTimesWithoutCropping() {
        val bounds = listOf(CanvasPaintBounds(0f, 0f, 1500f, 2300f))
        for (scale in listOf(1f, 2f)) {
            val layout = canvasExportLayout(bounds, margin = 0f, scale = scale,
                maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels)
            assertEquals((1500 * scale).toInt(), layout.pixelWidth)
            assertEquals((2300 * scale).toInt(), layout.pixelHeight)
            assertEquals(Vec2(layout.pixelWidth.toFloat(), layout.pixelHeight.toFloat()),
                layout.viewport.worldToScreen(Vec2(1500f, 2300f)))
        }
        val wide = canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 10000f, 1f)), margin = 0f,
            maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels)
        assertEquals(10000, wide.pixelWidth)
        val large = canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 5000f, 5000f)), margin = 0f,
            maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels)
        assertEquals(25_000_000L, large.pixelWidth.toLong() * large.pixelHeight)
        assertFails { canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 100000f, 100000f)), margin = 0f,
            maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels) }
    }
    @Test fun limitsRejectRatherThanSilentlyCropOrOmitContent() {
        assertFails { canvasExportLayout(emptyList()) }
        assertFails { CanvasPaintBounds(Float.NaN, 0f, 1f, 1f) }
        assertFails { CanvasPaintBounds(2f, 0f, 1f, 1f) }
        assertFails { canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 5000f, 10f)), margin = 0f) }
        assertFails { canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 3000f, 3000f)), margin = 0f) }
        assertFails { canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 1f, 1f)), scale = 0f) }
        val layout = canvasExportLayout(listOf(CanvasPaintBounds(0f, 0f, 0f, 0f)), margin = 0f)
        assertEquals(1, layout.pixelWidth); assertEquals(1, layout.pixelHeight)
    }
}
