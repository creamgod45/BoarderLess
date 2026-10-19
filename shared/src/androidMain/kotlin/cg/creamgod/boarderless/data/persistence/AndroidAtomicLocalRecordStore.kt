package cg.creamgod.boarderless.data.persistence

import android.system.Os
import android.system.ErrnoException
import android.system.OsConstants
import cg.creamgod.boarderless.data.parseStrictJsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** App-private atomic receipts. CAS holds a process mutex and a cross-process file lock.
 * Any force/rename failure propagates; an unknown publication remains readable on retry. */
internal class AndroidAtomicLocalRecordStore(private val root: File, private val maximumPayloadBytes: Int = 1024*1024) : DurableLocalRecordStore {
    init { require(maximumPayloadBytes in 1..4*1024*1024) }
    @Serializable private data class Record(val generation: Long, val payload: String, val checksum: String)
    private val json = Json { encodeDefaults = true }
    private companion object { val processLock = Any() }

    private fun verify(file: File, directory: Boolean) {
        val stat = Os.lstat(file.absolutePath)
        check(stat.st_uid == Os.getuid())
        check(stat.st_mode and 0xf000 == if (directory) 0x4000 else 0x8000)
        check(stat.st_mode and 0x1ff == if (directory) 0x1c0 else 0x180)
    }
    private fun forceDirectory(directory: File) {
        val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try { check(Os.fstat(fd).st_mode and 0xf000 == 0x4000); Os.fsync(fd) } finally { Os.close(fd) }
    }
    private fun <T> locked(block: () -> T): T = synchronized(processLock) {
        check(root.isAbsolute && root.canonicalFile == root.absoluteFile)
        if (!root.exists()) {
            Os.mkdir(root.absolutePath, 0x1c0)
            forceDirectory(checkNotNull(root.parentFile))
        }
        verify(root, true)
        forceDirectory(checkNotNull(root.parentFile))
        val lock = File(root, "writer.lock")
        val fd = Os.open(lock.absolutePath, OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW, 0x180)
        FileOutputStream(fd).use { stream ->
            verify(lock, false)
            stream.channel.lock().use { block() }
        }
    }
    private fun file(key: String): File {
        require(key.matches(Regex("[0-9a-f]{64}")))
        return File(root, "$key.receipt")
    }
    private fun checksum(generation: Long, bytes: ByteArray): String {
        val hash = SHA256(); hash.update("$generation\n".encodeToByteArray()); hash.update(bytes)
        return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    }
    private fun readLocked(key: String): DurableLocalRecord? {
        val file = file(key)
        try { Os.lstat(file.absolutePath) } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) return null else throw error
        }
        verify(file, false)
        require(file.length() in 1..maximumPayloadBytes.toLong()*2+4096)
        val raw = FileInputStream(file).use { it.readBytes() }.decodeToString(throwOnInvalidSequence = true)
        val record = json.decodeFromJsonElement(Record.serializer(), parseStrictJsonObject(raw))
        require(record.generation > 0)
        val bytes = record.payload.encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumPayloadBytes && checksum(record.generation, bytes) == record.checksum)
        return DurableLocalRecord(record.generation, bytes)
    }
    override fun read(key: String): DurableLocalRecord? = locked { readLocked(key) }
    override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) = locked {
        require(payload.size in 1..maximumPayloadBytes)
        val previous = readLocked(key)
        check(previous?.generation == generation) { "Receipt generation changed" }
        val next = Math.addExact(generation ?: 0L, 1L)
        val record = Record(next, payload.decodeToString(throwOnInvalidSequence = true), checksum(next,payload))
        val bytes = json.encodeToString(record).encodeToByteArray()
        require(bytes.size.toLong() <= maximumPayloadBytes.toLong()*2+4096)
        val temp = File(root,"${UUID.randomUUID()}.partial")
        val fd = Os.open(temp.absolutePath, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 0x180)
        try {
            FileOutputStream(fd).use { it.write(bytes); it.flush(); it.fd.sync() }
            Os.rename(temp.absolutePath, file(key).absolutePath)
            forceDirectory(root)
            check(readLocked(key)?.let { it.generation == next && it.payload.contentEquals(payload) } == true)
        } finally {
            if (temp.exists()) check(temp.delete())
        }
    }
    override fun confirmDurable(key: String, generation: Long) = locked {
        check(readLocked(key)?.generation == generation)
        val fd = Os.open(file(key).absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
        forceDirectory(root)
    }
}
