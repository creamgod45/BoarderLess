package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import com.russhwolf.settings.Settings

class CanvasPreferences(
    private val settings: Settings = Settings(),
) {
    fun loadViewport(workspaceId: String): Viewport? {
        val prefix = prefix(workspaceId)
        if (!settings.hasKey("$prefix.zoom")) return null
        return runCatching {
            Viewport(
                pan =
                    Vec2(
                        x = settings.getFloat("$prefix.panX", 0f),
                        y = settings.getFloat("$prefix.panY", 0f),
                    ),
                zoom = settings.getFloat("$prefix.zoom", 1f),
            )
        }.getOrNull()
    }

    fun saveViewport(
        workspaceId: String,
        viewport: Viewport,
    ) {
        val prefix = prefix(workspaceId)
        settings.putFloat("$prefix.panX", viewport.pan.x)
        settings.putFloat("$prefix.panY", viewport.pan.y)
        settings.putFloat("$prefix.zoom", viewport.zoom)
    }

    fun loadDisplaySettings(workspaceId: String): CanvasDisplaySettings {
        val prefix = prefix(workspaceId)
        return CanvasDisplaySettings(
            showGrid = settings.getBoolean("$prefix.showGrid", true),
            snapToGrid = settings.getBoolean("$prefix.snapToGrid", false),
            backgroundToken = settings.getString("$prefix.background", "default"),
        )
    }

    fun saveDisplaySettings(
        workspaceId: String,
        display: CanvasDisplaySettings,
        saveBackground: Boolean = true,
    ) {
        val prefix = prefix(workspaceId)
        settings.putBoolean("$prefix.showGrid", display.showGrid)
        settings.putBoolean("$prefix.snapToGrid", display.snapToGrid)
        if (saveBackground) settings.putString("$prefix.background", display.backgroundToken)
    }

    private fun prefix(workspaceId: String): String = "workspace.$workspaceId.viewport"
}

data class CanvasDisplaySettings(
    val showGrid: Boolean = true,
    val snapToGrid: Boolean = false,
    val backgroundToken: String = "default",
)
