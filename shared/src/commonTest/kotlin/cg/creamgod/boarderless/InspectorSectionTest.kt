package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.InspectorSection
import cg.creamgod.boarderless.feature.canvas.PanelSection
import cg.creamgod.boarderless.feature.canvas.toggleInspectorSection
import cg.creamgod.boarderless.feature.canvas.togglePanelSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectorSectionTest {
    @Test
    fun emptyPreferenceLeavesEverySectionExpanded() {
        val collapsed = emptySet<String>()

        assertTrue(InspectorSection.entries.none { it.token in collapsed })
    }

    @Test
    fun togglingASectionDoesNotCloseItsSiblings() {
        val contentCollapsed = toggleInspectorSection(emptySet(), InspectorSection.Content)
        val twoCollapsed = toggleInspectorSection(contentCollapsed, InspectorSection.Appearance)

        assertEquals(setOf("content", "appearance"), twoCollapsed)
        assertEquals(setOf("appearance"), toggleInspectorSection(twoCollapsed, InspectorSection.Content))
    }

    @Test
    fun panelSectionsUseIndependentNamespacedTokens() {
        val createCollapsed = togglePanelSection(emptySet(), PanelSection.CreateAndNavigate)
        val twoCollapsed = togglePanelSection(createCollapsed, PanelSection.CanvasView)

        assertEquals(
            setOf("compact-menu.create-and-navigate", "compact-menu.canvas-view"),
            twoCollapsed,
        )
        assertEquals(
            setOf("compact-menu.canvas-view"),
            togglePanelSection(twoCollapsed, PanelSection.CreateAndNavigate),
        )
    }
}
