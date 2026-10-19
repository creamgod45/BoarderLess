@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.coordinateIosCanvasExportWrite
import cg.creamgod.boarderless.data.writeIosCanvasExportNewFile
import cg.creamgod.boarderless.domain.export.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.test.*

/** Native filesystem / Foundation coordinator evidence; not external provider or picker QA. */
class IosCanvasExportTest {
    private fun read(path: String): ByteArray {
        val data = checkNotNull(NSFileManager.defaultManager.contentsAtPath(path))
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }
    private fun directory() = (NSTemporaryDirectory() + "canvas-export-test-${NSUUID().UUIDString}").also {
        check(NSFileManager.defaultManager.createDirectoryAtPath(it, false, null, null))
    }
    private fun entries(path: String) = checkNotNull(NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null))

    @Test fun actualPngAndPdfFilesMatchEncodingAndNeverOverwrite() {
        val directory = directory()
        try {
            val raster = CanvasExportRaster(2, 2, intArrayOf(0xff80ff00.toInt(), 0x018001ff, 0, -1))
            for (format in CanvasExportFormat.entries) {
                val bytes = raster.encode(format); val path = "$directory/fixture.${format.extension}"
                writeIosCanvasExportNewFile(path, listOf(bytes.copyOfRange(0, 9), bytes.copyOfRange(9, bytes.size))) { true }
                assertContentEquals(bytes, read(path))
                val attributes = checkNotNull(NSFileManager.defaultManager.attributesOfItemAtPath(path, null))
                assertEquals(384, (attributes[NSFilePosixPermissions] as NSNumber).intValue and 511)
                assertFails { writeIosCanvasExportNewFile(path, listOf(byteArrayOf(1, 2, 3))) { true } }
                assertContentEquals(bytes, read(path))
            }
            assertEquals(2, entries(directory).size) // No hidden staging files after collision.
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }

    @Test fun guardFailureAtEachPrePublicationBoundaryRemovesOnlyStage() {
        val directory = directory()
        try {
            for (boundary in 1..4) {
                var checks = 0
                assertFails { writeIosCanvasExportNewFile("$directory/fixture.png", listOf(byteArrayOf(1, 2, 3))) { ++checks < boundary } }
                assertEquals(boundary, checks)
                assertTrue(entries(directory).isEmpty())
            }
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }

    @Test fun multiChunkBinaryPreservesEveryByteAndPublishesSingleFile() {
        val directory = directory()
        try {
            val first = ByteArray(1024 * 1024) { (it % 251).toByte() }; val last = byteArrayOf(-128, -1, 0, 3)
            val path = "$directory/fixture.pdf"
            writeIosCanvasExportNewFile(path, listOf(first, last)) { true }
            assertContentEquals(first + last, read(path)); assertEquals(listOf("fixture.pdf"), entries(directory))
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }

    @Test fun nativeCoordinatorPublishesBinaryWithoutReplacingExistingFile() = runBlocking {
        withContext(Dispatchers.Default) {
            val directory = directory()
            try {
                val path = "$directory/fixture.png"; val bytes = byteArrayOf(-119, 80, 78, 71, 0, -1)
                coordinateIosCanvasExportWrite(NSURL.fileURLWithPath(path), listOf(bytes)) { true }
                assertContentEquals(bytes, read(path))
                assertFails { coordinateIosCanvasExportWrite(NSURL.fileURLWithPath(path), listOf(byteArrayOf(7))) { true } }
                assertContentEquals(bytes, read(path)); assertEquals(listOf("fixture.png"), entries(directory))
            } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
        }
    }
}
