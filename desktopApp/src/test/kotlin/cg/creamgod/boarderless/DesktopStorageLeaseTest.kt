package cg.creamgod.boarderless

import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopStorageLeaseTest {
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-storage-lease-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun child(
        root: Path,
        mode: String,
    ): Process {
        val classpath =
            listOf(DesktopStorageLease::class.java, DesktopStorageLeaseProcess::class.java, Unit::class.java)
                .map {
                    Path
                        .of(
                            it.protectionDomain.codeSource.location
                                .toURI(),
                        ).toString()
                }.distinct()
                .joinToString(File.pathSeparator)
        return ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            classpath,
            DesktopStorageLeaseProcess::class.java.name,
            root.toString(),
            mode,
        ).redirectErrorStream(true).start()
    }

    private fun line(process: Process) =
        CompletableFuture
            .supplyAsync {
                process.inputStream.bufferedReader().readLine()
            }.get(15, TimeUnit.SECONDS)

    @Test fun sameProcessCannotAcquireTwiceAndCloseKeepsStableLockFile() =
        fixture { root ->
            val lease = DesktopStorageLease.acquire(root)
            lease.requireHeld()
            val file = root.resolve("lifetime-writer.lock")
            val inode = Files.readAttributes(file, BasicFileAttributes::class.java).fileKey()
            assertFailsWith<DesktopStorageBusyException> { DesktopStorageLease.acquire(root) }
            lease.requireHeld()
            lease.close()
            lease.close()
            assertFails { lease.requireHeld() }
            DesktopStorageLease.acquire(root).use { it.requireHeld() }
            assertEquals(inode, Files.readAttributes(file, BasicFileAttributes::class.java).fileKey())
            assertEquals(0L, Files.size(file))
        }

    @Test fun independentJvmIsExcludedWhileParentHoldsTheLease() =
        fixture { root ->
            DesktopStorageLease.acquire(root).use { lease ->
                val process = child(root, "probe")
                try {
                    assertEquals("BUSY", line(process))
                    assertTrue(process.waitFor(15, TimeUnit.SECONDS))
                    assertEquals(0, process.exitValue())
                    lease.requireHeld()
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                        process.waitFor(15, TimeUnit.SECONDS)
                    }
                }
            }
            val process = child(root, "probe")
            try {
                assertEquals("ACQUIRED", line(process))
                assertTrue(process.waitFor(15, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(15, TimeUnit.SECONDS)
                }
            }
        }

    @Test fun killedJvmReleasesLeaseWithoutDeletingItsStableFile() =
        fixture { root ->
            val process = child(root, "hold")
            try {
                assertEquals("HELD", line(process))
                val file = root.resolve("lifetime-writer.lock")
                val inode = Files.readAttributes(file, BasicFileAttributes::class.java).fileKey()
                assertFailsWith<DesktopStorageBusyException> { DesktopStorageLease.acquire(root) }
                process.destroyForcibly()
                assertTrue(process.waitFor(15, TimeUnit.SECONDS))
                DesktopStorageLease.acquire(root).use { it.requireHeld() }
                assertEquals(inode, Files.readAttributes(file, BasicFileAttributes::class.java).fileKey())
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(15, TimeUnit.SECONDS)
                }
            }
        }

    @Test fun symlinkOrNonPrivateLockIsRejectedWithoutRepairOrTruncation() =
        fixture { parent ->
            if (!Files.getFileStore(parent).supportsFileAttributeView("posix")) return@fixture
            val target = parent.resolve("target")
            DesktopStorageLease.acquire(target).close()
            val symlink = parent.resolve("alias")
            Files.createSymbolicLink(symlink, target)
            assertFails { DesktopStorageLease.acquire(symlink) }
            assertTrue(Files.isSymbolicLink(symlink))
            val file = target.resolve("lifetime-writer.lock")
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
            assertFails { DesktopStorageLease.acquire(target) }
            assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(file))
            Files.delete(file)
            Files.createSymbolicLink(file, parent.resolve("unrelated"))
            assertFails { DesktopStorageLease.acquire(target) }
            assertTrue(Files.isSymbolicLink(file))
            assertFalse(Files.exists(parent.resolve("unrelated")))
        }

    @Test fun focusAndOpenProtocolRequiresExactTokenAndCommand() {
        var focused = 0
        val opened = mutableListOf<String>()
        val id = "09ffed45-d34c-43c9-a52f-434b32c9db3f"

        fun send(command: String?) = dispatchDesktopInstanceCommand(command, "secret", opened::add) { focused++ }
        assertTrue(send("secret focus"))
        assertTrue(send("secret open $id"))
        for (command in listOf(null, "wrong focus", "secret focus extra", "secret open invalid/path", "secret", "secret  focus")) {
            assertFalse(send(command))
        }
        assertEquals(1, focused)
        assertEquals(listOf(id), opened)
    }
}

internal object DesktopStorageLeaseProcess {
    @JvmStatic fun main(args: Array<String>) {
        val lease =
            try {
                DesktopStorageLease.acquire(Path.of(args[0]))
            } catch (_: DesktopStorageBusyException) {
                println("BUSY")
                return
            }
        lease.use {
            if (args[1] == "hold") {
                println("HELD")
                System.out.flush()
                System.`in`.read() // Parent terminates this isolated JVM; no APP or user Settings.
            } else {
                println("ACQUIRED")
            }
        }
    }
}
