package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AndroidGifAnimation
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Requires an Android device/emulator; host tests cannot validate real Bitmap pixel operations. */
class AndroidGifDeviceTest {
    @Test fun realPixelsStayImmutableAcrossRandomAccessAndRelease() = runBlocking {
        val animation = AndroidGifAnimation.decode(twoFrameGifFixture())
        try {
            val red = animation.frame(0)
            val blue = animation.frame(1)
            val pixels = IntArray(1)
            red.readPixels(pixels); assertEquals(0xffff0000.toInt(), pixels[0])
            blue.readPixels(pixels); assertEquals(0xff0000ff.toInt(), pixels[0])
            animation.frame(0).readPixels(pixels); assertEquals(0xffff0000.toInt(), pixels[0])
            animation.frame(1).readPixels(pixels); assertEquals(0xff0000ff.toInt(), pixels[0])
            red.readPixels(pixels); assertEquals(0xffff0000.toInt(), pixels[0])
            assertFailsWith<IllegalArgumentException> { animation.frame(2) }
            animation.release()
            blue.readPixels(pixels); assertEquals(0xff0000ff.toInt(), pixels[0])
            assertFailsWith<IllegalStateException> { animation.frame(0) }
        } finally { animation.release() }
    }
}
