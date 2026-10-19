package cg.creamgod.boarderless.domain.export

import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import kotlin.math.ceil

/** Bounds of actual painted content, including text outside its object and routed connections. */
data class CanvasPaintBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(right >= left && bottom >= top)
    }
}

data class CanvasExportLayout(
    val worldBounds: CanvasPaintBounds, val pixelWidth: Int, val pixelHeight: Int, val scale: Float,
) {
    val viewport: Viewport get() = Viewport(Vec2(-worldBounds.left * scale, -worldBounds.top * scale), scale)
}

/** The caller must supply measured paint bounds; object containers alone would crop floating text. */
fun canvasExportLayout(paint: List<CanvasPaintBounds>, margin: Float = 24f, scale: Float = 1f,
    maxDimension: Int = 4096, maxPixels: Long = 4_194_304): CanvasExportLayout {
    require(maxDimension in 1..MaxCanvasExportDimension && maxPixels in 1..MaxCanvasExportPixels)
    require(paint.isNotEmpty()) { "Nothing to export" }
    require(margin.isFinite() && margin in 0f..1024f)
    require(scale.isFinite() && scale > 0f && scale <= 8f)
    val bounds = CanvasPaintBounds(paint.minOf { it.left } - margin, paint.minOf { it.top } - margin,
        paint.maxOf { it.right } + margin, paint.maxOf { it.bottom } + margin)
    val width = ceil((bounds.right.toDouble() - bounds.left) * scale).coerceAtLeast(1.0)
    val height = ceil((bounds.bottom.toDouble() - bounds.top) * scale).coerceAtLeast(1.0)
    require(width <= maxDimension && height <= maxDimension && width * height <= maxPixels) { "Export size limit exceeded" }
    return CanvasExportLayout(bounds, width.toInt(), height.toInt(), scale)
}
