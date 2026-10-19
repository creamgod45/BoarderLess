package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.desktopCanvasExportDestination
import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.feature.canvas.publishCanvasExport
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopCanvasExportPublicationTest {
    @Test fun completePngAndPdfArePublishedNewOnlyAndStaleScopeLeavesNoFile() = runTest {
        val root = Files.createTempDirectory("boarderless-export-test-")
        try {
            val raster = CanvasExportRaster(3, 2, intArrayOf(-1, 0xffff0000.toInt(), 0x80123456.toInt(), 0, 0xff00ff00.toInt(), 0xff0000ff.toInt()))
            for (format in CanvasExportFormat.entries) {
                val target = root.resolve("canvas.${format.extension}")
                val file = desktopCanvasExportDestination(target.toString()) { true }
                assertFalse(Files.exists(target))
                publishCanvasExport(raster, format, file) { true }
                val expected = raster.encode(format)
                assertContentEquals(expected, Files.readAllBytes(target))
                if (format == CanvasExportFormat.Png) assertEquals(3, ImageIO.read(target.toFile()).width)
                assertFails {
                    publishCanvasExport(raster, format, desktopCanvasExportDestination(target.toString()) { true }) { true }
                }
                assertContentEquals(expected, Files.readAllBytes(target))
            }
            val stale = root.resolve("stale.png"); var allowed = true
            val file = desktopCanvasExportDestination(stale.toString()) { allowed }
            file.writeChunk(raster.encode(CanvasExportFormat.Png)); assertFalse(Files.exists(stale))
            allowed = false
            assertFailsWith<IllegalStateException> { file.commit() }
            file.abort()
            assertFalse(Files.exists(stale))
            Files.list(root).use { entries -> assertEquals(setOf("canvas.png", "canvas.pdf"), entries.map { it.fileName.toString() }.toList().toSet()) }
        } finally {
            Files.list(root).use { it.forEach(Files::delete) }; Files.delete(root)
        }
    }
}
