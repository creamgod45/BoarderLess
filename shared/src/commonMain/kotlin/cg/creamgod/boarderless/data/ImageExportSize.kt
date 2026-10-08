package cg.creamgod.boarderless.data

import kotlin.math.floor

internal data class ImageExportSize(val width: Int, val height: Int)

/** Fit the complete original inside the export box without upscaling or full-source allocation. */
internal fun imageExportSize(sourceWidth: Int, sourceHeight: Int, displayWidth: Int, displayHeight: Int): ImageExportSize {
    require(sourceWidth in 1..65_535 && sourceHeight in 1..65_535 && sourceWidth.toLong() * sourceHeight <= 268_435_456)
    require(displayWidth in 1..4096 && displayHeight in 1..4096 && displayWidth.toLong() * displayHeight <= 4_194_304)
    val fit = minOf(displayWidth.toDouble() / sourceWidth, displayHeight.toDouble() / sourceHeight, 1.0)
    return ImageExportSize(floor(sourceWidth * fit).toInt().coerceAtLeast(1), floor(sourceHeight * fit).toInt().coerceAtLeast(1))
}
