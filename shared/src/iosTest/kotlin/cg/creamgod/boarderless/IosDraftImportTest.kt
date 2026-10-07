@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.symlink
import kotlin.test.*

/** Sandbox fd and real coordination tests, not external provider/grant/UIKit signoff. */
class IosDraftImportTest {
    private fun path() = NSTemporaryDirectory() + "boarderless-read-test-${NSUUID().UUIDString}.json"

    @Test fun readsUnicodeBomReadOnlyAndPreservesExistingFile() {
        val source = path()
        try {
            writeIosDraftBackupNewFile(source, "\uFEFF{\"text\":\"中文🙂\"}") { true }
            assertEquals("{\"text\":\"中文🙂\"}", readIosDraftJson(source) { true })
            assertEquals("{\"text\":\"中文🙂\"}", readIosDraftJson(source) { true })
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(source))
        } finally { NSFileManager.defaultManager.removeItemAtPath(source, null) }
    }

    @Test fun rejectsOversizeSymlinkAndExpiredScopeWithoutDeletingSource() {
        val source = path(); val link = path()
        try {
            writeIosDraftBackupNewFile(source, "private") { true }
            assertEquals(0, symlink(source, link))
            assertFails { readIosDraftJson(link) { true } }
            assertFails { readIosDraftJson(source) { false } }
            var checks = 0
            assertFails { readIosDraftJson(source) { ++checks < 3 } }
            assertEquals("private", readIosDraftJson(source) { true })
            NSFileManager.defaultManager.removeItemAtPath(source, null)
            writeIosDraftBackupNewFile(source, "x".repeat(4 * 1024 * 1024 + 1)) { true }
            assertFails { readIosDraftJson(source) { true } }
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(source))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(link, null)
            NSFileManager.defaultManager.removeItemAtPath(source, null)
        }
    }

    @Test fun realFoundationReadCoordinatorChecksScopeBeforeAndInsideAccessor() = runBlocking {
        withContext(Dispatchers.Default) {
            val source = path()
            try {
                writeIosDraftBackupNewFile(source, "{\"text\":\"中文🙂\"}") { true }
                val url = checkNotNull(NSURL.fileURLWithPath(source))
                assertEquals("{\"text\":\"中文🙂\"}", coordinateIosDraftRead(url) { true })
                assertFails { coordinateIosDraftRead(url) { false } }
                var checks = 0
                assertFails { coordinateIosDraftRead(url) { ++checks < 2 } }
                assertEquals(2, checks)
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(source))
            } finally { NSFileManager.defaultManager.removeItemAtPath(source, null) }
        }
    }

    @Test fun ownContainerNeedsNoExternalGrantAndNeighborPrefixDoesNotAuthorize() = runBlocking {
        val source = NSHomeDirectory() + "/boarderless-owned-${NSUUID().UUIDString}.json"
        try {
            writeIosDraftBackupNewFile(source, "{}") { true }
            val url = NSURL.fileURLWithPath(source)
            assertTrue(isOwnSandboxDraftUrl(url))
            assertFalse(isOwnSandboxDraftUrl(NSURL.fileURLWithPath(NSHomeDirectory() + "-other/backup.json")))
            assertEquals("{}", readIosExternalDraftJson(url) { true })
            assertFails { readIosExternalDraftJson(url) { false } }
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(source))
        } finally { NSFileManager.defaultManager.removeItemAtPath(source, null) }
    }
}
