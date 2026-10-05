package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap

/** Device-local normalized image region, never part of a canvas operation. */
data class ViewportImageRequest(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val displayWidth: Int, val displayHeight: Int,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom)
        require(displayWidth > 0 && displayHeight > 0)
    }
}

data class ImagePreviewTile(
    val bitmap: ImageBitmap,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val sourceWidth: Int, val sourceHeight: Int,
)
