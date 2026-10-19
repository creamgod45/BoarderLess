@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class GifPlaybackTest {
    @Test fun finiteRepeatExcludesFirstCycleAndPreservesDelays() =
        runTest {
            val frames = mutableListOf<Pair<Int, Long>>()
            playGifFrames(2, 1, { if (it == 0) 50 else 100 }, {}, { frames += it to currentTime })
            assertEquals(listOf(0 to 0L, 1 to 50L, 0 to 150L, 1 to 200L), frames)
            assertEquals(300L, currentTime)
        }

    @Test fun pauseFreezesFrameAndResumesCursor() =
        runTest {
            val frames = mutableListOf<Int>()
            var playing = true
            val resume = CompletableDeferred<Unit>()
            val job =
                launch {
                    playGifFrames(2, -1, { 50 }, { if (!playing) resume.await() }, { frames += it })
                }
            runCurrent()
            advanceTimeBy(50)
            runCurrent()
            assertEquals(listOf(0, 1), frames)
            playing = false
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listOf(0, 1), frames)
            playing = true
            resume.complete(Unit)
            runCurrent()
            assertEquals(listOf(0, 1, 0), frames)
            job.cancelAndJoin()
            advanceTimeBy(500)
            assertEquals(listOf(0, 1, 0), frames)
        }

    @Test fun singleFrameStopsAndInfiniteCursorWraps() {
        assertNull(nextGifFrame(GifPlaybackCursor(), 1, 0))
        assertEquals(GifPlaybackCursor(0, 1), nextGifFrame(GifPlaybackCursor(2), 3, -1))
        assertEquals(GifPlaybackCursor(1), nextGifFrame(GifPlaybackCursor(), 3, 0))
        assertFailsWith<IllegalArgumentException> { nextGifFrame(GifPlaybackCursor(3), 3, 0) }
    }

    @Test fun tinyDurationIsNormalizedButLongDurationIsNotTruncated() {
        assertEquals(100L, gifFrameDelay(0))
        assertEquals(100L, gifFrameDelay(10))
        assertEquals(20L, gifFrameDelay(20))
        assertEquals(655_350L, gifFrameDelay(655_350))
    }
}
