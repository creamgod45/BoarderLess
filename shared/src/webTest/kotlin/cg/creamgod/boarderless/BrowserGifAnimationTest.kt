package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.test.*

class BrowserGifAnimationTest {
    @Test fun actualGifHasDifferentPixelsAndPreservesTimingAndLoop() = runTest {
        withContext(Dispatchers.Default) {
            val gateway = Gateway(twoFrameGifFixture())
            val animation = loadBrowserGif(gateway, session(), "gif-1")
            try {
                assertEquals(1, gateway.downloads); assertEquals(1, gateway.authorizations)
                assertEquals(2, animation.frameCount); assertEquals(-1, animation.repetitionCount)
                assertEquals(50L, animation.durationMs(0)); assertEquals(100L, animation.durationMs(1))
                val first = animation.frame(0); val second = animation.frame(1)
                val red = IntArray(1); val blue = IntArray(1)
                first.readPixels(red); second.readPixels(blue)
                assertEquals(0xffff0000.toInt(), red[0]); assertEquals(0xff0000ff.toInt(), blue[0])
                first.readPixels(red); assertEquals(0xffff0000.toInt(), red[0])
                assertFailsWith<IllegalArgumentException> { animation.frame(2) }
            } finally { animation.release() }
            animation.release()
            assertFailsWith<IllegalStateException> { animation.frame(0) }
        }
    }
    @Test fun wrongChecksumTruncationOrCancellationCannotReturnDecoder() = runTest {
        assertFailsWith<IllegalStateException> { loadBrowserGif(Gateway(twoFrameGifFixture(), badHash = true), session(), "gif-1") }
        assertFailsWith<AssetDownloadException> { loadBrowserGif(Gateway(twoFrameGifFixture(), truncate = true), session(), "gif-1") }
        assertFailsWith<CancellationException> { loadBrowserGif(Gateway(twoFrameGifFixture(), cancel = true), session(), "gif-1") }
    }
    @Test fun foreignWorkspacePendingWrongMimeOrOversizedMetadataDoesNotDownload() = runTest {
        listOf(
            Gateway(twoFrameGifFixture(), foreign = true),
            Gateway(twoFrameGifFixture(), status = AssetStatus.Pending),
            Gateway(twoFrameGifFixture(), mime = "image/png"),
            Gateway(twoFrameGifFixture(), size = AssetPreviewPolicy.MaxEncodedBytes + 1),
        ).forEach { gateway ->
            assertFailsWith<IllegalArgumentException> { loadBrowserGif(gateway, session(), "gif-1") }
            assertEquals(0, gateway.downloads)
        }
    }
    @Test fun validlyHashedButCorruptOrNonGifContentIsRejected() = runTest {
        withContext(Dispatchers.Default) {
            assertFailsWith<IllegalArgumentException> { loadBrowserGif(Gateway(byteArrayOf(1, 2, 3)), session(), "gif-1") }
            val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            assertFailsWith<IllegalArgumentException> { loadBrowserGif(Gateway(png), session(), "gif-1") }
        }
    }
    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0,
        Workspace(WorkspaceId("w"), "GIF"))
    private inner class Gateway(private val bytes: ByteArray, private val cancel: Boolean = false,
        private val badHash: Boolean = false, private val foreign: Boolean = false, private val truncate: Boolean = false,
        private val status: AssetStatus = AssetStatus.Ready, private val mime: String = "image/gif", private val size: Long = bytes.size.toLong()) : AssetDownloadGateway {
        var downloads = 0; var authorizations = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(
                WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", mime, size,
                    if (badHash) "sha256:wrong" else checksum(bytes), 1, 1, null, status, "2026-10-02"),
                "https://storage.invalid/gif",
            )
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            if (!truncate) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }
    private fun checksum(bytes: ByteArray) = "sha256:" + SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
