package cg.creamgod.boarderless.data.remote

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileLock

/** Application-lifetime cross-process exclusion for cooperating server factories.
 * Shared within this process; NOT exclusion between repositories in the same process.
 * Never removes a lock file or retries a failed lock by switching storage identity. */
internal class AndroidServerWriterLease private constructor(private val lockFile: File,
    private val stream: FileOutputStream, private val lock: FileLock,
    private val device: Long, private val inode: Long) {
    fun requireActive() {
        check(stream.channel.isOpen && lock.isValid) { "Server storage writer lease lost" }
        val stat = Os.lstat(lockFile.absolutePath)
        check(stat.st_uid == Os.getuid() && stat.st_mode and 0xf000 == 0x8000 &&
            stat.st_mode and 0x1ff == 0x180 && stat.st_dev == device && stat.st_ino == inode)
    }
    companion object {
        private val leases = mutableMapOf<String, AndroidServerWriterLease>()
        fun acquire(root: File): AndroidServerWriterLease = synchronized(leases) {
            check(root.isAbsolute && root.canonicalFile == root.absoluteFile)
            leases[root.absolutePath]?.let { it.requireActive(); return@synchronized it }
            if (!root.exists()) {
                Os.mkdir(root.absolutePath, 0x1c0)
                forceDirectory(checkNotNull(root.parentFile))
            }
            val directory = Os.lstat(root.absolutePath)
            check(directory.st_uid == Os.getuid() && directory.st_mode and 0xf000 == 0x4000 &&
                directory.st_mode and 0x1ff == 0x1c0)
            val file = File(root, "server-writer.lock")
            val fd = Os.open(file.absolutePath, OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW, 0x180)
            val stream = FileOutputStream(fd)
            try {
                val stat = Os.fstat(fd)
                check(stat.st_uid == Os.getuid() && stat.st_mode and 0xf000 == 0x8000 && stat.st_mode and 0x1ff == 0x180)
                val lock = checkNotNull(stream.channel.tryLock()) { "Server storage is owned by another process" }
                try {
                    Os.fsync(fd); forceDirectory(root)
                    val lease = AndroidServerWriterLease(file, stream, lock, stat.st_dev, stat.st_ino)
                    lease.requireActive(); leases[root.absolutePath] = lease
                    lease
                } catch (failure: Throwable) { lock.release(); throw failure }
            } catch (failure: Throwable) { stream.close(); throw failure }
        }
        private fun forceDirectory(directory: File) {
            val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            try { check(Os.fstat(fd).st_mode and 0xf000 == 0x4000); Os.fsync(fd) }
            finally { Os.close(fd) }
        }
    }
}
