package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.JvmFileAssetTransferSource
import java.nio.file.Files
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmFileAssetTransferSourceTest {
    @Test
    fun imageSourceReadsDimensionsAndKeepsOriginalBytes() = runBlocking {
        val path = Files.createTempFile("boarderless-image-", ".png")
        try {
            ImageIO.write(BufferedImage(37, 19, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
            val original = Files.readAllBytes(path)

            val source = JvmFileAssetTransferSource.fromPath(path.toString())

            assertEquals(37, source.width)
            assertEquals(19, source.height)
            assertContentEquals(original, source.readChunk(0, original.size))
        } finally {
            Files.deleteIfExists(path)
        }
        Unit
    }

    @Test
    fun fileSourceHashesAndReadsBoundedChunks() = runBlocking {
        val path = Files.createTempFile("boarderless-asset-", ".png")
        try {
            Files.write(path, byteArrayOf(1, 2, 3, 4, 5))

            val source = JvmFileAssetTransferSource.fromPath(path.toString())

            assertEquals("image/png", source.mediaType)
            assertEquals(5, source.byteSize)
            assertTrue(source.checksum.matches(Regex("sha256:[0-9a-f]{64}")))
            assertContentEquals(byteArrayOf(2, 3), source.readChunk(offset = 1, maximumBytes = 2))
            assertContentEquals(byteArrayOf(), source.readChunk(offset = 5, maximumBytes = 2))
        } finally {
            Files.deleteIfExists(path)
        }
        Unit
    }

    @Test
    fun changedFileIsRejectedBeforeReadingMoreData() = runBlocking {
        val path = Files.createTempFile("boarderless-asset-", ".gif")
        try {
            Files.write(path, byteArrayOf(1, 2, 3))
            val source = JvmFileAssetTransferSource.fromPath(path.toString())
            Files.write(path, byteArrayOf(1, 2, 3, 4))

            assertFailsWith<IllegalStateException> { source.readChunk(0, 2) }
        } finally {
            Files.deleteIfExists(path)
        }
        Unit
    }
}
