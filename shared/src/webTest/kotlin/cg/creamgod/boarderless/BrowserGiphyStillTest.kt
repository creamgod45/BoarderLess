package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.awaitBrowserImageDecoder
import cg.creamgod.boarderless.data.decodeBrowserPreview
import cg.creamgod.boarderless.data.BrowserGifAnimation
import cg.creamgod.boarderless.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.test.*

class BrowserGiphyStillTest {
    @Test fun providerBytesReachGifDecoderAndPublishDifferentFrames() = runTest {
        withContext(Dispatchers.Default) {
            val client = GiphyMediaClient(HttpClient(MockEngine { respond(twoFrameGifFixture()) }))
            val animation = loadGiphyAnimation("https://media2.giphy.com/fixture.gif", client) {
                awaitBrowserImageDecoder(); BrowserGifAnimation.decode(it)
            }
            try {
                val pixel = IntArray(1)
                animation.frame(0).readPixels(pixel); assertEquals(0xffff0000.toInt(), pixel[0])
                animation.frame(1).readPixels(pixel); assertEquals(0xff0000ff.toInt(), pixel[0])
            } finally { animation.release() }
        }
    }
    @Test fun ephemeralPngCanBeDecodedWithoutAssetStorage() = runTest {
        withContext(Dispatchers.Default) {
            awaitBrowserImageDecoder()
            val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            val bitmap = decodeBrowserPreview(bytes)
            assertEquals(1, bitmap.width)
            assertEquals(1, bitmap.height)
            assertFails { decodeBrowserPreview(byteArrayOf(1, 2, 3)) }
        }
    }
}
