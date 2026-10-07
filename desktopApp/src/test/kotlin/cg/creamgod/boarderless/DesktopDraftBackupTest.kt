package cg.creamgod.boarderless

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class DesktopDraftBackupTest {
    @Test fun writesUtf8OnceAndNeverOverwritesExistingFile() =
        runBlocking {
            val directory = Files.createTempDirectory("boarderless-draft-backup-test")
            val target = directory.resolve("backup.json")
            try {
                val destination = DesktopDraftBackupDestination(target)
                destination.write("{\"text\":\"私密🙂\"}") { true }
                assertEquals("{\"text\":\"私密🙂\"}", Files.readString(target))
                assertFails { destination.write("overwrite") { true } }
                destination.close()
                assertFails { DesktopDraftBackupDestination(target).write("overwrite") { true } }
                assertEquals("{\"text\":\"私密🙂\"}", Files.readString(target))
            } finally {
                Files.deleteIfExists(target)
                Files.deleteIfExists(directory)
            }
        }

    @Test fun failedFinalScopeGuardDoesNotCreateOrLeavePartialFile() =
        runBlocking {
            val directory = Files.createTempDirectory("boarderless-draft-scope-test")
            val target = directory.resolve("backup.json")
            try {
                assertFails { DesktopDraftBackupDestination(target).write("private") { false } }
                assertFalse(Files.exists(target))
                var checks = 0
                assertFails { DesktopDraftBackupDestination(target).write("private") { ++checks < 2 } }
                assertFalse(Files.exists(target))
                val closed = DesktopDraftBackupDestination(target)
                closed.close()
                assertFails { closed.write("private") { true } }
                assertFalse(Files.exists(target))
            } finally {
                Files.deleteIfExists(target)
                Files.deleteIfExists(directory)
            }
        }
}
