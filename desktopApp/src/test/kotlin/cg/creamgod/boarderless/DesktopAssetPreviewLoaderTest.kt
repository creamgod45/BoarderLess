package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetDownloadGateway
import cg.creamgod.boarderless.data.AssetDownloadTicket
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.bitmapPreviewCache
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopAssetPreviewLoaderTest {
    @Test
    fun readyPngIsVerifiedDecodedAndTemporaryFilesRemoved() =
        runBlocking {
            withCache { root ->
                val gateway = FakeGateway(png())
                val cache = bitmapPreviewCache()
                val image = DesktopAssetPreviewLoader(gateway, root, cache).load(session(), "image-1")
                assertEquals(2, image.width)
                assertEquals(3, image.height)
                assertSame(image, DesktopAssetPreviewLoader(gateway, root, cache).load(session(), "image-1"))
                assertEquals(2, gateway.authorizations)
                assertEquals(1, gateway.downloads)
                assertEmpty(root)
            }
        }

    @Test
    fun wrongWorkspaceOrAssetRejectedBeforeDownload() =
        runBlocking {
            withCache { root ->
                listOf(
                    asset(png()).copy(workspaceId = WorkspaceId("other")),
                    asset(png()).copy(id = "other"),
                ).forEach { metadata ->
                    val gateway = FakeGateway(png(), metadata)
                    assertFailsWith<IllegalArgumentException> {
                        DesktopAssetPreviewLoader(gateway, root).load(session(), "image-1")
                    }
                    assertEquals(0, gateway.downloads)
                    assertEmpty(root)
                }
            }
        }

    @Test
    fun pendingVideoOrOversizedPreviewRejectedBeforeDownload() =
        runBlocking {
            withCache { root ->
                val bytes = png()
                listOf(
                    asset(bytes).copy(status = AssetStatus.Pending),
                    asset(bytes).copy(mediaType = "video/mp4"),
                    asset(bytes).copy(byteSize = DesktopAssetPreviewLoader.MaxPreviewEncodedBytes + 1),
                ).forEach { metadata ->
                    val gateway = FakeGateway(bytes, metadata)
                    assertFailsWith<IllegalArgumentException> {
                        DesktopAssetPreviewLoader(gateway, root).load(session(), "image-1")
                    }
                    assertEquals(0, gateway.downloads)
                    assertEmpty(root)
                }
            }
        }

    @Test
    fun checksumMismatchAndTruncationNeverProducePreview() =
        runBlocking {
            withCache { root ->
                val bytes = png()
                listOf(
                    asset(bytes).copy(checksum = "sha256:wrong"),
                    asset(bytes).copy(byteSize = bytes.size.toLong() + 1),
                ).forEach { metadata ->
                    assertFailsWith<IllegalStateException> {
                        DesktopAssetPreviewLoader(FakeGateway(bytes, metadata), root).load(session(), "image-1")
                    }
                    assertEmpty(root)
                }
            }
        }

    @Test
    fun cancellationPropagatesAndCleansPartialDownload() =
        runBlocking {
            withCache { root ->
                val gateway = FakeGateway(png(), cancelAfterChunk = true)
                assertFailsWith<CancellationException> {
                    DesktopAssetPreviewLoader(gateway, root).load(session(), "image-1")
                }
                assertEmpty(root)
            }
        }

    @Test
    fun verifiedButCorruptImageIsRejectedAndCleaned() =
        runBlocking {
            withCache { root ->
                assertFailsWith<IllegalArgumentException> {
                    DesktopAssetPreviewLoader(FakeGateway(byteArrayOf(1, 2, 3)), root).load(session(), "image-1")
                }
                assertEmpty(root)
            }
        }

    @Test
    fun largeDecodedDimensionsRejectedBeforePixelAllocation() {
        val bytes = png()
        // Valid IHDR CRC, but 16M pixels: inspection must fail before allocating/decompressing pixels.
        ByteBuffer.wrap(bytes).putInt(16, 4000).putInt(20, 4000)
        val crc = CRC32().apply { update(bytes, 12, 17) }.value.toInt()
        ByteBuffer.wrap(bytes).putInt(29, crc)
        val error = assertFailsWith<IllegalArgumentException> { DesktopAssetPreviewLoader.decodePreview(bytes) }
        assertTrue(error.message.orEmpty().contains("safe decode limit"))
    }

    private suspend fun withCache(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-preview-test-")
        try {
            block(root)
        } finally {
            // Tests require the loader to leave this empty, including all failure paths.
            Files.delete(root)
        }
    }

    private fun assertEmpty(root: Path) = Files.list(root).use { assertEquals(0L, it.count()) }

    private fun png(): ByteArray =
        ByteArrayOutputStream().use { out ->
            ImageIO.write(BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB), "png", out)
            out.toByteArray()
        }

    private fun session() =
        WorkspaceSession(
            "viewer-1",
            "client-1",
            WorkspaceMemberRole.Viewer,
            0,
            0,
            Workspace(WorkspaceId("workspace-1"), "Media"),
        )

    private fun asset(bytes: ByteArray) =
        WorkspaceAsset(
            "image-1",
            WorkspaceId("workspace-1"),
            "owner-1",
            "image/png",
            bytes.size.toLong(),
            "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            2,
            3,
            null,
            AssetStatus.Ready,
            "2026-10-01T00:00:00Z",
        )

    private inner class FakeGateway(
        private val bytes: ByteArray,
        private val metadata: WorkspaceAsset = asset(bytes),
        private val cancelAfterChunk: Boolean = false,
    ) : AssetDownloadGateway {
        var authorizations = 0
        var downloads = 0

        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(metadata, "https://storage.invalid/signed")
        }

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            downloads++
            onChunk(bytes.copyOfRange(0, 1))
            if (cancelAfterChunk) throw CancellationException("user cancelled")
            if (bytes.size > 1) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }
}
