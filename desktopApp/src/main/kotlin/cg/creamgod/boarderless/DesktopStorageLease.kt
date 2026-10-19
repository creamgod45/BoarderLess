package cg.creamgod.boarderless

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopStorageBusyException : IOException("Another Desktop writer holds the storage lease")

/** Lifetime lease for cooperating Desktop APP processes, acquired before creating APP/settings.
 * This does NOT exclude older builds, tools or other writers that ignore this protocol. It is
 * not authority to migrate/reset storage. The stable lock is never deleted or replaced.
 */
internal class DesktopStorageLease private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    fun requireHeld() {
        check(!closed.get() && lock.isValid && channel.isOpen)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                lock.release()
            } finally {
                channel.close()
            }
        }
    }

    companion object {
        fun acquire(rootPath: Path): DesktopStorageLease {
            require(rootPath.isAbsolute)
            val root = rootPath.normalize()
            val posix = Files.getFileStore(root.parent).supportsFileAttributeView("posix")
            val directoryAttributes =
                if (posix) {
                    arrayOf(
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
                    )
                } else {
                    emptyArray()
                }
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(root, *directoryAttributes)
                } catch (
                    _: FileAlreadyExistsException,
                ) {
                    // Verify the winner, never repair its permissions.
                }
            }
            check(!Files.isSymbolicLink(root) && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            if (posix) {
                check(
                    Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS) ==
                        PosixFilePermissions.fromString("rwx------"),
                )
            }
            val file = root.resolve("lifetime-writer.lock")
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                check(!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                if (posix) {
                    check(
                        Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS) ==
                            PosixFilePermissions.fromString("rw-------"),
                    )
                }
            }
            val attributes =
                if (posix) {
                    arrayOf(
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
                    )
                } else {
                    emptyArray()
                }
            val channel =
                FileChannel.open(
                    file,
                    setOf<OpenOption>(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS,
                    ),
                    *attributes,
                )
            try {
                val lock =
                    try {
                        channel.tryLock() ?: throw DesktopStorageBusyException()
                    } catch (
                        _: OverlappingFileLockException,
                    ) {
                        throw DesktopStorageBusyException()
                    }
                return DesktopStorageLease(channel, lock)
            } catch (failure: Exception) {
                channel.close()
                throw failure
            }
        }
    }
}
