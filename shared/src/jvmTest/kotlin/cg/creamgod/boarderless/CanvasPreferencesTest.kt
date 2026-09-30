package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.CanvasDisplaySettings
import cg.creamgod.boarderless.data.persistence.CanvasPreferences
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalSettingsImplementation::class)
class CanvasPreferencesTest {
    @Test
    fun viewportAndDisplaySettingsSurviveARepositoryReopen() {
        val properties = Properties()
        val first = CanvasPreferences(PropertiesSettings(properties))
        val viewport = Viewport(pan = Vec2(320f, -48f), zoom = 1.75f)
        val display = CanvasDisplaySettings(
            showGrid = false,
            snapToGrid = true,
            backgroundToken = "cool",
        )

        first.saveViewport("workspace-a", viewport)
        first.saveDisplaySettings("workspace-a", display)
        val reopened = CanvasPreferences(PropertiesSettings(properties))

        assertEquals(viewport, reopened.loadViewport("workspace-a"))
        assertEquals(display, reopened.loadDisplaySettings("workspace-a"))
    }

    @Test
    fun preferencesRemainIsolatedBetweenWorkspaces() {
        val preferences = CanvasPreferences(PropertiesSettings(Properties()))
        val first = Viewport(pan = Vec2(10f, 20f), zoom = 0.75f)
        val second = Viewport(pan = Vec2(-500f, 80f), zoom = 2f)

        preferences.saveViewport("workspace-a", first)
        preferences.saveViewport("workspace-b", second)

        assertEquals(first, preferences.loadViewport("workspace-a"))
        assertEquals(second, preferences.loadViewport("workspace-b"))
    }

    @Test
    fun corruptedNonFiniteViewportIsIgnored() {
        val properties = Properties().apply {
            setProperty("workspace.workspace-a.viewport.panX", "12.0")
            setProperty("workspace.workspace-a.viewport.panY", "24.0")
            setProperty("workspace.workspace-a.viewport.zoom", "Infinity")
        }
        val preferences = CanvasPreferences(PropertiesSettings(properties))

        assertNull(preferences.loadViewport("workspace-a"))
    }
}
