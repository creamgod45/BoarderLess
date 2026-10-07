package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.UiPreferences
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsImplementation::class)
class UiPreferencesTest {
    @Test
    fun inspectorCollapseStateSurvivesASettingsReopen() {
        val properties = Properties()
        val first = UiPreferences(PropertiesSettings(properties))

        assertTrue(first.collapsedInspectorSections.isEmpty())
        first.collapsedInspectorSections = setOf("reuse", "content")

        val reopened = UiPreferences(PropertiesSettings(properties))
        assertEquals(setOf("content", "reuse"), reopened.collapsedInspectorSections)
        assertEquals("content,reuse", properties.getProperty("ui.inspector.collapsedSections"))
    }

    @Test
    fun inspectorCollapseStateNormalizesEmptyAndDuplicateTokens() {
        val properties =
            Properties().apply {
                setProperty("ui.inspector.collapsedSections", " content,content, ,appearance ")
            }

        assertEquals(
            setOf("content", "appearance"),
            UiPreferences(PropertiesSettings(properties)).collapsedInspectorSections,
        )
    }

    @Test
    fun panelCollapseStateIsPersistedSeparatelyFromInspectorState() {
        val properties = Properties()
        val first = UiPreferences(PropertiesSettings(properties))

        first.collapsedInspectorSections = setOf("content")
        first.collapsedPanelSections = setOf("compact-menu.history", "compact-menu.canvas-view")

        val reopened = UiPreferences(PropertiesSettings(properties))
        assertEquals(setOf("content"), reopened.collapsedInspectorSections)
        assertEquals(
            setOf("compact-menu.history", "compact-menu.canvas-view"),
            reopened.collapsedPanelSections,
        )
        assertEquals(
            "compact-menu.canvas-view,compact-menu.history",
            properties.getProperty("ui.panels.collapsedSections"),
        )
    }
}
