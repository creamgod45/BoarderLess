package cg.creamgod.boarderless.feature.canvas

internal enum class InspectorSection(
    val token: String,
) {
    Content("content"),
    Appearance("appearance"),
    Transform("transform"),
    Arrange("arrange"),
    Reuse("reuse"),
}

internal fun toggleInspectorSection(
    collapsed: Set<String>,
    section: InspectorSection,
): Set<String> = if (section.token in collapsed) collapsed - section.token else collapsed + section.token

internal enum class PanelSection(
    val token: String,
) {
    CreateAndNavigate("compact-menu.create-and-navigate"),
    History("compact-menu.history"),
    CanvasView("compact-menu.canvas-view"),
}

internal fun togglePanelSection(
    collapsed: Set<String>,
    section: PanelSection,
): Set<String> = if (section.token in collapsed) collapsed - section.token else collapsed + section.token
