package cg.creamgod.boarderless

import androidx.compose.ui.graphics.ImageBitmap
import cg.creamgod.boarderless.data.GifAnimation
import cg.creamgod.boarderless.data.remote.loadGiphyAnimation
import cg.creamgod.boarderless.data.remote.loadGiphyStill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadDesktopGiphyStill(url: String): ImageBitmap =
    withContext(Dispatchers.IO) {
        loadGiphyStill(url) { DesktopAssetPreviewLoader.decodePreview(it) }
    }

internal suspend fun loadDesktopGiphyAnimation(url: String): GifAnimation = loadGiphyAnimation(url) { DesktopGifAnimation.decode(it) }
