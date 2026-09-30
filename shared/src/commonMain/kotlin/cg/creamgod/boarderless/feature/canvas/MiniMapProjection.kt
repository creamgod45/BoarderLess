package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import kotlin.math.min

internal data class MiniMapRect(
    val topLeft: Vec2,
    val bottomRight: Vec2,
)

internal data class MiniMapProjection(
    val worldBounds: MiniMapRect,
    val scale: Float,
    val offset: Vec2,
    val viewportRect: MiniMapRect,
) {
    fun worldToMiniMap(point: Vec2): Vec2 = (point - worldBounds.topLeft) * scale + offset

    fun miniMapToWorld(point: Vec2): Vec2 {
        val world = (point - offset) / scale + worldBounds.topLeft
        return Vec2(
            x = world.x.coerceIn(worldBounds.topLeft.x, worldBounds.bottomRight.x),
            y = world.y.coerceIn(worldBounds.topLeft.y, worldBounds.bottomRight.y),
        )
    }
}

internal fun createMiniMapProjection(
    contentBounds: CanvasObjectBounds,
    viewport: Viewport,
    canvasSize: CanvasSize,
    miniMapSize: CanvasSize,
    padding: Float,
): MiniMapProjection {
    require(canvasSize.width > 0f && canvasSize.height > 0f) { "Canvas size must be positive" }
    require(miniMapSize.width > 0f && miniMapSize.height > 0f) { "Minimap size must be positive" }
    require(padding >= 0f) { "Minimap padding must not be negative" }

    val viewportTopLeft = viewport.screenToWorld(Vec2.Zero)
    val viewportBottomRight = viewport.screenToWorld(Vec2(canvasSize.width, canvasSize.height))
    val worldBounds = MiniMapRect(
        topLeft = Vec2(
            x = minOf(contentBounds.topLeft.x, viewportTopLeft.x),
            y = minOf(contentBounds.topLeft.y, viewportTopLeft.y),
        ),
        bottomRight = Vec2(
            x = maxOf(contentBounds.bottomRight.x, viewportBottomRight.x),
            y = maxOf(contentBounds.bottomRight.y, viewportBottomRight.y),
        ),
    )
    val worldWidth = (worldBounds.bottomRight.x - worldBounds.topLeft.x).coerceAtLeast(1f)
    val worldHeight = (worldBounds.bottomRight.y - worldBounds.topLeft.y).coerceAtLeast(1f)
    val availableWidth = (miniMapSize.width - padding * 2f).coerceAtLeast(1f)
    val availableHeight = (miniMapSize.height - padding * 2f).coerceAtLeast(1f)
    val scale = min(availableWidth / worldWidth, availableHeight / worldHeight)
    val renderedSize = Vec2(worldWidth * scale, worldHeight * scale)
    val offset = Vec2(
        x = (miniMapSize.width - renderedSize.x) / 2f,
        y = (miniMapSize.height - renderedSize.y) / 2f,
    )
    fun project(point: Vec2): Vec2 = (point - worldBounds.topLeft) * scale + offset

    return MiniMapProjection(
        worldBounds = worldBounds,
        scale = scale,
        offset = offset,
        viewportRect = MiniMapRect(
            topLeft = project(viewportTopLeft),
            bottomRight = project(viewportBottomRight),
        ),
    )
}
