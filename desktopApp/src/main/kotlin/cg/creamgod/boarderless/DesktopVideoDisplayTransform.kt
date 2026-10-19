package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetPreviewPolicy
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor

/** Copies container side data once; never retains a pointer into the native decoder. */
internal class DesktopVideoDisplayTransform private constructor(
    val width: Int,
    val height: Int,
    private val matrix: Matrix33,
    private val identity: Boolean,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) {
    fun apply(source: Image): Image {
        require(source.width == sourceWidth && source.height == sourceHeight) { "Video dimensions changed during playback" }
        if (identity) return source
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(0)
            surface.canvas.concat(matrix)
            surface.canvas.drawImage(source, 0f, 0f)
            surface.makeImageSnapshot()
        }
    }

    companion object {
        fun from(
            width: Int,
            height: Int,
            sideData: ByteBuffer?,
            sampleAspectRatio: Double = 1.0,
        ): DesktopVideoDisplayTransform {
            AssetPreviewPolicy.validateDimensions(width, height)
            require(sampleAspectRatio.isFinite() && sampleAspectRatio > 0) { "Invalid video sample aspect ratio" }
            val values =
                if (sideData == null) {
                    intArrayOf(65536, 0, 0, 0, 65536, 0, 0, 0, 1 shl 30)
                } else {
                    require(sideData.remaining() >= 36) { "Truncated video display matrix" }
                    val buffer = sideData.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer()
                    IntArray(9) { buffer.get() }
                }
            // Perspective matrices are not silently treated as ordinary rotation.
            require(values[2] == 0 && values[5] == 0 && values[8] > 0) { "Unsupported video perspective matrix" }
            val scale = values[8].toDouble() / (1 shl 30)
            // Correct encoded pixel width before applying the container's display matrix.
            val a = values[0] / 65536.0 / scale * sampleAspectRatio
            val b = values[1] / 65536.0 / scale * sampleAspectRatio
            val c = values[3] / 65536.0 / scale
            val d = values[4] / 65536.0 / scale
            require(kotlin.math.abs(a * d - b * c) > 1e-12) { "Singular video display matrix" }
            // Translation changes origin, not the local displayed size; normalize the bounding box.
            val xs = doubleArrayOf(0.0, a * width, c * height, a * width + c * height)
            val ys = doubleArrayOf(0.0, b * width, d * height, b * width + d * height)
            val left = floor(xs.min())
            val top = floor(ys.min())
            val outputWidth = ceil(xs.max()) - left
            val outputHeight = ceil(ys.max()) - top
            require(outputWidth in 1.0..Int.MAX_VALUE.toDouble() && outputHeight in 1.0..Int.MAX_VALUE.toDouble())
            val outWidth = outputWidth.toInt()
            val outHeight = outputHeight.toInt()
            AssetPreviewPolicy.validateDimensions(outWidth, outHeight)
            return DesktopVideoDisplayTransform(
                outWidth,
                outHeight,
                Matrix33(a.toFloat(), c.toFloat(), (-left).toFloat(), b.toFloat(), d.toFloat(), (-top).toFloat(), 0f, 0f, 1f),
                a == 1.0 && d == 1.0 && b == 0.0 && c == 0.0,
                width,
                height,
            )
        }
    }
}
