package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.compactBottomSheetWidthDp
import cg.creamgod.boarderless.feature.canvas.compactInspectorWidthDp
import cg.creamgod.boarderless.feature.canvas.compactSheetExpandedAfterDrag
import cg.creamgod.boarderless.feature.canvas.compactStatusMaxWidthDp
import cg.creamgod.boarderless.feature.canvas.emptyCanvasCardWidthDp
import cg.creamgod.boarderless.feature.canvas.shouldShowCompactMenuEntry
import cg.creamgod.boarderless.feature.canvas.shouldShowDesktopCanvasToolbar
import cg.creamgod.boarderless.feature.canvas.shouldShowStatusOverlay
import cg.creamgod.boarderless.feature.canvas.transformHandleTouchTargetDp
import cg.creamgod.boarderless.feature.canvas.transformHandleVisualSizeDp
import cg.creamgod.boarderless.feature.canvas.usesCompactCanvasLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveCanvasTest {
    @Test
    fun phoneAndNarrowTabletWidthsUseCompactLayout() {
        assertTrue(usesCompactCanvasLayout(widthPixels = 1080, heightPixels = 2400, density = 3f))
        assertTrue(usesCompactCanvasLayout(widthPixels = 1300, heightPixels = 1800, density = 2f))
        assertTrue(usesCompactCanvasLayout(widthPixels = 1206, heightPixels = 2622, density = 3f))
    }

    @Test
    fun landscapePhoneKeepsCompactLayoutDespiteWideLongEdge() {
        assertTrue(usesCompactCanvasLayout(widthPixels = 2622, heightPixels = 1206, density = 3f))
    }

    @Test
    fun desktopWidthsKeepSideInspector() {
        assertFalse(usesCompactCanvasLayout(widthPixels = 1440, heightPixels = 1800, density = 2f))
        assertFalse(usesCompactCanvasLayout(widthPixels = 1200, heightPixels = 800, density = 1f))
    }

    @Test
    fun unknownOrInvalidMeasurementsDoNotFlashCompactLayout() {
        assertFalse(usesCompactCanvasLayout(widthPixels = 0, heightPixels = 1800, density = 2f))
        assertFalse(usesCompactCanvasLayout(widthPixels = 1080, heightPixels = 0, density = 2f))
        assertFalse(usesCompactCanvasLayout(widthPixels = 1080, heightPixels = 1800, density = 0f))
    }

    @Test
    fun desktopToolbarWaitsForAValidLayoutMeasurement() {
        assertFalse(shouldShowDesktopCanvasToolbar(widthPixels = 0, heightPixels = 0, density = 3f))
        assertFalse(shouldShowDesktopCanvasToolbar(widthPixels = 1206, heightPixels = 2622, density = 3f))
        assertTrue(shouldShowDesktopCanvasToolbar(widthPixels = 1440, heightPixels = 900, density = 1f))
    }

    @Test
    fun inspectorFitsPhoneAndSplitScreenWidths() {
        assertEquals(284f, compactInspectorWidthDp(widthPixels = 1080, density = 3f))
        assertEquals(196f, compactInspectorWidthDp(widthPixels = 440, density = 2f))
        assertEquals(160f, compactInspectorWidthDp(widthPixels = 300, density = 2f))
    }

    @Test
    fun emptyCanvasCardFitsPhonesAndSplitScreenWithoutChangingDesktopWidth() {
        assertEquals(360f, emptyCanvasCardWidthDp(1440, density = 2f, compactLayout = false))
        assertEquals(336f, emptyCanvasCardWidthDp(1080, density = 3f, compactLayout = true))
        assertEquals(196f, emptyCanvasCardWidthDp(440, density = 2f, compactLayout = true))
        assertEquals(126f, emptyCanvasCardWidthDp(300, density = 2f, compactLayout = true))
        assertEquals(360f, emptyCanvasCardWidthDp(0, density = 2f, compactLayout = true))
    }

    @Test
    fun compactStatusKeepsSideMarginsAndAReadableMaximumWidth() {
        assertEquals(360f, compactStatusMaxWidthDp(1206, density = 3f))
        assertEquals(188f, compactStatusMaxWidthDp(440, density = 2f))
        assertEquals(120f, compactStatusMaxWidthDp(240, density = 2f))
        assertEquals(320f, compactStatusMaxWidthDp(0, density = 2f))
    }

    @Test
    fun bottomSheetKeepsReadableMarginsAndMinimumWidth() {
        assertEquals(344f, compactBottomSheetWidthDp(widthPixels = 1080, density = 3f))
        assertEquals(204f, compactBottomSheetWidthDp(widthPixels = 440, density = 2f))
        assertEquals(134f, compactBottomSheetWidthDp(widthPixels = 300, density = 2f))
        assertEquals(320f, compactBottomSheetWidthDp(widthPixels = 0, density = 2f))
    }

    @Test
    fun compactTransformHandlesUseAccessibleHitTargetsWithoutOversizedVisuals() {
        assertEquals(44f, transformHandleTouchTargetDp(largeTouchTargets = true))
        assertEquals(22f, transformHandleVisualSizeDp(largeTouchTargets = true))
        assertEquals(18f, transformHandleTouchTargetDp(largeTouchTargets = false))
        assertEquals(18f, transformHandleVisualSizeDp(largeTouchTargets = false))
    }

    @Test
    fun bottomSheetDragOnlyChangesStateAfterIntentionalMovement() {
        assertTrue(compactSheetExpandedAfterDrag(expanded = false, dragYPixels = -96f, density = 3f))
        assertFalse(compactSheetExpandedAfterDrag(expanded = true, dragYPixels = 96f, density = 3f))
        assertFalse(compactSheetExpandedAfterDrag(expanded = false, dragYPixels = -40f, density = 3f))
        assertTrue(compactSheetExpandedAfterDrag(expanded = true, dragYPixels = 40f, density = 3f))
        assertTrue(compactSheetExpandedAfterDrag(expanded = true, dragYPixels = Float.NaN, density = 3f))
    }

    @Test
    fun statusOverlayDoesNotObscureModalContent() {
        assertTrue(shouldShowStatusOverlay(compactModalVisible = false, commandPaletteVisible = false))
        assertFalse(shouldShowStatusOverlay(compactModalVisible = true, commandPaletteVisible = false))
        assertFalse(shouldShowStatusOverlay(compactModalVisible = false, commandPaletteVisible = true))
    }

    @Test
    fun compactMenuEntryOnlyAppearsWithoutAnotherOverlay() {
        assertTrue(
            shouldShowCompactMenuEntry(
                compactLayout = true,
                compactModalVisible = false,
                commandPaletteVisible = false,
            ),
        )
        assertFalse(
            shouldShowCompactMenuEntry(
                compactLayout = true,
                compactModalVisible = true,
                commandPaletteVisible = false,
            ),
        )
        assertFalse(
            shouldShowCompactMenuEntry(
                compactLayout = true,
                compactModalVisible = false,
                commandPaletteVisible = true,
            ),
        )
        assertFalse(
            shouldShowCompactMenuEntry(
                compactLayout = false,
                compactModalVisible = false,
                commandPaletteVisible = false,
            ),
        )
    }
}
