package cg.creamgod.boarderless.domain.model

import kotlin.math.min

data class Viewport(
    val pan: Vec2 = Vec2.Zero,
    val zoom: Float = 1f,
) {
    init {
        require(zoom.isFinite() && zoom > 0f) { "zoom must be finite and positive" }
    }

    fun worldToScreen(world: Vec2): Vec2 = world * zoom + pan

    fun screenToWorld(screen: Vec2): Vec2 = (screen - pan) / zoom

    fun panBy(screenDelta: Vec2): Viewport = copy(pan = pan + screenDelta)

    fun zoomAt(
        screenPoint: Vec2,
        factor: Float,
        minimumZoom: Float = 0.2f,
        maximumZoom: Float = 4f,
    ): Viewport {
        val worldPoint = screenToWorld(screenPoint)
        val nextZoom = (zoom * factor).coerceIn(minimumZoom, maximumZoom)
        return Viewport(
            pan = screenPoint - worldPoint * nextZoom,
            zoom = nextZoom,
        )
    }

    fun fit(
        worldTopLeft: Vec2,
        worldBottomRight: Vec2,
        screenSize: CanvasSize,
        padding: Float = 48f,
        minimumZoom: Float = 0.2f,
        maximumZoom: Float = 4f,
    ): Viewport {
        val contentWidth = (worldBottomRight.x - worldTopLeft.x).coerceAtLeast(1f)
        val contentHeight = (worldBottomRight.y - worldTopLeft.y).coerceAtLeast(1f)
        val availableWidth = (screenSize.width - padding * 2f).coerceAtLeast(1f)
        val availableHeight = (screenSize.height - padding * 2f).coerceAtLeast(1f)
        val nextZoom =
            min(availableWidth / contentWidth, availableHeight / contentHeight)
                .coerceIn(minimumZoom, maximumZoom)
        val worldCenter =
            Vec2(
                x = (worldTopLeft.x + worldBottomRight.x) / 2f,
                y = (worldTopLeft.y + worldBottomRight.y) / 2f,
            )
        val screenCenter = Vec2(screenSize.width / 2f, screenSize.height / 2f)
        return Viewport(
            pan = screenCenter - worldCenter * nextZoom,
            zoom = nextZoom,
        )
    }
}
