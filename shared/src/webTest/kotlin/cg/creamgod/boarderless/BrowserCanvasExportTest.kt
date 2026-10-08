package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.BrowserCanvasExportDestination
import cg.creamgod.boarderless.domain.export.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.test.*

/** Real Blob bytes and DOM dispatch interception; not proof of browser disk storage. */
class BrowserCanvasExportTest {
    private suspend fun blobHex() = suspendCancellableCoroutine<String> { pending ->
        browserBackupFixtureHex { if (pending.isActive) pending.resume(it) }
    }
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Test fun pngAndPdfProduceExactBinaryBlobWithCorrectMimeAndRelease() = runTest {
        val raster = CanvasExportRaster(2, 2, intArrayOf(0xff80ff00.toInt(), 0x018001ff, 0x00000000, 0xffffffff.toInt()))
        for (format in CanvasExportFormat.entries) {
            browserBackupFixtureInstall()
            try {
                val expected = raster.encode(format)
                val sink = BrowserCanvasExportDestination("fixture.${format.extension}", format) { true }
                sink.writeChunk(expected.copyOfRange(0, 9)); sink.writeChunk(expected.copyOfRange(9, expected.size))
                assertEquals("0", browserBackupFixtureValue("created"))
                sink.commit(); sink.abort()
                assertEquals(expected.hex(), blobHex())
                assertEquals(format.mediaType, browserBackupFixtureValue("type"))
                assertEquals("fixture.${format.extension}", browserBackupFixtureValue("filename"))
                assertEquals("1", browserBackupFixtureValue("clicks")); assertEquals("0", browserBackupFixtureValue("anchors"))
                assertEquals("1", browserBackupFixtureValue("urls"))
                browserBackupFixtureExpire()
                assertEquals("0", browserBackupFixtureValue("urls")); assertEquals("0", browserBackupFixtureValue("timers"))
            } finally { browserBackupFixtureRestore() }
        }
    }

    @Test fun chunksOwnTheirBytesIncludingZeroAndHighBitValues() = runTest {
        browserBackupFixtureInstall()
        try {
            val source = ByteArray(65536) { (it % 256).toByte() }; val expected = source.copyOf() + byteArrayOf(-128, 0, -1)
            val sink = BrowserCanvasExportDestination("fixture.png", CanvasExportFormat.Png) { true }
            sink.writeChunk(source); source.fill(0)
            sink.writeChunk(byteArrayOf(-128, 0, -1)); sink.commit()
            assertEquals(expected.hex(), blobHex())
        } finally { browserBackupFixtureRestore() }
    }

    @Test fun expiredScopeAfterObjectUrlCreationCleansResourcesWithoutClick() = runTest {
        browserBackupFixtureInstall()
        try {
            val sink = BrowserCanvasExportDestination("fixture.pdf", CanvasExportFormat.Pdf) { browserBackupFixtureValue("created") == "0" }
            sink.writeChunk(byteArrayOf(1, 2, 3))
            assertFails { sink.commit() }; sink.abort()
            assertEquals("0", browserBackupFixtureValue("clicks")); assertEquals("1", browserBackupFixtureValue("revoked"))
            assertEquals("0", browserBackupFixtureValue("anchors")); assertEquals("0", browserBackupFixtureValue("urls"))
            assertEquals("0", browserBackupFixtureValue("timers"))
            assertFails { sink.commit() }
        } finally { browserBackupFixtureRestore() }
    }

    @Test fun abortAndDispatchFailureNeverRetryOrLeak() = runTest {
        browserBackupFixtureInstall()
        try {
            val aborted = BrowserCanvasExportDestination("fixture.png", CanvasExportFormat.Png) { true }
            aborted.writeChunk(byteArrayOf(1)); aborted.abort()
            assertFails { aborted.commit() }; assertEquals("0", browserBackupFixtureValue("created"))
            browserBackupFixtureFailClick()
            val failed = BrowserCanvasExportDestination("fixture.png", CanvasExportFormat.Png) { true }
            failed.writeChunk(byteArrayOf(1)); assertFails { failed.commit() }; failed.abort()
            assertFails { failed.commit() }
            assertEquals("0", browserBackupFixtureValue("clicks")); assertEquals("0", browserBackupFixtureValue("anchors"))
            assertEquals("0", browserBackupFixtureValue("urls")); assertEquals("0", browserBackupFixtureValue("timers"))
        } finally { browserBackupFixtureRestore() }
    }
}

internal expect fun browserBackupFixtureHex(done: (String) -> Unit)
