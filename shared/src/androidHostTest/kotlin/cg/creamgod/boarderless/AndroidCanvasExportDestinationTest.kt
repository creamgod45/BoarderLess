package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AndroidCanvasExportDocument
import cg.creamgod.boarderless.data.StagedAndroidCanvasExportDestination
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.*

class AndroidCanvasExportDestinationTest {
    private class Document(var blank: Boolean = true, val fail: Boolean = false, val onWrite: () -> Unit = {}) : AndroidCanvasExportDocument {
        val bytes = ByteArrayOutputStream()
        var opened = false
        var discarded = false
        override fun isBlank() = blank
        override fun openOutput(): OutputStream {
            opened = true
            return object : OutputStream() {
                override fun write(value: Int) { bytes.write(value); if (fail) throw IOException("provider failure") }
                override fun write(buffer: ByteArray, offset: Int, count: Int) {
                    if (fail) { bytes.write(buffer, offset, 1); throw IOException("provider failure") }
                    bytes.write(buffer, offset, count)
                    onWrite()
                }
            }
        }
        override suspend fun discardBlank() { check(blank); discarded = true }
    }

    @Test fun chunksStayPrivateUntilCommitAndCommittedDocumentSurvivesAbort() = runBlocking {
        val cache = Files.createTempDirectory("canvas-export-test").toFile()
        try {
            val doc = Document(); val sink = StagedAndroidCanvasExportDestination(cache, { true }, doc)
            val first = ByteArray(1024 * 1024) { (it % 251).toByte() }; val last = byteArrayOf(3, 7, 9)
            sink.writeChunk(first); sink.writeChunk(last)
            assertFalse(doc.opened); assertEquals(1, cache.listFiles()!!.size)
            sink.commit(); sink.abort()
            assertContentEquals(first + last, doc.bytes.toByteArray()); assertFalse(doc.discarded)
            assertEquals(0, cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }

    @Test fun staleScopeBeforePublicationRemovesStageAndBlankDocument() = runBlocking {
        val cache = Files.createTempDirectory("canvas-export-test").toFile()
        try {
            var current = true; val doc = Document(); val sink = StagedAndroidCanvasExportDestination(cache, { current }, doc)
            sink.writeChunk(byteArrayOf(1, 2)); current = false
            assertFailsWith<IllegalStateException> { sink.commit() }; sink.abort()
            assertFalse(doc.opened); assertTrue(doc.discarded); assertEquals(0, cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }

    @Test fun nonemptyDocumentIsNeverOpenedOrDeleted() = runBlocking {
        val cache = Files.createTempDirectory("canvas-export-test").toFile()
        try {
            val doc = Document(blank = false); val sink = StagedAndroidCanvasExportDestination(cache, { true }, doc)
            sink.writeChunk(byteArrayOf(1, 2))
            assertFailsWith<IllegalStateException> { sink.commit() }
            assertFailsWith<IllegalStateException> { sink.abort() }
            assertFalse(doc.opened); assertFalse(doc.discarded); assertEquals(0, cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }

    @Test fun providerFailureRetainsUncertainOutputButRemovesPrivateStage() = runBlocking {
        val cache = Files.createTempDirectory("canvas-export-test").toFile()
        try {
            val doc = Document(fail = true); val sink = StagedAndroidCanvasExportDestination(cache, { true }, doc)
            sink.writeChunk(byteArrayOf(1, 2, 3))
            assertFailsWith<IOException> { sink.commit() }; sink.abort()
            assertContentEquals(byteArrayOf(1), doc.bytes.toByteArray()); assertFalse(doc.discarded)
            assertEquals(0, cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }

    @Test fun scopeChangeDuringPublicationStopsSubsequentChunksAndRetainsUncertainOutput() = runBlocking {
        val cache = Files.createTempDirectory("canvas-export-test").toFile()
        try {
            var current = true
            val doc = Document(onWrite = { current = false })
            val sink = StagedAndroidCanvasExportDestination(cache, { current }, doc)
            sink.writeChunk(ByteArray(1024 * 1024) { 5 }); sink.writeChunk(byteArrayOf(7))
            assertFailsWith<IllegalStateException> { sink.commit() }; sink.abort()
            assertEquals(1024 * 1024, doc.bytes.size()); assertFalse(doc.discarded)
            assertEquals(0, cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }
}
