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
    @Test fun exportPlanCoversAllSourceTilesBeyondViewportLimit() {
        val request = ViewportImageRequest(0f, 0f, 1f, 1f, 2500, 1000)
        val viewport = planImageTiles(6000, 2400, request)
        val export = planImageTiles(6000, 2400, request, completeImage = true)
        assertEquals(16, viewport.size); assertEquals(18, export.size)
        assertEquals(6000L * 2400, export.sumOf { it.first.width.toLong() * it.first.height })
        assertTrue(export.all { it.second == 2 })
        val thin = planImageTiles(60000, 1000, request.copy(displayWidth = 4000, displayHeight = 1000), true)
        assertTrue(thin.all { it.second == 8 })
        assertEquals(60000L * 1000, thin.sumOf { it.first.width.toLong() * it.first.height })
        val full = planImageTiles(10000, 10000, request.copy(displayWidth = 10000, displayHeight = 10000), true)
        assertTrue(full.size > 128)
        assertEquals(100_000_000L, full.sumOf { it.first.width.toLong() * it.first.height })
    }

    @Test fun oversizedOriginalExportHasEdgeDetailAndDoesNotDoubleCompositeAlpha() = runBlocking {
        val source = BufferedImage(6000, 2400, BufferedImage.TYPE_INT_ARGB)
        val graphics = source.createGraphics()
        val bytes = try {
            graphics.color = java.awt.Color(0, 255, 0, 128); graphics.fillRect(0, 0, 6000, 2400)
            for (stripe in 0 until 16) {
                graphics.color = if (stripe % 2 == 0) java.awt.Color.BLACK else java.awt.Color.WHITE
                graphics.fillRect(5872 + stripe * 8, 2272, 8, 128)
            }
            ByteArrayOutputStream().also { assertTrue(ImageIO.write(source, "png", it)) }.toByteArray()
        } finally { graphics.dispose(); source.flush() }
        val root = Files.createTempDirectory("export-image-test-"); val loader = DesktopViewportImageLoader(root)
        try {
            val gateway = Gateway(bytes, 6000, 2400)
            gateway.metadata = gateway.metadata.copy(mediaType = "image/png")
            val frame = loader.loadExportImage(gateway, session(), "a", 2500, 1000)
            assertEquals(2500, frame.width); assertEquals(1000, frame.height)
            val pixel = IntArray(1)
            for (x in listOf(3, 423, 850, 1280, 1700, 2140, 2497)) {
                frame.readPixels(pixel, startX = x, startY = 400, width = 1, height = 1)
                assertEquals(0x8000ff00.toInt(), pixel[0])
            }
            val row = IntArray(53); frame.readPixels(row, startX = 2447, startY = 980, width = 53, height = 1)
            val transitions = row.map { ((it ushr 16) and 255) >= 128 }.zipWithNext().count { it.first != it.second }
            assertTrue(transitions >= 14, "Fine edge stripes must survive full-image export: $transitions transitions")
            val large = loader.loadExportImage(gateway, session(), "a", 5000, 2000)
            assertEquals(5000, large.width); assertEquals(2000, large.height)
            large.readPixels(pixel, startX = 4900, startY = 400, width = 1, height = 1)
            assertEquals(0x8000ff00.toInt(), pixel[0])
            gateway.allowed = false
            assertFailsWith<IllegalStateException> { loader.loadExportImage(gateway, session(), "a", 2500, 1000) }
            assertEquals(1, gateway.downloads); assertEquals(3, gateway.authorizations)
        } finally { loader.clear(); Files.delete(root) }
    }

    @Test fun jpegExifAllEightDirectionsMatchViewportAndExportAcrossTileEdges() = runBlocking {
        val source = BufferedImage(1024, 640, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        val jpeg = try {
            for ((index, color) in listOf(java.awt.Color.RED, java.awt.Color.GREEN, java.awt.Color.BLUE, java.awt.Color.YELLOW).withIndex()) {
                graphics.color = color; graphics.fillRect((index % 2) * 512, (index / 2) * 320, 512, 320)
            }
            ByteArrayOutputStream().also { assertTrue(ImageIO.write(source, "jpeg", it)) }.toByteArray()
        } finally { graphics.dispose(); source.flush() }
        val corners = listOf("RGBY", "GRYB", "YBGR", "BYRG", "RBGY", "BRYG", "YGBR", "GYRB")
        val root = Files.createTempDirectory("exif-viewport-test-"); val loader = DesktopViewportImageLoader(root)
        try {
            for (orientation in 1..8) {
                // Both TIFF byte orders, plus an unrelated XMP APP1 before EXIF.
                val tiff = if (orientation <= 4) byteArrayOf(73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
                    else byteArrayOf(77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,orientation.toByte(),0,0,0,0,0,0)
                val payload = byteArrayOf(69,120,105,102,0,0) + tiff
                val length = payload.size + 2
                val xmp = "http://ns.adobe.com/xap/1.0/\u0000fixture".encodeToByteArray()
                val bytes = jpeg.copyOfRange(0,2) + byteArrayOf(0xff.toByte(),0xe1.toByte(),0,(xmp.size+2).toByte()) + xmp +
                    byteArrayOf(0xff.toByte(),0xe1.toByte(),(length shr 8).toByte(),length.toByte()) + payload + jpeg.copyOfRange(2,jpeg.size)
                val gateway = Gateway(bytes, 1024,640)
                val w = if (orientation >= 5) 640 else 1024; val h = if (orientation >= 5) 1024 else 640
                val frame = loader.loadExportImage(gateway, session(), "a", w,h)
                assertEquals(w,frame.width,"orientation $orientation width"); assertEquals(h,frame.height)
                val tiles = mutableListOf<ImagePreviewTile>()
                loader.load(gateway, session(), "a", ViewportImageRequest(0f,0f,1f,1f,w,h)) { tiles += it }
                for ((index,point) in listOf(0.1f to 0.1f,0.9f to 0.1f,0.1f to 0.9f,0.9f to 0.9f).withIndex()) {
                    val pixel = IntArray(1)
                    frame.readPixels(pixel,startX=(point.first*w).toInt(),startY=(point.second*h).toInt(),width=1,height=1)
                    val expected = corners[orientation-1][index]
                    fun color(argb: Int): Char {
                        val r=(argb ushr 16) and 255; val g=(argb ushr 8) and 255; val b=argb and 255
                        return when { r>180 && g>180 && b<80 -> 'Y'; r>180 && g<80 && b<80 -> 'R'; g>180 && r<80 && b<80 -> 'G'; b>180 && r<80 && g<80 -> 'B'; else -> '?' }
                    }
                    assertEquals(expected,color(pixel[0]),"export orientation $orientation corner $index")
                    val tile=tiles.last { point.first>=it.left && point.first<it.right && point.second>=it.top && point.second<it.bottom }
                    assertEquals(w,tile.sourceWidth); assertEquals(h,tile.sourceHeight)
                    tile.bitmap.readPixels(pixel,startX=((point.first-tile.left)/(tile.right-tile.left)*tile.bitmap.width).toInt(),startY=((point.second-tile.top)/(tile.bottom-tile.top)*tile.bitmap.height).toInt(),width=1,height=1)
                    assertEquals(expected,color(pixel[0]),"viewport orientation $orientation corner $index")
                }
                assertEquals(1,gateway.downloads); assertEquals(2,gateway.authorizations)
                loader.clear()
            }
        } finally { loader.clear(); Files.delete(root) }
    }

    @Test fun jpegOrientationHeaderIsBoundedAndRejectsTruncation() {
        val root=Files.createTempDirectory("exif-header-test-"); val file=root.resolve("fixture")
        try {
            Files.write(file,byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xe1.toByte(),0,32,69,120))
            assertFailsWith<IllegalStateException> { desktopJpegOrientation(file) }
            Files.write(file,byteArrayOf(0xff.toByte(),0xd8.toByte())+ByteArray(1024*1024+1) { 0xff.toByte() })
            assertFailsWith<IllegalStateException> { desktopJpegOrientation(file) }
            Files.write(file,byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xda.toByte()))
            assertEquals(1,desktopJpegOrientation(file))
        } finally { Files.deleteIfExists(file); Files.delete(root) }
    }

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
