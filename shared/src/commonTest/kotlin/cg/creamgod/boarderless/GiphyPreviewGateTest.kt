package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.feature.canvas.giphyStillUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GiphyPreviewGateTest {
    @Test fun boundsActiveLoadsAndCancelledWaiterNeverEnters() =
        runTest {
            val gate = GiphyPreviewGate(3)
            val release = CompletableDeferred<Unit>()
            var active = 0
            var maximum = 0
            var entries = 0
            val loads =
                List(3) {
                    launch {
                        gate.load {
                            entries++
                            active++
                            maximum = maxOf(maximum, active)
                            release.await()
                            active--
                        }
                    }
                }
            runCurrent()
            val queued =
                launch {
                    gate.load {
                        entries++
                        active++
                        maximum = maxOf(maximum, active)
                        active--
                    }
                }
            runCurrent()
            assertEquals(3, active)
            assertEquals(3, entries)
            queued.cancelAndJoin()
            release.complete(Unit)
            loads.joinAll()
            assertEquals(3, maximum)
            assertEquals(3, entries)
            assertEquals(0, active)
            assertEquals("next", gate.load { "next" })
        }

    @Test fun failedLoadReleasesItsPermit() =
        runTest {
            val gate = GiphyPreviewGate(1)
            assertFailsWith<IllegalStateException> { gate.load { error("decoder failed") } }
            assertEquals(42, gate.load { 42 })
        }

    @Test fun choosesStillWithoutRewritingQueryOrDownloadingOriginal() {
        val small = "https://media2.giphy.com/a/100h_s.gif?cid=keep&rid=100h_s.gif"
        val item =
            GiphyItem(
                "id",
                images =
                    mapOf(
                        "fixed_width_still" to
                            GiphyRendition(url = "https://media2.giphy.com/200w_s.gif", size = "${GiphyMediaClient.MaxStillBytes + 1}"),
                        "fixed_height_still" to GiphyRendition(url = small, size = "2000"),
                        "original" to GiphyRendition(url = "https://media2.giphy.com/original.gif"),
                    ),
            )
        assertEquals(small, giphyStillUrl(item))
        assertNull(giphyStillUrl(item.copy(images = item.images - "fixed_height_still")))
        assertNull(giphyStillUrl(item.copy(images = mapOf("fixed_width_still" to GiphyRendition(url = "https://foreign.test/a.gif")))))
    }
}
