package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.delay

/** Device-local decoder, never an operation or a shareable node attribute. */
interface GifAnimation {
    val frameCount: Int

    /** Repeats after the first cycle; -1 means infinite. */
    val repetitionCount: Int

    fun durationMs(frameIndex: Int): Long

    suspend fun frame(frameIndex: Int): ImageBitmap

    suspend fun release()
}

data class GifPlaybackCursor(
    val frameIndex: Int = 0,
    val completedCycles: Long = 0,
)

fun nextGifFrame(
    cursor: GifPlaybackCursor,
    frameCount: Int,
    repetitionCount: Int,
): GifPlaybackCursor? {
    require(frameCount > 0 && cursor.frameIndex in 0 until frameCount && cursor.completedCycles >= 0 && repetitionCount >= -1)
    if (cursor.frameIndex < frameCount - 1) return cursor.copy(frameIndex = cursor.frameIndex + 1)
    return if (repetitionCount == -1 || cursor.completedCycles < repetitionCount) {
        GifPlaybackCursor(0, cursor.completedCycles + 1)
    } else {
        null
    }
}

/** Browser-style normalization of zero/tiny GIF delay, without truncating long frame durations. */
fun gifFrameDelay(duration: Int): Long = if (duration < 20) 100 else duration.toLong()

/** Pause gates stop frame publication; cursor/loop progress survives a pause. */
suspend fun playGifFrames(
    frameCount: Int,
    repetitionCount: Int,
    durationMs: (Int) -> Long,
    awaitPlaying: suspend () -> Unit,
    showFrame: suspend (Int) -> Unit,
) {
    require(frameCount > 0 && repetitionCount >= -1)
    var cursor = GifPlaybackCursor()
    showFrame(0)
    while (true) {
        awaitPlaying()
        val duration = durationMs(cursor.frameIndex)
        require(duration > 0)
        delay(duration)
        awaitPlaying()
        val next = nextGifFrame(cursor, frameCount, repetitionCount) ?: return
        showFrame(next.frameIndex)
        cursor = next
    }
}
