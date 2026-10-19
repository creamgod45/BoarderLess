package cg.creamgod.boarderless.domain.export

/** Straight (not premultiplied) sRGB ARGB pixels, top row first. Owns its input. */
class CanvasExportRaster(val width: Int, val height: Int, pixels: IntArray) {
    private val owned: IntArray
    init {
        require(width in 1..MaxCanvasExportDimension && height in 1..MaxCanvasExportDimension) { "Export dimensions out of range" }
        require(width.toLong() * height <= MaxCanvasExportPixels) { "Export pixel limit exceeded" }
        require(pixels.size.toLong() == width.toLong() * height) { "Export pixels do not match dimensions" }
        owned = pixels.copyOf()
    }
    internal fun pixel(index: Int): Int = owned[index]
    internal val pixelCount: Int get() = owned.size
}

// Representation limits: dimensions and the backing IntArray use Kotlin Int indices.
// These are not a product image-size policy; platform runtimes may have their own budgets.
const val MaxCanvasExportPixels: Long = 2_147_483_647
const val MaxCanvasExportDimension: Int = Int.MAX_VALUE

enum class CanvasExportFormat(val extension: String, val mediaType: String) {
    Png("png", "image/png"), Pdf("pdf", "application/pdf"),
}

fun CanvasExportRaster.encode(format: CanvasExportFormat): ByteArray = when (format) {
    CanvasExportFormat.Png -> encodeCanvasPng(this)
    CanvasExportFormat.Pdf -> encodeCanvasPdf(this)
}
