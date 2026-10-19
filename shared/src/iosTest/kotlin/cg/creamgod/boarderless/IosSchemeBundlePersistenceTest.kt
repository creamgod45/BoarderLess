@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*
import kotlin.test.*

class IosSchemeBundlePersistenceTest {
    private fun root() = checkNotNull(NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path).trimEnd('/') + "/bundle-store-test-${NSUUID().UUIDString}"
    private fun remove(path: String) { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    private fun bytes(path: String): ByteArray {
        val data = checkNotNull(NSFileManager.defaultManager.contentsAtPath(path))
        return ByteArray(data.length.toInt()).also { result -> if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }
    private fun write(path: String, bytes: ByteArray) {
        bytes.usePinned { check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, atomically = false)) }
    }
    private fun mode(path: String) = memScoped { val info = alloc<stat>(); check(lstat(path, info.ptr) == 0); info.st_mode.toInt() and 511 }

    @Test fun nativeCasReopenPrivateModesAndCorruptionAreFenced() {
        val root = root(); val key = "a".repeat(64)
        try {
            val first = IosAtomicLocalRecordStore(root)
            first.compareAndSet(key, null, "{\"first\":true}".encodeToByteArray())
            assertEquals(448, mode(root)); assertEquals(384, mode("$root/$key.record"))
            val second = IosAtomicLocalRecordStore(root)
            assertEquals(1L, second.read(key)?.generation)
            second.confirmDurable(key, 1)
            second.compareAndSet(key, 1, "{\"second\":true}".encodeToByteArray())
            assertFailsWith<IllegalStateException> { first.compareAndSet(key, 1, "{}".encodeToByteArray()) }
            assertEquals(2L, first.read(key)?.generation)
            val path = "$root/$key.record"
            write(path, bytes(path).decodeToString().replace("second", "broken").encodeToByteArray())
            val corrupt = bytes(path)
            assertFails { second.read(key) }; assertFails { second.compareAndSet(key, null, "{}".encodeToByteArray()) }
            assertContentEquals(corrupt, bytes(path))
            assertFalse(NSFileManager.defaultManager.contentsOfDirectoryAtPath(root, null)!!.any { (it as String).endsWith(".partial") })
        } finally { remove(root) }
    }

    @Test fun symlinkAndUnsafeRootModesAreRejectedWithoutFollowingThem() {
        val root = root(); val key = "a".repeat(64)
        try {
            val store = IosAtomicLocalRecordStore(root); assertNull(store.read(key))
            val path = "$root/$key.record"
            check(symlink("$root/missing", path) == 0)
            assertFails { store.read(key) }; assertFails { store.compareAndSet(key, null, "{}".encodeToByteArray()) }
            check(unlink(path) == 0); check(chmod(root, 511u) == 0)
            assertFails { store.read(key) }; check(chmod(root, 448u) == 0)
        } finally { remove(root) }
    }

    @Test fun pendingIdentityReopensAndReadyRecoveryUsesOnlyRecordedId() = runBlocking {
        val root = root()
        try {
            val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 0, 0, Workspace(WorkspaceId("w"), "Destination"))
            val asset = SchemeBundleAsset("source", "image/png", 81, "sha256:" + "b".repeat(64), 1, 1, null)
            val ready = WorkspaceAsset("destination", owner.workspace.id, "u", asset.mediaType, asset.byteSize, asset.checksum, 1, 1, null, AssetStatus.Ready, "now")
            fun receipts() = PersistentSchemeBundleImportReceipts(IosAtomicLocalRecordStore(root), owner, "a".repeat(64), listOf(asset))
            receipts().beforeComplete(asset, "destination")
            var fresh = ready.copy(status = AssetStatus.Pending); var calls = 0
            val repository = object : AssetRepository {
                override suspend fun listAssets(session: WorkspaceSession) = error("must query exact recorded id")
                override suspend fun getAsset(session: WorkspaceSession, assetId: String): WorkspaceAsset {
                    assertEquals(owner, session); assertEquals("destination", assetId); calls++; return fresh
                }
            }
            assertFails { receipts().recovered(asset, repository) }
            assertFails { receipts().beforeComplete(asset, "another") }
            fresh = ready
            val recovered = assertNotNull(receipts().recovered(asset, repository))
            receipts().ready(asset, recovered); receipts().beforeComplete(asset, "destination")
            assertEquals(recovered, receipts().recovered(asset, repository))
            fresh = ready.copy(ownerId = "foreign")
            assertFails { receipts().recovered(asset, repository) }
            assertEquals(4, calls)
        } finally { remove(root) }
    }

    @Test fun nativeSchemeLibraryReopenPreservesNamesPayloadAndMonotonicIds() {
        val root = root()
        try {
            val payload = ClipboardJson.encodeToString(ClipboardPayload.serializer(), ClipboardPayload(version = 4,
                nodes = listOf(ClipboardNode("n", 0f, 0f, 120f, 80f, 0f, "中文🙂", "paper"))))
            val first = IosFileQuickSchemeStore(root).save(payload, "方案🙂", 4, "source")
            val reopened = IosFileQuickSchemeStore(root)
            assertEquals(listOf(first), reopened.list())
            val renamed = assertNotNull(reopened.rename(first.id, "已改名"))
            assertEquals(listOf(renamed), IosFileQuickSchemeStore(root).list())
            assertTrue(reopened.delete(first.id))
            val next = IosFileQuickSchemeStore(root).save(payload, "New", 4)
            assertEquals(2, next.id); assertEquals(listOf(next), reopened.list())
        } finally { remove(root) }
    }

    @Test fun interruptedSnapshotCleansPrivateCopyAndKeepsOriginalFile() = runBlocking {
        withContext(Dispatchers.Default) {
            val path = root() + ".blscheme"
            val original = ByteArray(1024 * 1024 + 19) { (it % 251).toByte() }
            try {
                write(path, original)
                fun snapshots() = NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)!!
                    .filter { (it as String).startsWith("boarderless-bundle-") }.toSet()
                val before = snapshots()
                for (cancel in listOf(false, true)) {
                    var checks = 0
                    assertFails {
                        IosSchemeBundleSource.snapshot(NSURL.fileURLWithPath(path)) {
                            if (++checks >= 4) {
                                if (cancel) throw CancellationException("fixture cancellation")
                                false
                            } else true
                        }
                    }
                    assertEquals(before, snapshots())
                    assertContentEquals(original, bytes(path))
                }
                Unit
            } finally { remove(path) }
        }
    }

    @Test fun nativePrivateSnapshotIgnoresSubsequentSourceChangesAndReleasesOnlyCopy() = runBlocking {
        withContext(Dispatchers.Default) {
            val path = root() + ".blscheme"
            val original = ByteArray(1024 * 1024 + 19) { (it % 251).toByte() }
            try {
                write(path, original)
                val snapshot = IosSchemeBundleSource.snapshot(NSURL.fileURLWithPath(path)) { true }
                write(path, byteArrayOf(1, 2, 3))
                assertEquals(original.size.toLong(), snapshot.byteSize)
                assertContentEquals(original.copyOfRange(1024 * 1024 - 10, original.size), snapshot.readChunk(1024L * 1024 - 10, 100))
                snapshot.release(); snapshot.release()
                assertFails { snapshot.readChunk(0, 1) }
                assertContentEquals(byteArrayOf(1, 2, 3), bytes(path))
                write(path, ByteArray(44))
                assertFails { IosSchemeBundleSource.snapshot(NSURL.fileURLWithPath(path)) { true } }
                Unit
            } finally { remove(path) }
        }
    }
}
