package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CanvasExportPublicationTest {
    @Test fun scopeChangeDuringWriteNeverCommitsAndAborts() = runTest {
        var allowed = true; var chunks = 0; var committed = false; var aborted = false
        val destination = object : CanvasExportDestination {
            override suspend fun writeChunk(bytes: ByteArray) {
                assertTrue(bytes.size <= 1024 * 1024); chunks++; allowed = false
            }
            override suspend fun commit() { committed = true }
            override suspend fun abort() { aborted = true }
        }
        assertFailsWith<IllegalStateException> {
            publishCanvasExport(CanvasExportRaster(512, 512, IntArray(512 * 512) { -1 }), CanvasExportFormat.Png, destination) { allowed }
        }
        assertEquals(1, chunks); assertFalse(committed); assertTrue(aborted)
    }

    @Test fun cancelledWriteAbortsAndKeepsCancellation() = runTest {
        var aborted = false
        val destination = object : CanvasExportDestination {
            override suspend fun writeChunk(bytes: ByteArray) { throw CancellationException("fixture") }
            override suspend fun commit() { fail("Cannot commit cancelled export") }
            override suspend fun abort() { aborted = true; error("cleanup fixture") }
        }
        val failure = assertFailsWith<CancellationException> {
            publishCanvasExport(CanvasExportRaster(1, 1, intArrayOf(-1)), CanvasExportFormat.Pdf, destination) { true }
        }
        assertEquals("fixture", failure.message); assertTrue(aborted)
        assertEquals(1, failure.suppressedExceptions.size)
    }
}
