package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class VideoPlaybackLeaseTest {
    @Test fun successfulCommandsStayOpenUntilOwnerReleases() = runTest {
        val player = Player()
        val lease = VideoPlaybackLease(player)
        lease.execute { setPlaying(true) }
        lease.execute { setMuted(false) }
        lease.execute { seekTo(123) }
        assertEquals(3, player.commands)
        assertFalse(lease.failed.value)
        assertEquals(0, player.releases)
        lease.release(); lease.release()
        assertEquals(1, player.releases)
        assertFailsWith<IllegalStateException> { lease.execute { setPlaying(true) } }
    }

    @Test fun commandFailureClosesEvenWhenNativeStateDoesNotReportFailure() = runTest {
        val player = Player(commandError = IllegalArgumentException("Device lost"))
        val lease = VideoPlaybackLease(player)
        assertFailsWith<IllegalArgumentException> { lease.execute { setMuted(false) } }
        assertTrue(lease.failed.value)
        assertFalse(player.state.value.failed)
        assertEquals(1, player.releases)
        lease.release()
        assertEquals(1, player.releases)
    }

    @Test fun cleanupFailurePreservesOriginalErrorAndLeaseStaysTerminal() = runTest {
        val original = IllegalArgumentException("Seek failed")
        val cleanup = IllegalStateException("Close failed")
        val player = Player(original, cleanup)
        val lease = VideoPlaybackLease(player)
        val error = assertFailsWith<IllegalArgumentException> { lease.execute { seekTo(0) } }
        assertSame(original, error)
        // Coroutine stack-trace recovery can copy an exception across withContext.
        assertTrue(error.suppressedExceptions.any { it is IllegalStateException && it.message == cleanup.message })
        lease.release()
        assertEquals(1, player.releases)
        assertFailsWith<IllegalStateException> { lease.execute { setPlaying(true) } }
    }

    @Test fun cancellationIsNotReportedAsCommandFailureAndOwnerStillCloses() = runTest {
        val player = Player(commandError = CancellationException("Workspace changed"))
        val lease = VideoPlaybackLease(player)
        assertFailsWith<CancellationException> { lease.execute { setPlaying(true) } }
        assertFalse(lease.failed.value)
        assertEquals(0, player.releases)
        lease.release()
        assertEquals(1, player.releases)
    }

    @Test fun releaseWaitsForInFlightCommandAndRejectsQueuedCommands() = runTest {
        val player = Player()
        val lease = VideoPlaybackLease(player)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val command = launch { lease.execute { entered.complete(Unit); finish.await(); setPlaying(true) } }
        entered.await()
        val release = launch { lease.release() }
        yield()
        assertEquals(0, player.releases)
        finish.complete(Unit)
        command.join(); release.join()
        assertEquals(1, player.releases)
        assertFailsWith<IllegalStateException> { lease.execute { seekTo(1) } }
    }

    private class Player(private val commandError: Exception? = null, private val releaseError: Exception? = null) : VideoPlayback {
        override val state = MutableStateFlow(VideoPlaybackState())
        var commands = 0
        var releases = 0
        private fun command() { commands++; commandError?.let { throw it } }
        override suspend fun setPlaying(playing: Boolean) = command()
        override suspend fun setMuted(muted: Boolean) = command()
        override suspend fun seekTo(positionMs: Long) = command()
        override suspend fun release() { releases++; releaseError?.let { throw it } }
    }
}
