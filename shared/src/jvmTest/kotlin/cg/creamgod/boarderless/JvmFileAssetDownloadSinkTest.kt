package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.JvmFileAssetDownloadSink
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class JvmFileAssetDownloadSinkTest {
    @Test
    fun verifiedDownloadIsAtomicallyExposed() = runBlocking {
        val directory = Files.createTempDirectory("boarderless-cache-")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val sink = JvmFileAssetDownloadSink.create(directory.toString(), "asset:1", "image/png")
            sink.writeChunk(0, byteArrayOf(1, 2))
            sink.writeChunk(2, byteArrayOf(3, 4))

            val local = sink.commit(bytes.size.toLong(), checksum(bytes))

            val target = Path(local.token)
            assertEquals("image/png", local.mediaType)
            assertContentEquals(bytes, Files.readAllBytes(target))
            assertFalse(Files.list(directory).use { files -> files.anyMatch { it.fileName.toString().endsWith(".part") } })
        } finally {
            deleteTree(directory)
        }
        Unit
    }

    @Test
    fun checksumMismatchDoesNotExposeTargetAndCanAbort() = runBlocking {
        val directory = Files.createTempDirectory("boarderless-cache-")
        try {
            val sink = JvmFileAssetDownloadSink.create(directory.toString(), "asset-2", "video/mp4")
            sink.writeChunk(0, byteArrayOf(9, 8, 7))

            assertFailsWith<IllegalStateException> { sink.commit(3, "sha256:wrong") }
            sink.abort()

            assertFalse(Files.exists(directory.resolve("asset-2.mp4")))
            assertFalse(Files.list(directory).use { files -> files.findAny().isPresent })
        } finally {
            deleteTree(directory)
        }
        Unit
    }

    private fun checksum(bytes: ByteArray): String = "sha256:" +
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    private fun deleteTree(directory: java.nio.file.Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
