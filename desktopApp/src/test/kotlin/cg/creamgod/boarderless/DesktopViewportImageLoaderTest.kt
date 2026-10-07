package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopViewportImageLoaderTest {
    private fun session() = WorkspaceSession("u", "c", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Test"))

    private fun request() = ViewportImageRequest(.5f, 0f, 1f, .5f, 4000, 3200)

    private class Gateway(
        val bytes: ByteArray,
        width: Int = 4000,
        height: Int = 3200,
    ) : AssetDownloadGateway {
        var downloads = 0
        var authorizations = 0
        var allowed = true
        var metadata =
            WorkspaceAsset(
                "a",
                WorkspaceId("w"),
                "u",
                "image/jpeg",
                bytes.size.toLong(),
                "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                width,
                height,
                null,
                AssetStatus.Ready,
                "2026-10-02",
            )

        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ): AssetDownloadTicket {
            authorizations++
            check(allowed) { "Forbidden" }
            return AssetDownloadTicket(metadata, "https://storage.invalid/private")
        }

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            downloads++
            bytes.asList().chunked(65536).forEach { onChunk(it.toByteArray()) }
        }
    }

    private fun jpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return try {
            ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "jpeg", it)) }.toByteArray()
        } finally {
            image.flush()
        }
    }

    @Test fun highResolutionOriginalProducesCoarseThenVisibleTilesAndReusesVerifiedFile() =
        runBlocking {
            val root = Files.createTempDirectory("viewport-image-test-")
            val loader = DesktopViewportImageLoader(root)
            try {
                val gateway = Gateway(jpeg(4000, 3200)) // 12.8MP: old preview rejected it.
                val tiles = mutableListOf<ImagePreviewTile>()
                loader.load(gateway, session(), "a", request()) { tiles += it }
                assertTrue(tiles.first().bitmap.width <= 1024)
                assertEquals(4000, tiles.first().sourceWidth)
                assertTrue(tiles.size in 2..17)
                assertTrue(tiles.drop(1).all { it.bitmap.width <= 512 && it.bitmap.height <= 512 })
                assertTrue(tiles.drop(1).all { it.right > .5f && it.top < .5f })
                loader.load(gateway, session(), "a", request().copy(left = 0f, right = .5f)) { }
                assertEquals(1, gateway.downloads)
                assertEquals(2, gateway.authorizations)
                gateway.allowed = false
                var emitted = false
                assertFailsWith<IllegalStateException> { loader.load(gateway, session(), "a", request()) { emitted = true } }
                assertFalse(emitted)
            } finally {
                loader.clear()
                Files.delete(root)
            }
        }

    @Test fun checksumMismatchAndForeignScopeNeverExposeTiles() =
        runBlocking {
            val root = Files.createTempDirectory("viewport-image-test-")
            val loader = DesktopViewportImageLoader(root)
            try {
                val gateway = Gateway(jpeg(10, 10), 10, 10)
                gateway.metadata = gateway.metadata.copy(checksum = "sha256:wrong")
                assertFailsWith<IllegalStateException> { loader.load(gateway, session(), "a", request()) { fail("Unverified pixels") } }
                gateway.metadata = gateway.metadata.copy(workspaceId = WorkspaceId("other"))
                assertFailsWith<IllegalArgumentException> { loader.load(gateway, session(), "a", request()) { fail("Foreign pixels") } }
                assertEquals(1, gateway.downloads)
                assertEquals(0L, Files.list(root).use { it.count() })
            } finally {
                loader.clear()
                Files.delete(root)
            }
        }

    @Test fun cancellationStopsAfterFirstBatchAndClearRemovesOwnedCache() =
        runBlocking {
            val root = Files.createTempDirectory("viewport-image-test-")
            val loader = DesktopViewportImageLoader(root)
            try {
                var emitted = 0
                assertFailsWith<CancellationException> {
                    loader.load(Gateway(jpeg(100, 80), 100, 80), session(), "a", request()) {
                        emitted++
                        throw CancellationException("Viewport changed")
                    }
                }
                assertEquals(1, emitted)
                loader.clear()
                assertEquals(0L, Files.list(root).use { it.count() })
            } finally {
                loader.clear()
                Files.delete(root)
            }
        }

    @Test fun gridIsBoundedAndSamplingFollowsDisplaySize() {
        val full = ViewportImageRequest(0f, 0f, 1f, 1f, 8000, 6000)
        val plan = planImageTiles(8000, 6000, full)
        assertEquals(16, plan.size)
        assertTrue(plan.all { it.first.width <= 512 && it.first.height <= 512 && it.second == 1 })
        val small = planImageTiles(8000, 6000, full.copy(displayWidth = 400, displayHeight = 300))
        assertTrue(small.all { it.second >= 16 })
        assertTrue(small.size < plan.size)
    }

    @Test fun highResolutionPngRegionPreservesPixels() =
        runBlocking {
            val image = BufferedImage(4096, 3072, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            val bytes =
                try {
                    graphics.color = java.awt.Color.BLUE
                    graphics.fillRect(2048, 0, 2048, 3072)
                    ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "png", it)) }.toByteArray()
                } finally {
                    graphics.dispose()
                    image.flush()
                }
            val root = Files.createTempDirectory("viewport-image-test-")
            val loader = DesktopViewportImageLoader(root)
            try {
                val gateway = Gateway(bytes, 4096, 3072)
                gateway.metadata = gateway.metadata.copy(mediaType = "image/png")
                val tiles = mutableListOf<ImagePreviewTile>()
                loader.load(gateway, session(), "a", request().copy(displayWidth = 4096, displayHeight = 3072)) { tiles += it }
                val pixels = IntArray(1)
                tiles[1].bitmap.readPixels(pixels, width = 1, height = 1)
                assertEquals(0xff0000ff.toInt(), pixels[0])
                assertTrue(tiles.drop(1).all { it.left >= .5f })
            } finally {
                loader.clear()
                Files.delete(root)
            }
        }
}
