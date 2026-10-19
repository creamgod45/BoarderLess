package cg.creamgod.boarderless.data

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions

/**
 * Owner-only file helpers for the desktop stores. POSIX file systems get 0700/0600 permissions and
 * the permission check; file systems without a POSIX view (Windows/NTFS) rely on the per-user
 * profile ACLs, so creation attributes and the permission comparison are skipped there.
 */
internal object PrivateFileSystem {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun isPosix(path: Path): Boolean {
        var existing: Path? = path.toAbsolutePath()
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.parent
        return existing != null && Files.getFileStore(existing).supportsFileAttributeView("posix")
    }

    fun directoryAttributes(path: Path): Array<FileAttribute<*>> =
        if (isPosix(path)) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } else {
            emptyArray()
        }

    fun fileAttributes(path: Path): Array<FileAttribute<*>> =
        if (isPosix(path)) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            emptyArray()
        }

    fun requirePrivateDirectory(path: Path) {
        check(!Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        if (isPosix(path)) {
            check(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS) == PosixFilePermissions.fromString("rwx------"))
        }
    }

    /**
     * Flushes directory metadata after a rename. Windows cannot open a directory as a channel, and its
     * ATOMIC_MOVE is already a write-through MoveFileEx, so the flush is skipped there.
     */
    fun forceDirectory(path: Path) {
        try {
            FileChannel.open(path, StandardOpenOption.READ).use { it.force(true) }
        } catch (failure: IOException) {
            if (!(windows && failure is AccessDeniedException)) throw failure
        }
    }
}
