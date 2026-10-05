package cg.creamgod.boarderless

import androidx.compose.ui.graphics.ImageBitmap
import cg.creamgod.boarderless.data.GifAnimation
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.feature.canvas.giphyAnimationUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GiphyAnimationTest {
    private val url = "https://media2.giphy.com/media/id/200w.gif?cid=keep&rid=200w.gif"
    private fun client() = GiphyMediaClient(HttpClient(MockEngine { respond(twoFrameGifFixture()) }))

    @Test fun choosesSmallProviderRenditionAndKeepsQueryWithoutOriginalFallback() {
        val item = GiphyItem("id", images = mapOf(
            "fixed_width" to GiphyRendition(url = url, size = "100"),
            "original" to GiphyRendition(url = "https://media2.giphy.com/original.gif")))
        assertEquals(url, giphyAnimationUrl(item))
        assertNull(giphyAnimationUrl(item.copy(images = item.images - "fixed_width")))
        assertNull(giphyAnimationUrl(item.copy(images = mapOf("fixed_width" to GiphyRendition(url = "https://foreign.test/a.gif")))))
    }
    @Test fun oversizedRenditionUsesSafeDownsizedOrRemainsUnavailable() {
        val item = GiphyItem("id", images = mapOf(
            "fixed_width" to GiphyRendition(url = url, size = "${GiphyMediaClient.MaxStillBytes + 1}"),
            "downsized" to GiphyRendition(url = "$url&smaller=true", size = "500")))
        assertEquals("$url&smaller=true", giphyAnimationUrl(item))
        assertNull(giphyAnimationUrl(item.copy(images = item.images - "downsized")))
    }
    @Test fun successfulLoadTransfersDecoderOwnershipWithoutEarlyRelease() = runTest {
        val animation = StubAnimation()
        val opened = loadGiphyAnimation(url, client()) { bytes ->
            assertContentEquals(twoFrameGifFixture(), bytes)
            animation
        }
        assertSame(animation, opened)
        assertEquals(0, animation.releases)
        opened.release()
        assertEquals(1, animation.releases)
    }
    @Test fun cancellationAfterDecodeReleasesEvenWhenCleanupFailsAndKeepsOriginalCancellation() = runTest {
        val animation = StubAnimation(failRelease = true)
        val isolatedJob = Job()
        try {
            val failed = assertFailsWith<CancellationException> {
                withContext(isolatedJob) {
                    loadGiphyAnimation(url, client()) {
                        currentCoroutineContext().cancel(CancellationException("Obsolete selection"))
                        animation
                    }
                }
            }
            assertTrue(failed.message.orEmpty().contains("Obsolete selection"))
            assertEquals(1, animation.releases)
        } finally { isolatedJob.cancel() }
    }
    private class StubAnimation(private val failRelease: Boolean = false) : GifAnimation {
        var releases = 0
        override val frameCount = 2
        override val repetitionCount = -1
        override fun durationMs(frameIndex: Int) = 100L
        override suspend fun frame(frameIndex: Int): ImageBitmap = error("Not required for ownership test")
        override suspend fun release() { releases++; if (failRelease) error("cleanup failure") }
    }
}
