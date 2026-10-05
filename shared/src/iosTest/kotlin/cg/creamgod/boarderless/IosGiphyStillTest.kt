package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.IosAssetPreviewLoader
import cg.creamgod.boarderless.data.IosGifAnimation
import cg.creamgod.boarderless.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.*

class IosGiphyStillTest {
    @Test fun providerBytesReachGifDecoderAndPublishDifferentFrames() = runTest {
        val client = GiphyMediaClient(HttpClient(MockEngine { respond(twoFrameGifFixture()) }))
        val animation = loadGiphyAnimation("https://media2.giphy.com/fixture.gif", client) { IosGifAnimation.decode(it) }
        try {
            val pixel = IntArray(1)
            animation.frame(0).readPixels(pixel); assertEquals(0xffff0000.toInt(), pixel[0])
            animation.frame(1).readPixels(pixel); assertEquals(0xff0000ff.toInt(), pixel[0])
        } finally { animation.release() }
    }
    @Test fun ephemeralPngCanBeDecodedWithoutAnAssetFile() {
        val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
        val bitmap = IosAssetPreviewLoader.decodePreview(bytes)
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }
    @Test fun malformedProviderStillIsNotPublished() {
        assertFails { IosAssetPreviewLoader.decodePreview(byteArrayOf(1, 2, 3)) }
    }
}
