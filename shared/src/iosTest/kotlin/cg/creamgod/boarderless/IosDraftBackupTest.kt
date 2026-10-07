@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.writeIosDraftBackupNewFile
import cg.creamgod.boarderless.data.coordinateIosDraftBackupWrite
import cg.creamgod.boarderless.data.IosDraftCoordinationFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.test.*

/** Real simulator filesystem tests, NOT external File Provider/picker/security-scope evidence. */
class IosDraftBackupTest {
    private fun path() = NSTemporaryDirectory() + "boarderless-draft-test-${NSUUID().UUIDString}.json"
    private fun read(path: String): String {
        val data = checkNotNull(NSFileManager.defaultManager.contentsAtPath(path))
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes.decodeToString()
    }

    @Test fun exclusiveUtf8FileWithPrivatePermissionsNeverOverwrites() {
        val path = path()
        try {
            writeIosDraftBackupNewFile(path, "{\"text\":\"私密🙂\"}") { true }
            assertEquals("{\"text\":\"私密🙂\"}", read(path))
            val attributes = checkNotNull(NSFileManager.defaultManager.attributesOfItemAtPath(path, null))
            assertEquals(384, (attributes[NSFilePosixPermissions] as NSNumber).intValue and 511)
            assertFails { writeIosDraftBackupNewFile(path, "overwrite") { true } }
            assertEquals("{\"text\":\"私密🙂\"}", read(path))
        } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }

    @Test fun guardFailureBeforeAndAfterCreationDoesNotLeavePartialFiles() {
        val path = path()
        try {
            assertFails { writeIosDraftBackupNewFile(path, "private") { false } }
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
            var checks = 0
            assertFails { writeIosDraftBackupNewFile(path, "private") { ++checks < 2 } }
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
        } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }

    @Test fun finalGuardFailureRemovesOnlyTheNewFile() {
        val path = path()
        try {
            var checks = 0
            assertFails { writeIosDraftBackupNewFile(path, "private") { ++checks < 4 } }
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
        } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }

    @Test fun foundationCoordinatorWritesNewFileAndPreservesExistingContents() = runBlocking {
        withContext(Dispatchers.Default) {
            val path = path()
            val target = NSURL.fileURLWithPath(path)
            try {
                try {
                    coordinateIosDraftBackupWrite(target, "{\"text\":\"私密🙂\"}") { true }
                } catch (error: IosDraftCoordinationFailure) {
                    // Native details only for this randomly named sandbox fixture, never UI/logged user URLs.
                    fail("Sandbox coordination: ${error.nativeError?.localizedDescription}; ${error.nativeError?.userInfo}")
                }
                assertEquals("{\"text\":\"私密🙂\"}", read(path))
                assertFails { coordinateIosDraftBackupWrite(target, "overwrite") { true } }
                assertEquals("{\"text\":\"私密🙂\"}", read(path))
            } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        }
    }

    @Test fun foundationCoordinatorRejectsExpiredScopeBeforeCreatingFile() = runBlocking {
        withContext(Dispatchers.Default) {
            val path = path()
            val target = NSURL.fileURLWithPath(path)
            try {
                assertFails { coordinateIosDraftBackupWrite(target, "private") { false } }
                assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
                var checks = 0
                assertFails { coordinateIosDraftBackupWrite(target, "private") { ++checks < 3 } }
                assertEquals(3, checks) // Reached accessor, expired before opening its destination.
                assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
            } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        }
    }
}
