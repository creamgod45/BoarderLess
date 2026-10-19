@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package cg.creamgod.boarderless.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

@Composable
fun IosVideoSurface(
    playback: VideoPlayback,
    modifier: Modifier,
) {
    val native = playback as IosVideoPlayback
    UIKitView(
        factory = { VideoLayerView(native) },
        modifier = modifier,
        onRelease = { it.detachPlayer() },
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
    )
}

private class VideoLayerView(
    private val playback: IosVideoPlayback,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val videoLayer = AVPlayerLayer()

    init {
        userInteractionEnabled = false
        clipsToBounds = true
        videoLayer.videoGravity = AVLayerVideoGravityResizeAspect
        layer.addSublayer(videoLayer)
        playback.attach(videoLayer, this)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        videoLayer.frame = bounds
        CATransaction.commit()
    }

    fun detachPlayer() {
        playback.detach(videoLayer, this)
        videoLayer.removeFromSuperlayer()
    }
}
