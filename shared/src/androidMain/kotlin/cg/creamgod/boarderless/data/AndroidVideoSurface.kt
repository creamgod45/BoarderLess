package cg.creamgod.boarderless.data

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun AndroidVideoSurface(playback: VideoPlayback, modifier: Modifier) {
    val androidPlayback = playback as AndroidVideoPlayback
    val state by playback.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(playback, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch { playback.release() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> VideoTextureView(context, androidPlayback) },
        update = { it.fit(state.width, state.height) },
        onRelease = { it.detachPlayer() },
    )
}

private class VideoTextureView(context: android.content.Context, private val player: AndroidVideoPlayback) : TextureView(context) {
    private var ownedSurface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0
    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                ownedSurface = Surface(texture).also { player.attachSurface(this@VideoTextureView, it) }
                fit(videoWidth, videoHeight)
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { fit(videoWidth, videoHeight) }
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { detachPlayer(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }
    fun fit(videoWidth: Int, videoHeight: Int) {
        this.videoWidth = videoWidth; this.videoHeight = videoHeight
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val scale = min(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply { setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f) })
    }
    fun detachPlayer() {
        player.attachSurface(this, null)
        ownedSurface?.release()
        ownedSurface = null
    }
}
