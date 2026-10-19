package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.imageExportSize
import kotlin.test.*

class ImageExportSizeTest {
    @Test fun oversizedSourceFitsEntireOutputWithoutPreviewLimit() {
        val large = imageExportSize(6000, 2400, 2500, 1000)
        assertEquals(2500, large.width); assertEquals(1000, large.height)
        val portrait = imageExportSize(2400, 6000, 2500, 1000)
        assertEquals(400, portrait.width); assertEquals(1000, portrait.height)
        val tiny = imageExportSize(2, 1, 4096, 1024)
        assertEquals(2, tiny.width); assertEquals(1, tiny.height)
    }
    @Test fun extremeAspectRatioStillAllocatesBoundedNonzeroPixels() {
        val narrow = imageExportSize(1, 65535, 1, 4096)
        assertEquals(1, narrow.width); assertEquals(4096, narrow.height)
        val flat = imageExportSize(65535, 1, 1, 1)
        assertEquals(1, flat.width); assertEquals(1, flat.height)
    }
    @Test fun invalidHeadersAndExcessiveOutputRejectBeforeNativeAllocation() {
        for ((width, height) in listOf(0 to 1, 1 to -1, 65536 to 1, 65535 to 65535)) {
            assertFailsWith<IllegalArgumentException> { imageExportSize(width, height, 1, 1) }
        }
        for ((width, height) in listOf(0 to 1, 1 to -1, 4097 to 1, 4096 to 4096)) {
            assertFailsWith<IllegalArgumentException> { imageExportSize(1, 1, width, height) }
        }
    }
}
