@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.parseStrictJsonObject
import kotlinx.cinterop.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import platform.Foundation.*
import platform.posix.*

/** Private records, process lock + flock, strict checksums and generation-fenced publication. */
internal class IosAtomicLocalRecordStore(private val root: String, private val maximumPayloadBytes: Int = 1024 * 1024) : DurableLocalRecordStore {
    init { require(maximumPayloadBytes in 1..4 * 1024 * 1024) }
    @Serializable private data class Record(val generation: Long, val payload: String, val checksum: String)
    private val json = Json { encodeDefaults = true }
    private companion object { val processLock = NSRecursiveLock() }
    private fun verify(stat: stat, directory: Boolean) {
        check(stat.st_uid == getuid())
        check(stat.st_mode.toInt() and 0xf000 == if (directory) 0x4000 else 0x8000)
        check(stat.st_mode.toInt() and 0x1ff == if (directory) 448 else 384)
    }
    private fun forceDirectory(path: String) {
        val fd = open(path, O_RDONLY or O_DIRECTORY or O_NOFOLLOW)
        check(fd >= 0)
        try { check(fsync(fd) == 0) } finally { check(close(fd) == 0) }
    }
    private fun <T> locked(block: () -> T): T {
        processLock.lock()
        try {
            require(root.startsWith('/') && '\u0000' !in root && root.last() != '/')
            check(NSURL.fileURLWithPath(root).URLByResolvingSymlinksInPath?.path == root)
            memScoped {
                val info = alloc<stat>()
                if (lstat(root, info.ptr) != 0) {
                    check(errno == ENOENT && mkdir(root, 448u) == 0)
                    forceDirectory(root.substringBeforeLast('/'))
                    check(lstat(root, info.ptr) == 0)
                }
                verify(info, true)
            }
            forceDirectory(root.substringBeforeLast('/'))
            val fd = open("$root/writer.lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_NONBLOCK, 384)
            check(fd >= 0)
            try {
                memScoped { val info = alloc<stat>(); check(fstat(fd, info.ptr) == 0); verify(info, false) }
                check(flock(fd, LOCK_EX) == 0)
                try { return block() } finally { check(flock(fd, LOCK_UN) == 0) }
            } finally { check(close(fd) == 0) }
        } finally { processLock.unlock() }
    }
    private fun path(key: String): String {
        require(key.matches(Regex("[0-9a-f]{64}")))
        return "$root/$key.record"
    }
    private fun checksum(generation: Long, bytes: ByteArray): String {
        val hash = SHA256(); hash.update("$generation\n".encodeToByteArray()); hash.update(bytes)
        return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    private fun readLocked(key: String): DurableLocalRecord? = memScoped {
        val path = path(key); val info = alloc<stat>()
        if (lstat(path, info.ptr) != 0) { check(errno == ENOENT); return null }
        verify(info, false)
        val fd = open(path, O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
        check(fd >= 0)
        try {
            check(fstat(fd, info.ptr) == 0); verify(info, false)
            require(info.st_size in 1..maximumPayloadBytes.toLong() * 2 + 4096)
            val bytes = ByteArray(info.st_size.toInt())
            bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = read(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
                    if (count < 0 && errno == EINTR) continue
                    check(count > 0); offset += count.toInt()
                }
            }
            val extra = alloc<ByteVar>()
            var count: Long
            do { count = read(fd, extra.ptr, 1uL) } while (count < 0 && errno == EINTR)
            check(count == 0L)
            val record = json.decodeFromJsonElement(Record.serializer(), parseStrictJsonObject(bytes.decodeToString(throwOnInvalidSequence = true)))
            val payload = record.payload.encodeToByteArray(throwOnInvalidSequence = true)
            require(record.generation > 0 && payload.size in 1..maximumPayloadBytes && checksum(record.generation, payload) == record.checksum)
            DurableLocalRecord(record.generation, payload)
        } finally { check(close(fd) == 0) }
    }
    override fun read(key: String): DurableLocalRecord? = locked { readLocked(key) }
    override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) = locked {
        require(payload.size in 1..maximumPayloadBytes)
        check(readLocked(key)?.generation == generation) { "Local record generation changed" }
        require(generation == null || generation in 1 until Long.MAX_VALUE)
        val next = (generation ?: 0L) + 1L
        val bytes = json.encodeToString(Record(next, payload.decodeToString(throwOnInvalidSequence = true), checksum(next, payload))).encodeToByteArray()
        require(bytes.size.toLong() <= maximumPayloadBytes.toLong() * 2 + 4096)
        val part = "$root/${NSUUID().UUIDString}.partial"
        val fd = open(part, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
        check(fd >= 0)
        var descriptorOpen = true
        try {
            bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = write(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
                    if (count < 0 && errno == EINTR) continue
                    check(count > 0); offset += count.toInt()
                }
            }
            check(fsync(fd) == 0)
            val result = close(fd); descriptorOpen = false; check(result == 0)
            check(rename(part, path(key)) == 0)
            forceDirectory(root)
            check(readLocked(key)?.let { it.generation == next && it.payload.contentEquals(payload) } == true)
        } finally {
            if (descriptorOpen) check(close(fd) == 0)
            check(unlink(part) == 0 || errno == ENOENT)
        }
    }
    override fun confirmDurable(key: String, generation: Long) = locked {
        check(readLocked(key)?.generation == generation)
        val fd = open(path(key), O_RDONLY or O_NOFOLLOW)
        check(fd >= 0)
        try { check(fsync(fd) == 0) } finally { check(close(fd) == 0) }
        forceDirectory(root)
    }
}

/** Canonicalize the framework-owned parent only; owned leaf symlinks remain rejected. */
internal fun iosPrivateRecordRoot(leaf: String): String {
    require(leaf.matches(Regex("[a-z-]+")))
    val library = NSFileManager.defaultManager.URLsForDirectory(NSLibraryDirectory, NSUserDomainMask).first() as NSURL
    return checkNotNull(library.URLByResolvingSymlinksInPath?.path).trimEnd('/') + "/$leaf"
}
