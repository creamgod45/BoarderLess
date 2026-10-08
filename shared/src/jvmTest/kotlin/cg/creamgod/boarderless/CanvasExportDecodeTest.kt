package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.*

class CanvasExportDecodeTest {
    @Test fun pngIndependentDecoderKeepsArgbAcrossDeflateBlocks() {
        for ((w, h) in listOf(3 to 2, 257 to 257)) {
            val pixels = IntArray(w * h) { i ->
                val alpha = (i * 29) and 255
                (alpha shl 24) or (((i * 71) and 255) shl 16) or (((i * 13) and 255) shl 8) or ((i * 101) and 255)
            }
            val png = CanvasExportRaster(w, h, pixels).encode(CanvasExportFormat.Png)
            val decoded = ImageIO.read(ByteArrayInputStream(png))
            assertEquals(w, decoded.width); assertEquals(h, decoded.height)
            assertContentEquals(pixels, decoded.getRGB(0, 0, w, h, null, 0, w))
            var offset = 8
            fun int32(at: Int) = (0..3).fold(0) { n, k -> (n shl 8) or (png[at + k].toInt() and 255) }
            while (offset < png.size) {
                val length = int32(offset)
                val crc = CRC32().apply { update(png, offset + 4, length + 4) }
                assertEquals(crc.value.toInt(), int32(offset + length + 8))
                offset += length + 12
            }
            assertEquals(png.size, offset)
        }
    }

    @Test fun pdfOffsetsLengthsAndOptionalVisualFixture() {
        val w = 128; val h = 96
        val pixels = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x < 32 && y < 32 -> 0xffff0000.toInt()
                x >= 96 && y < 32 -> 0xff00ff00.toInt()
                x < 32 && y >= 64 -> 0xff0000ff.toInt()
                x >= 96 && y >= 64 -> 0xffffff00.toInt()
                x in 40..87 && y in 32..63 -> 0x808000ff.toInt()
                else -> 0x00112233
            }
        }
        val raster = CanvasExportRaster(w, h, pixels)
        val bytes = raster.encode(CanvasExportFormat.Pdf)
        val text = bytes.toString(Charsets.ISO_8859_1)
        val start = Regex("startxref\\n(\\d+)\\n%%EOF").find(text)!!.groupValues[1].toInt()
        assertTrue(text.substring(start).startsWith("xref\n"))
        val entries = text.substring(start).lineSequence().drop(3).take(6).toList()
        entries.forEachIndexed { i, entry ->
            val at = entry.take(10).toInt()
            assertTrue(text.substring(at).startsWith("${i + 1} 0 obj\n"))
        }
        Regex("/Length (\\d+) >>\\nstream\\n").findAll(text).forEach { match ->
            val length = match.groupValues[1].toInt()
            assertTrue(text.substring(match.range.last + 1 + length).startsWith("\nendstream\n"))
        }
        System.getenv("BOARDERLESS_EXPORT_FIXTURE_DIR")?.let { directory ->
            val base = Path.of(directory)
            require(base.isAbsolute)
            Files.createDirectories(base)
            val fixture = Files.createTempDirectory(base, "raster-")
            Files.write(fixture.resolve("canvas-export.pdf"), bytes)
            Files.write(fixture.resolve("canvas-export.png"), raster.encode(CanvasExportFormat.Png))
            println("Canvas export fixture: $fixture")
        }
    }
}
