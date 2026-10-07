package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.awaitActiveMediaRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MediaReadCancellationTest {
    @Test fun activeReadReturnsItsResult() =
        runTest {
            assertEquals("preview", awaitActiveMediaRead { "preview" })
        }

    @Test fun activeFailurePreservesOriginalError() =
        runTest {
            val failure = IllegalStateException("unavailable")
            assertSame(failure, assertFailsWith<IllegalStateException> { awaitActiveMediaRead<String> { throw failure } })
        }

    @Test fun cancelledLoaderReturningWithoutSuspensionCannotPublish() =
        runTest {
            var published = false
            var cancelled = false
            launch {
                try {
                    awaitActiveMediaRead {
                        currentCoroutineContext().cancel()
                        "late bitmap or metadata"
                    }
                    published = true
                } catch (_: CancellationException) {
                    cancelled = true
                }
            }.join()
            assertFalse(published)
            assertTrue(cancelled)
        }

    @Test fun cancelledLoaderThrowingOrdinaryErrorCannotPublishUnavailableState() =
        runTest {
            var unavailable = false
            var cancelled = false
            launch {
                try {
                    awaitActiveMediaRead<String> {
                        currentCoroutineContext().cancel()
                        throw IllegalStateException("late storage failure")
                    }
                } catch (_: CancellationException) {
                    cancelled = true
                } catch (_: Exception) {
                    unavailable = true
                }
            }.join()
            assertFalse(unavailable)
            assertTrue(cancelled)
        }

    @Test fun alreadyCancelledReadNeverStartsLoader() =
        runTest {
            var started = false
            launch {
                currentCoroutineContext()[Job]!!.cancel()
                try {
                    awaitActiveMediaRead { started = true }
                } catch (_: CancellationException) {
                    // Expected.
                }
            }.join()
            assertFalse(started)
        }
}
