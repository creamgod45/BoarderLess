package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.CanvasPaintBounds
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.comparisonPreviewViewport
import kotlin.test.*

class WorkspaceComparisonPreviewTest {
    @Test fun aSingleViewportPreservesDisplacementAcrossVersionsAndFitsOutsideTextBounds() {
        val before = CanvasPaintBounds(-100f, -60f, 80f, 40f)
        val local = CanvasPaintBounds(100f, 90f, 480f, 240f)
        val remote = CanvasPaintBounds(800f, -80f, 980f, 40f)
        val viewport = comparisonPreviewViewport(listOf(before, local, remote), 600f, 280f)
        listOf(before, local, remote).forEach { paint ->
            val first = viewport.worldToScreen(Vec2(paint.left, paint.top))
            val last = viewport.worldToScreen(Vec2(paint.right, paint.bottom))
            assertTrue(first.x >= 15.9f && first.y >= 15.9f)
            assertTrue(last.x <= 584.1f && last.y <= 264.1f)
        }
        val source = viewport.worldToScreen(Vec2(before.left, before.top))
        val target = viewport.worldToScreen(Vec2(remote.left, remote.top))
        assertEquals((remote.left - before.left) * viewport.zoom, target.x - source.x, .001f)
    }

    @Test fun hugeCanvasesFitWithoutAllocatingHugeRasterOrForcingEditorMinimumZoom() {
        val viewport = comparisonPreviewViewport(listOf(CanvasPaintBounds(-1_000_000f, -1_000_000f, 1_000_000f, 1_000_000f)), 600f, 280f)
        assertTrue(viewport.zoom > 0 && viewport.zoom < .001f)
        val center = viewport.worldToScreen(Vec2.Zero)
        assertEquals(300f, center.x, .001f); assertEquals(140f, center.y, .001f)
        assertFails { comparisonPreviewViewport(emptyList(), Float.NaN, 280f) }
        assertFails { comparisonPreviewViewport(emptyList(), 600f, 0f) }
    }
}
