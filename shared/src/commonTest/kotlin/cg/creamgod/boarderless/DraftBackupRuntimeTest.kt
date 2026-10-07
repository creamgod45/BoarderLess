package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DraftBackupRuntimeTest {
    private val workspace = Workspace(WorkspaceId("w"), "Private board")
    private val review = WorkspaceDraftReview("draft", 1, 2, 1, 2, workspace, workspace, workspace, emptyList(), true, true)
    private class Destination : DraftBackupDestination {
        var json: String? = null
        var closed = 0
        var beforeWrite: () -> Unit = {}
        override suspend fun write(json: String, canWrite: () -> Boolean) {
            beforeWrite()
            check(canWrite())
            this.json = json
        }
        override fun close() { closed++ }
    }

    @Test fun explicitDestinationThenFreshReadAndOnlyThenPrivateJsonWrite() = runTest {
        val destination = Destination()
        val order = mutableListOf<String>()
        val runtime = DraftBackupRuntime { name ->
            assertEquals("boarderless-draft-backup.json", name)
            assertFalse("Private" in name)
            order.add("choose"); destination
        }
        val result = exportWorkspaceDraftBackup(runtime, "draft", { true }) {
            assertNull(destination.json)
            order.add("fresh read"); review
        }
        assertEquals(DraftBackupResult.Saved, result)
        assertEquals(listOf("choose", "fresh read"), order)
        assertEquals(review.toBackupJson(), destination.json)
        assertEquals(1, destination.closed)
    }

    @Test fun cancellationScopeChangesAndReadDenialNeverWriteOrLeakDestination() = runTest {
        var reads = 0
        assertEquals(DraftBackupResult.Cancelled, exportWorkspaceDraftBackup(DraftBackupRuntime { null }, "draft", { true }) {
            reads++; review
        })
        assertEquals(0, reads)
        val destination = Destination()
        var current = true
        assertFailsWith<IllegalStateException> {
            exportWorkspaceDraftBackup(DraftBackupRuntime { destination }, "draft", { current }) { current = false; review }
        }
        assertNull(destination.json)
        assertEquals(1, destination.closed)
        val denied = Destination()
        assertFailsWith<IllegalArgumentException> {
            exportWorkspaceDraftBackup(DraftBackupRuntime { denied }, "draft", { true }) { throw IllegalArgumentException("Read revoked") }
        }
        assertNull(denied.json)
        assertEquals(1, denied.closed)
        val cancelled = Destination()
        assertFailsWith<CancellationException> {
            exportWorkspaceDraftBackup(DraftBackupRuntime { cancelled }, "draft", { true }) { throw CancellationException() }
        }
        assertEquals(1, cancelled.closed)
        assertNull(cancelled.json)
    }

    @Test fun browserDispatchDoesNotClaimSavedAndStillRequiresFreshRead() = runTest {
        val destination = Destination()
        val runtime = DraftBackupRuntime(usesBrowserDownload = true) { destination }
        var read = false
        assertEquals(DraftBackupResult.DownloadRequested,
            exportWorkspaceDraftBackup(runtime, "draft", { true }) {
                assertNull(destination.json)
                read = true
                review
            })
        assertTrue(read)
        assertEquals(review.toBackupJson(), destination.json)
        assertEquals(1, destination.closed)
    }

    @Test fun finalWriteBoundaryAndWrongDraftFailClosed() = runTest {
        var current = true
        val destination = Destination().apply { beforeWrite = { current = false } }
        assertFailsWith<IllegalStateException> {
            exportWorkspaceDraftBackup(DraftBackupRuntime { destination }, "draft", { current }) { review }
        }
        assertNull(destination.json)
        assertEquals(1, destination.closed)
        val wrong = Destination()
        assertFailsWith<IllegalStateException> {
            exportWorkspaceDraftBackup(DraftBackupRuntime { wrong }, "draft", { true }) { review.copy(draftId = "wrong") }
        }
        assertNull(wrong.json)
        assertEquals(1, wrong.closed)
    }

    @Test fun cancellationAwaitsProviderDisposalAndPreservesOriginalFailure() = runTest {
        var disposed = false
        val original = CancellationException("Cancelled after picker")
        val destination = object : DraftBackupDestination {
            override suspend fun write(json: String, canWrite: () -> Boolean) { error("Must not write") }
            override fun close() { error("Must use awaited disposal") }
            override suspend fun dispose() {
                delay(1)
                disposed = true
                throw IllegalArgumentException("Provider cleanup failed")
            }
        }
        var caught: Throwable? = null
        val job = launch {
            try {
                exportWorkspaceDraftBackup(DraftBackupRuntime { destination }, "draft", { true }) {
                    currentCoroutineContext().cancel(original)
                    throw original
                }
            } catch (error: Throwable) { caught = error }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertSame(original, caught)
        assertTrue(disposed)
    }
}
