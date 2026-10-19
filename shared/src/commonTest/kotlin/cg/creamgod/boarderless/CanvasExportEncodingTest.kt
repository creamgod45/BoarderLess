package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.*
import kotlin.test.*

class CanvasExportEncodingTest {
    @Test fun dimensionsPixelsAndFrozenInput() {
        assertFailsWith<IllegalArgumentException> { CanvasExportRaster(0, 1, intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { CanvasExportRaster(-1, 1, intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { CanvasExportRaster(4096, 4096, intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { CanvasExportRaster(2, 1, intArrayOf(0)) }
        val input = intArrayOf(0xffff0000.toInt())
        val frozen = CanvasExportRaster(1, 1, input)
        val png = frozen.encode(CanvasExportFormat.Png)
        val pdf = frozen.encode(CanvasExportFormat.Pdf)
        input[0] = 0xff00ff00.toInt()
        assertContentEquals(png, frozen.encode(CanvasExportFormat.Png))
        assertContentEquals(pdf, frozen.encode(CanvasExportFormat.Pdf))
        assertEquals("image/png", CanvasExportFormat.Png.mediaType)
        assertEquals("application/pdf", CanvasExportFormat.Pdf.mediaType)
    }

    @Test fun wideExportHasNoFixed8192PixelCeiling() {
        val raster = CanvasExportRaster(10000, 1, IntArray(10000) { -1 })
        assertTrue(raster.encode(CanvasExportFormat.Png).isNotEmpty())
        assertTrue("/MediaBox [0 0 10000 1]" in raster.encode(CanvasExportFormat.Pdf).decodeToString())
    }

    @Test fun opaquePdfOmitsMaskAndAlphaAddsMask() {
        val opaque = CanvasExportRaster(1, 1, intArrayOf(-1)).encode(CanvasExportFormat.Pdf).decodeToString()
        val transparent = CanvasExportRaster(1, 1, intArrayOf(0x80123456.toInt())).encode(CanvasExportFormat.Pdf).decodeToString()
        assertFalse("/SMask" in opaque)
        assertTrue("/SMask 6 0 R" in transparent)
        assertTrue("/MediaBox [0 0 1 1]" in transparent)
        assertTrue(transparent.endsWith("%%EOF\n"))
    }
}
