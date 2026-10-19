@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data.remote

import kotlinx.cinterop.*
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSURL
import platform.posix.*

/** Lifetime flock for cooperating iOS server factories. The process owns the descriptor
 * until termination; sharing it is not a same-process migration/cutover barrier. */
internal class IosServerWriterLease private constructor(private val path: String,
    private val fd: Int, private val device: Long, private val inode: Long) {
    fun requireActive() = memScoped {
        val info = alloc<stat>()
        check(fstat(fd, info.ptr) == 0); verify(info, false)
        check(info.st_dev.toLong() == device && info.st_ino.toLong() == inode)
        check(lstat(path, info.ptr) == 0); verify(info, false)
        check(info.st_dev.toLong() == device && info.st_ino.toLong() == inode)
        check(flock(fd, LOCK_EX or LOCK_NB) == 0) { "Server storage writer lease unavailable" }
    }
    companion object {
        private val processLock = NSRecursiveLock()
        private val leases = mutableMapOf<String, IosServerWriterLease>()
        private fun verify(info: stat, directory: Boolean) {
            check(info.st_uid == getuid())
            check(info.st_mode.toInt() and 0xf000 == if (directory) 0x4000 else 0x8000)
            check(info.st_mode.toInt() and 0x1ff == if (directory) 448 else 384)
            if (!directory) check(info.st_nlink.toInt() == 1)
        }
        private fun forceDirectory(path: String) {
            val fd = open(path, O_RDONLY or O_DIRECTORY or O_NOFOLLOW)
            check(fd >= 0)
            try { check(fsync(fd) == 0) } finally { check(close(fd) == 0) }
        }
        fun acquire(root: String): IosServerWriterLease {
            processLock.lock()
            try {
                require(root.startsWith('/') && '\u0000' !in root && root.last() != '/')
                check(NSURL.fileURLWithPath(root).URLByResolvingSymlinksInPath?.path == root)
                leases[root]?.let { it.requireActive(); return it }
                memScoped {
                    val info = alloc<stat>()
                    if (lstat(root, info.ptr) != 0) {
                        check(errno == ENOENT && mkdir(root, 448u) == 0)
                        forceDirectory(root.substringBeforeLast('/'))
                        check(lstat(root, info.ptr) == 0)
                    }
                    verify(info, true)
                }
                val path = "$root/server-writer.lock"
                val fd = open(path, O_RDWR or O_CREAT or O_NOFOLLOW or O_NONBLOCK, 384)
                check(fd >= 0)
                try {
                    return memScoped {
                        val info = alloc<stat>(); check(fstat(fd, info.ptr) == 0); verify(info, false)
                        check(flock(fd, LOCK_EX or LOCK_NB) == 0) { "Server storage is owned by another process" }
                        check(fsync(fd) == 0); forceDirectory(root)
                        val lease = IosServerWriterLease(path, fd, info.st_dev.toLong(), info.st_ino.toLong())
                        lease.requireActive(); leases[root] = lease
                        lease
                    }
                } catch (failure: Throwable) {
                    close(fd) // Closing also releases a successfully acquired flock.
                    throw failure
                }
            } finally { processLock.unlock() }
        }
    }
}
