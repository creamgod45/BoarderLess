package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Real Chrome Blob/bitmap/canvas/Skia pixels; transport and authorization are fixtures. */
class BrowserExportImageTest {
    @Test fun exifOrientationSwapsBoundsAndRotatesJpegPixels() = runTest {
        val bytes = suspendCancellableCoroutine<ByteArray> { pending -> browserExportFixtureJpeg { if (pending.isActive) pending.resume(Base64.decode(it)) } }
        val gateway = Gateway(bytes, hash(bytes))
        gateway.metadata = gateway.metadata.copy(mediaType = "image/jpeg", width = 200, height = 100)
        val frame = loadBrowserExportImage(gateway, session(), "a", 400, 400)
        assertEquals(100, frame.width); assertEquals(200, frame.height)
        val top = IntArray(1); val bottom = IntArray(1)
        frame.readPixels(top, startX = 50, startY = 20, width = 1, height = 1)
        frame.readPixels(bottom, startX = 50, startY = 180, width = 1, height = 1)
        assertTrue(((top[0] ushr 16) and 255) > 240 && (top[0] and 255) < 10)
        assertTrue((bottom[0] and 255) > 240 && ((bottom[0] ushr 16) and 255) < 10)
        assertEquals(0, browserExportFixtureActive())
    }
    @Test fun streamedOriginalBeyondPreviewByteLimitCanDecodeWithoutKotlinConcatenation() = runTest {
        val bytes = suspendCancellableCoroutine<ByteArray> { pending -> browserExportFixtureTiny { if (pending.isActive) pending.resume(Base64.decode(it)) } }
        val padding = ByteArray(1024 * 1024)
        val expected = bytes.size.toLong() + padding.size.toLong() * 33
        val sink = BrowserExportImageSink("image/png", expected)
        val hash = SHA256()
        try {
            hash.update(bytes); sink.writeChunk(0, bytes)
            var offset = bytes.size.toLong()
            repeat(33) { hash.update(padding); sink.writeChunk(offset, padding); offset += padding.size }
            val checksum = "sha256:" + hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            sink.commit(expected, checksum)
            val frame = sink.frame(128, 128)
            assertEquals(16, frame.width); assertEquals(8, frame.height)
            val pixel = IntArray(1); frame.readPixels(pixel, width = 1, height = 1)
            assertEquals(0x8000ff00.toInt(), pixel[0])
        } finally { sink.release() }
        assertEquals(0, browserExportFixtureActive())
    }
    private fun session() = WorkspaceSession("u", "c", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Test"))
    private suspend fun original() = suspendCancellableCoroutine<ByteArray> { pending ->
        browserExportFixtureOriginal { if (pending.isActive) pending.resume(Base64.decode(it)) }
    }
    private fun hash(bytes: ByteArray) = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private class Gateway(val bytes: ByteArray, checksum: String) : AssetDownloadGateway {
        var allowed = true; var corrupt = false; var downloads = 0
        var metadata = WorkspaceAsset("a", WorkspaceId("w"), "u", "image/png", bytes.size.toLong(), checksum,
            6000, 2400, null, AssetStatus.Ready, "2026-10-08")
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            check(allowed); return AssetDownloadTicket(metadata, "https://storage.invalid/private")
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++
            val copy = bytes.copyOf(); if (corrupt) copy[copy.lastIndex] = (copy.last().toInt() xor 1).toByte()
            var offset = 0
            while (offset < copy.size) {
                val end = minOf(offset + 65536, copy.size); onChunk(copy.copyOfRange(offset, end)); offset = end
            }
        }
    }
    @Test fun fullOriginalExportKeepsEdgeDetailTransparencyAndDirection() = runTest {
        val bytes = original(); val gateway = Gateway(bytes, hash(bytes))
        val frame = loadBrowserExportImage(gateway, session(), "a", 2500, 1000)
        assertEquals(2500, frame.width); assertEquals(1000, frame.height)
        val pixel = IntArray(1)
        frame.readPixels(pixel, startX = 4, startY = 4, width = 1, height = 1); assertEquals(0xffff0000.toInt(), pixel[0])
        frame.readPixels(pixel, startX = 4, startY = 996, width = 1, height = 1); assertEquals(0xff0000ff.toInt(), pixel[0])
        frame.readPixels(pixel, startX = 1200, startY = 400, width = 1, height = 1); assertEquals(0x8000ff00.toInt(), pixel[0])
        val row = IntArray(53); frame.readPixels(row, startX = 2447, startY = 980, width = 53, height = 1)
        val transitions = row.map { ((it ushr 16) and 255) >= 128 }.zipWithNext().count { it.first != it.second }
        assertTrue(transitions >= 14, "Edge detail lost: $transitions transitions")
        assertEquals(0, browserExportFixtureActive())
        gateway.allowed = false
        assertFailsWith<IllegalStateException> { loadBrowserExportImage(gateway, session(), "a", 2500, 1000) }
        assertEquals(1, gateway.downloads); assertEquals(0, browserExportFixtureActive())
    }
    @Test fun checksumAndScopeFailuresCannotPublishPixelsOrKeepHandles() = runTest {
        val bytes = original(); val gateway = Gateway(bytes, hash(bytes))
        gateway.metadata = gateway.metadata.copy(workspaceId = WorkspaceId("other"))
        assertFailsWith<IllegalArgumentException> { loadBrowserExportImage(gateway, session(), "a", 2500, 1000) }
        assertEquals(0, gateway.downloads); assertEquals(0, browserExportFixtureActive())
        gateway.metadata = gateway.metadata.copy(workspaceId = WorkspaceId("w")); gateway.corrupt = true
        assertFailsWith<IllegalStateException> { loadBrowserExportImage(gateway, session(), "a", 2500, 1000) }
        assertEquals(0, browserExportFixtureActive())
    }
    @Test fun cancellationDuringNativeDecodeDropsHandleAndClosesLateBitmap() = runTest {
        withContext(Dispatchers.Default) {
        val bytes = original(); val gateway = Gateway(bytes, hash(bytes))
        browserExportFixtureInstallDelay()
        try {
            val job = launch { loadBrowserExportImage(gateway, session(), "a", 2500, 1000) }
            withTimeout(5000) { while (!browserExportFixtureWaiting()) delay(10) }
            job.cancelAndJoin(); assertEquals(0, browserExportFixtureActive())
            suspendCancellableCoroutine<Unit> { pending -> browserExportFixtureCompleteDelay { if (pending.isActive) pending.resume(Unit) } }
            assertEquals(1, browserExportFixtureCloses()); assertEquals(0, browserExportFixtureActive())
        } finally { browserExportFixtureRestore() }
        }
    }
}

internal expect fun browserExportFixtureOriginal(done: (String) -> Unit)
internal expect fun browserExportFixtureJpeg(done: (String) -> Unit)
internal expect fun browserExportFixtureTiny(done: (String) -> Unit)
internal expect fun browserExportFixtureActive(): Int
internal expect fun browserExportFixtureInstallDelay()
internal expect fun browserExportFixtureWaiting(): Boolean
internal expect fun browserExportFixtureCompleteDelay(done: () -> Unit)
internal expect fun browserExportFixtureCloses(): Int
internal expect fun browserExportFixtureRestore()
