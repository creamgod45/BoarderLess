@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetImportException
import cg.creamgod.boarderless.data.AssetImportIssue
import cg.creamgod.boarderless.data.IosFileAssetTransferSource
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosFileAssetTransferSourceTest {
    @Test
    fun selectedFileIsHashedAndSnapshotCanBeReleasedWithoutDeletingOriginal() = runBlocking {
        val original = NSTemporaryDirectory() + "boarderless-test-${NSUUID().UUIDString}.png"
        val bytes = byteArrayOf(97, 98, 99)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(NSFileManager.defaultManager.createFileAtPath(original, data, null))
        try {
            val source = IosFileAssetTransferSource.fromUrl(NSURL.fileURLWithPath(original))
            try {
                assertEquals("image/png", source.mediaType)
                assertEquals(3L, source.byteSize)
                assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", source.checksum)
                assertContentEquals(byteArrayOf(98, 99), source.readChunk(1, 2))
                assertContentEquals(byteArrayOf(), source.readChunk(3, 2))
            } finally {
                source.release()
            }
            assertFailsWith<IllegalStateException> { source.readChunk(0, 1) }
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(original))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(original, null)
        }
    }

    @Test
    fun emptyFileIsRejectedAndFailedSnapshotIsRemoved() = runBlocking {
        val original = NSTemporaryDirectory() + "boarderless-test-${NSUUID().UUIDString}.gif"
        check(NSFileManager.defaultManager.createFileAtPath(original, null, null))
        try {
            val snapshotsBefore = snapshots()
            val error = assertFailsWith<AssetImportException> {
                IosFileAssetTransferSource.fromUrl(NSURL.fileURLWithPath(original))
            }
            assertEquals(AssetImportIssue.InvalidByteSize, error.issue)
            assertEquals(snapshotsBefore, snapshots())
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(original, null)
        }
    }

    private fun snapshots(): Set<String> = NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)
        .orEmpty().filterIsInstance<String>().filter { it.startsWith("boarderless-import-") }.toSet()
}
