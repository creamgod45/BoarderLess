package cg.creamgod.boarderless

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class DesktopVideoDisplayTransformTest {
    @Test fun sampleAspectRatioCorrectsEncodedHorizontalAxisBeforeRotation() {
        val wide = DesktopVideoDisplayTransform.from(64, 32, null, 2.0)
        assertEquals(128, wide.width); assertEquals(32, wide.height)
        val narrow = DesktopVideoDisplayTransform.from(64, 32, null, .5)
        assertEquals(32, narrow.width); assertEquals(32, narrow.height)
        val fractional = DesktopVideoDisplayTransform.from(64, 32, null, 4.0 / 3)
        assertEquals(86, fractional.width); assertEquals(32, fractional.height)
        val rotated = DesktopVideoDisplayTransform.from(64, 32, data(0, -65536, 0, 65536, 0), 2.0)
        assertEquals(32, rotated.width); assertEquals(128, rotated.height)
    }

    @Test fun invalidAndOversizedSampleAspectRatiosRejectBeforeRasterAllocation() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { ratio ->
            assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(64, 32, null, ratio) }
        }
        assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(4000, 2000, null, 2.0) }
    }
    @Test fun mirroredRasterPreservesAlphaAndDoesNotMutateSourcePixels() {
        val source = Image.makeRaster(ImageInfo(2, 1, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            byteArrayOf(255.toByte(), 0, 0, 128.toByte(), 0, 0, 255.toByte(), 255.toByte()), 8)
        try {
            val transform = DesktopVideoDisplayTransform.from(2, 1, data(-65536, 0, 0, 0, 65536))
            transform.apply(source).use { displayed ->
                val pixels = IntArray(2)
                displayed.toComposeImageBitmap().readPixels(pixels)
                assertEquals(255, pixels[0] and 255)
                assertEquals(255, pixels[0] ushr 24)
                assertEquals(128, pixels[1] ushr 24)
                assertTrue(((pixels[1] shr 16) and 255) >= 250)
            }
            val original = IntArray(2)
            source.toComposeImageBitmap().readPixels(original)
            assertEquals(128, original[0] ushr 24)
            assertEquals(255, original[1] and 255)
        } finally { source.close() }
    }
    @Test fun affineBoundsIncludeArbitraryRotationScaleAndMirror() {
        val mirror = DesktopVideoDisplayTransform.from(64, 32, data(-65536, 0, 0, 0, 65536))
        assertEquals(64, mirror.width); assertEquals(32, mirror.height)
        val scaled = DesktopVideoDisplayTransform.from(64, 32, data(131072, 0, 0, 0, 65536))
        assertEquals(128, scaled.width); assertEquals(32, scaled.height)
        val rotated = DesktopVideoDisplayTransform.from(64, 32, data(46341, -46341, 0, 46341, 46341))
        assertTrue(rotated.width in 68..69); assertTrue(rotated.height in 68..69)
    }

    @Test fun malformedPerspectiveSingularAndOversizedMatricesRejectBeforeRasterAllocation() {
        assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(64, 32, ByteBuffer.allocate(35)) }
        assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(64, 32, data(65536, 0, 1, 0, 65536)) }
        assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(64, 32, data(0, 0, 0, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { DesktopVideoDisplayTransform.from(4000, 2000, data(131072, 0, 0, 0, 131072)) }
    }

    private fun data(a: Int, b: Int, u: Int, c: Int, d: Int): ByteBuffer = ByteBuffer.allocate(36).order(ByteOrder.nativeOrder()).apply {
        listOf(a, b, u, c, d, 0, 0, 0, 1 shl 30).forEach { putInt(it) }
        flip()
    }
}
