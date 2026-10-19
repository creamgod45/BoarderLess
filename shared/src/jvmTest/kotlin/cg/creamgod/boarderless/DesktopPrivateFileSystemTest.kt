package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.PrivateFileSystem
import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Comparator
import kotlin.test.*

class DesktopPrivateFileSystemTest {
    private val scope = "c".repeat(64)

    private fun <T> fixture(action: (Path) -> T): T {
        val root = Files.createTempDirectory("boarderless-private-fs-test-").toRealPath()
        try {
            return action(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private fun posix(path: Path) = Files.getFileStore(path).supportsFileAttributeView("posix")

    @Test fun storeCreatesMissingRootAndRoundTripsOnThisFileSystem(): Unit =
        fixture { parent ->
            val root = parent.resolve("local")
            val store = DesktopAtomicDraftStore(root)
            assertTrue(Files.isDirectory(root))
            assertEquals(1L, store.compareAndSet(scope, null, "payload".toByteArray()).generation)
            assertContentEquals("payload".toByteArray(), DesktopAtomicDraftStore(root).read(scope)!!.payload)
            assertEquals(setOf(scope), store.recordKeys())
            if (posix(root)) assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
        }

    @Test fun storeOverwritesExistingRecordWithoutDirectoryHandle(): Unit =
        fixture { parent ->
            val store = DesktopAtomicDraftStore(parent.resolve("local"))
            store.compareAndSet(scope, null, "one".toByteArray())
            assertEquals(2L, store.compareAndSet(scope, 1, "two".toByteArray()).generation)
            assertContentEquals("two".toByteArray(), store.read(scope)!!.payload)
        }

    @Test fun creationAttributesFollowThePosixCapabilityOfTheFileStore(): Unit =
        fixture { root ->
            val missing = root.resolve("not").resolve("created-yet")
            if (posix(root)) {
                assertEquals(1, PrivateFileSystem.directoryAttributes(missing).size)
                assertEquals(1, PrivateFileSystem.fileAttributes(missing).size)
            } else {
                assertEquals(0, PrivateFileSystem.directoryAttributes(missing).size)
                assertEquals(0, PrivateFileSystem.fileAttributes(missing).size)
            }
        }

    @Test fun privateDirectoryCheckRejectsFilesAndLoosePosixModes(): Unit =
        fixture { root ->
            val file = Files.createFile(root.resolve("plain"))
            assertFails { PrivateFileSystem.requirePrivateDirectory(file) }
            val directory = Files.createDirectory(root.resolve("dir"), *PrivateFileSystem.directoryAttributes(root))
            PrivateFileSystem.requirePrivateDirectory(directory)
            if (posix(root)) {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"))
                assertFails { PrivateFileSystem.requirePrivateDirectory(directory) }
            }
        }

    @Test fun forcingADirectoryNeverFailsOnThisPlatform(): Unit =
        fixture { root -> PrivateFileSystem.forceDirectory(root) }
}
