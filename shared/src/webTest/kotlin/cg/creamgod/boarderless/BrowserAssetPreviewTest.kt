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

class BrowserAssetPreviewTest {
    @Test
    fun verifiedChunksYieldUniqueReferenceAndCanBeConsumedOnlyOnce() = runTest {
        val bytes = byteArrayOf(97, 98, 99)
        val sink = BrowserAssetDownloadSink("image/png")
        sink.writeChunk(0, byteArrayOf(97))
        sink.writeChunk(1, byteArrayOf(98, 99))
        val local = sink.commit(3, checksum(bytes))
        assertTrue(local.token.startsWith("browser:"))
        assertEquals("image/png", local.mediaType)
        assertContentEquals(bytes, sink.takeVerifiedBytes())
        assertFailsWith<IllegalStateException> { sink.takeVerifiedBytes() }
        assertFailsWith<IllegalStateException> { sink.writeChunk(3, byteArrayOf(1)) }
    }

    @Test
    fun wrongChecksumOrSizeNeverExposesBytes() = runTest {
        listOf(3L to "sha256:wrong", 4L to checksum(byteArrayOf(1, 2, 3))).forEach { (size, hash) ->
            val sink = BrowserAssetDownloadSink("image/png")
            sink.writeChunk(0, byteArrayOf(1, 2, 3))
            assertFailsWith<IllegalStateException> { sink.commit(size, hash) }
            assertFailsWith<IllegalStateException> { sink.takeVerifiedBytes() }
            sink.abort()
            assertFailsWith<IllegalStateException> { sink.commit(3, hash) }
        }
    }

    @Test
    fun chunksAreCopiedAndMustBeNonemptySequentialAndUnreleased() = runTest {
        val sink = BrowserAssetDownloadSink("image/png")
        assertFailsWith<IllegalArgumentException> { sink.writeChunk(0, byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { sink.writeChunk(1, byteArrayOf(1)) }
        val bytes = byteArrayOf(1)
        sink.writeChunk(0, bytes)
        bytes[0] = 9
        sink.commit(1, checksum(byteArrayOf(1)))
        assertContentEquals(byteArrayOf(1), sink.takeVerifiedBytes())
        sink.abort()
        assertFailsWith<IllegalStateException> { sink.writeChunk(0, byteArrayOf(1)) }
    }

    @Test
    fun actualPngIsDecodedAfterAuthorizedVerifiedDownload() = runTest {
        withContext(Dispatchers.Default) {
            val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            val gateway = Gateway(bytes)
            val cache = bitmapPreviewCache()
            val image = BrowserAssetPreviewLoader(gateway, cache).load(session(), "asset-1")
            assertSame(image, BrowserAssetPreviewLoader(gateway, cache).load(session(), "asset-1"))
            assertEquals(1, image.width)
            assertEquals(1, image.height)
            assertEquals(2, gateway.authorizations)
            assertEquals(1, gateway.downloads)
        }
    }

    @Test
    fun cancelledOrCorruptDownloadNeverReturnsImage() = runTest {
        withContext(Dispatchers.Default) {
            assertFailsWith<CancellationException> {
                BrowserAssetPreviewLoader(Gateway(byteArrayOf(1, 2, 3), cancel = true)).load(session(), "asset-1")
            }
            assertFailsWith<IllegalArgumentException> {
                BrowserAssetPreviewLoader(Gateway(byteArrayOf(1, 2, 3))).load(session(), "asset-1")
            }
        }
    }

    @Test
    fun foreignAuthorizationIsRejectedBeforeBinaryDownload() = runTest {
        val gateway = Gateway(byteArrayOf(1), foreign = true)
        assertFailsWith<IllegalArgumentException> { BrowserAssetPreviewLoader(gateway).load(session(), "asset-1") }
        assertEquals(0, gateway.downloads)
    }

    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0,
        Workspace(WorkspaceId("workspace-1"), "Preview"))

    private inner class Gateway(private val bytes: ByteArray, private val cancel: Boolean = false, private val foreign: Boolean = false) : AssetDownloadGateway {
        var authorizations = 0
        var downloads = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(
                WorkspaceAsset("asset-1", if (foreign) WorkspaceId("foreign") else session.workspace.id, "owner", "image/png",
                    bytes.size.toLong(), checksum(bytes), 1, 1, null, AssetStatus.Ready, "2026-10-01"),
                "https://storage.invalid/signed",
            )
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++
            onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            if (bytes.size > 1) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }

    private fun checksum(bytes: ByteArray) = "sha256:" + SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
