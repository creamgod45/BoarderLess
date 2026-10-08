@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import org.kotlincrypto.hash.sha2.SHA256
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.test.*

/** Native filesystem and Foundation coordination; picker/iCloud providers need separate GUI QA. */
class IosSchemeBundleDestinationTest {
    private fun directory() = (NSTemporaryDirectory() + "bundle-export-test-${NSUUID().UUIDString}").also {
        check(NSFileManager.defaultManager.createDirectoryAtPath(it, false, null, null))
    }
    private fun entries(path: String) = checkNotNull(NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null))
    private fun source(path: String) = object : SchemeBundleSource {
        override val byteSize = (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)!![NSFileSize] as NSNumber).longLongValue
        override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
            val file = checkNotNull(NSFileHandle.fileHandleForReadingAtPath(path))
            try {
                file.seekToFileOffset(offset.toULong())
                val data = file.readDataOfLength(minOf(maximumBytes.toLong(), byteSize - offset).toULong())
                return ByteArray(data.length.toInt()).also { bytes ->
                    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                }
            } finally { file.closeFile() }
        }
        override suspend fun release() = Unit
    }

    @Test fun fullOriginalAboveCanvasBudgetExportsAndReviewsByteForByte() = runBlocking {
        withContext(Dispatchers.Default) {
            val folder = directory(); val target = "$folder/fixture.blscheme"
            var privateStage: String? = null
            try {
                // Opaque original fixture checks the bundle transport, not an image decoder.
                val chunk = ByteArray(1024 * 1024) { (it % 251).toByte() }
                val checksum = "sha256:" + SHA256().also { hash -> repeat(33) { hash.update(chunk) } }.digest()
                    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                val owner = WorkspaceSession("owner", "client", WorkspaceMemberRole.Owner, 0, 0, Workspace(WorkspaceId("source"), "Source"))
                val asset = WorkspaceAsset("original", owner.workspace.id, "owner", "image/png", 33L * chunk.size, checksum, 1, 1, null, AssetStatus.Ready, "now")
                val payload = ClipboardPayload(nodes = emptyList(), media = listOf(ClipboardMedia("m", 0f, 0f, 10f, 10f, 0f, asset.id, "image")))
                val scheme = QuickScheme(3, "跨端🙂", ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), 6, "source")
                var published = false
                val destination = IosSchemeBundleDestination.create({ true }) { stage, bytes, guard ->
                    privateStage = stage
                    coordinateIosSchemeBundleWrite(stage, NSURL.fileURLWithPath(target), bytes, guard)
                    published = true
                }
                val gateway = object : AssetDownloadGateway {
                    override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
                        assertEquals(owner, session); assertEquals(asset.id, assetId)
                        return AssetDownloadTicket(asset, "https://private.invalid/signed")
                    }
                    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
                        repeat(33) { onChunk(chunk); assertFalse(published) }
                    }
                }
                QuickSchemeBundleCodec.export(scheme, owner, gateway, destination, { true }, { scheme })
                assertTrue(published)
                assertEquals(listOf("fixture.blscheme"), entries(folder))
                assertFalse(NSFileManager.defaultManager.fileExistsAtPath(checkNotNull(privateStage)))
                val nativeSnapshot = IosSchemeBundleSource.snapshot(NSURL.fileURLWithPath(target)) { true }
                val reviewed = QuickSchemeBundleCodec.review(nativeSnapshot) { true }
                assertEquals(scheme.copy(id = 0), reviewed.scheme)
                assertEquals(checksum, reviewed.assets.single().checksum)
                assertTrue(source(target).byteSize > 33L * chunk.size)
                destination.abort(); reviewed.release()
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(target))
                val mode = (NSFileManager.defaultManager.attributesOfItemAtPath(target, null)!![NSFilePosixPermissions] as NSNumber).intValue
                assertEquals(384, mode and 511)
            } finally { NSFileManager.defaultManager.removeItemAtPath(folder, null) }
        }
    }

    @Test fun scopeChangeNeverPublishesAndReleasesOwnedStage() = runBlocking {
        withContext(Dispatchers.Default) {
            var current = true; var published = false
            val before = entries(NSTemporaryDirectory()).filter { (it as String).startsWith("boarderless-bundle-") }.toSet()
            val destination = IosSchemeBundleDestination.create({ current }) { _, _, _ -> published = true }
            destination.writeChunk(ByteArray(1024 * 1024)); current = false
            assertFails { destination.commit() }
            destination.abort(); destination.abort()
            assertFalse(published)
            assertEquals(before, entries(NSTemporaryDirectory()).filter { (it as String).startsWith("boarderless-bundle-") }.toSet())
        }
    }

    @Test fun coroutineCancellationReleasesOnlyOwnedStageWithoutPublishing() = runBlocking {
        withContext(Dispatchers.Default) {
            val before = entries(NSTemporaryDirectory()).filter { (it as String).startsWith("boarderless-bundle-") }.toSet()
            var published = false
            val destination = IosSchemeBundleDestination.create({ true }) { _, _, _ -> published = true }
            val staged = CompletableDeferred<Unit>()
            val job = launch {
                try { destination.writeChunk(ByteArray(1024 * 1024)); staged.complete(Unit); awaitCancellation() }
                finally { destination.abort() }
            }
            staged.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled); assertFalse(published)
            assertEquals(before, entries(NSTemporaryDirectory()).filter { (it as String).startsWith("boarderless-bundle-") }.toSet())
        }
    }

    @Test fun exclusivePublicationCollisionKeepsExistingFileAndRemovesOnlyStages() = runBlocking {
        withContext(Dispatchers.Default) {
            val folder = directory(); val target = "$folder/fixture.blscheme"
            try {
                check(NSFileManager.defaultManager.createFileAtPath(target, NSData(), null))
                var privateStage = ""
                val destination = IosSchemeBundleDestination.create({ true }) { stage, bytes, guard ->
                    privateStage = stage
                    coordinateIosSchemeBundleWrite(stage, NSURL.fileURLWithPath(target), bytes, guard)
                }
                destination.writeChunk(ByteArray(45))
                assertFails { destination.commit() }; destination.abort()
                assertEquals(0L, source(target).byteSize)
                assertEquals(listOf("fixture.blscheme"), entries(folder))
                assertFalse(NSFileManager.defaultManager.fileExistsAtPath(privateStage))
            } finally { NSFileManager.defaultManager.removeItemAtPath(folder, null) }
        }
    }
}
