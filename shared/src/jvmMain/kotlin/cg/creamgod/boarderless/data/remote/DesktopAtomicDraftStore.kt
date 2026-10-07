package cg.creamgod.boarderless.data.remote

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal class AtomicDraftBusyException : IOException("Draft store is busy")

internal class AtomicDraftConflictException : IOException("Draft store generation changed")

internal class AtomicDraftCommitUnknownException(
    cause: Exception,
) : IOException("Draft publication outcome requires a fresh read", cause)

internal data class AtomicDraftRecord(
    val generation: Long,
    val payload: ByteArray?,
)

internal enum class AtomicDraftStage { DataForced, Published, DirectoryForced }

/** Desktop local-file primitive, not yet the SessionPreferences backend.
 * Caller supplies a trusted private LOCAL directory. All cooperating readers/writers use the
 * stable lock protocol. No ordinary-move fallback, legacy import, GC or automatic retry.
 * Null payload is a persisted tombstone (retains generation, preventing an ABA overwrite).
 */
internal class DesktopAtomicDraftStore(
    rootPath: Path,
    private val checkpoint: (AtomicDraftStage) -> Unit = {},
) {
    private val root: Path

    init {
        require(rootPath.isAbsolute)
        val requested = rootPath.normalize()
        if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(requested, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            } catch (
                _: FileAlreadyExistsException,
            ) {
                // Another initializer won; verify below.
            }
        }
        checkPrivateRoot(requested)
        root = requested.toRealPath()
        FileChannel.open(requireNotNull(root.parent), StandardOpenOption.READ).use { it.force(true) }
    }

    /** Read-only published-record catalog. Caller must hold a lifetime exclusion covering ALL
     * writers when using this for migration; per-key locks alone do not freeze the catalog.
     * Staging/lock files are retained, never interpreted as published records or reclaimed.
     */
    fun recordKeys(): Set<String> {
        checkPrivateRoot(root)
        return Files.list(root).use { paths ->
            val keys = linkedSetOf<String>()
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                val name = path.fileName.toString()
                if (!name.endsWith(".record")) continue
                val key = name.removeSuffix(".record")
                require(key.matches(Regex("[0-9a-f]{64}")))
                check(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                keys.add(key)
                require(keys.size <= 1024)
            }
            keys
        }
    }

    fun read(scopeHash: String): AtomicDraftRecord? = locked(scopeHash) { readLocked(scopeHash) }

    fun compareAndSet(
        scopeHash: String,
        expectedGeneration: Long?,
        payload: ByteArray?,
    ): AtomicDraftRecord {
        require(expectedGeneration == null || expectedGeneration in 1..MaxGeneration)
        require(payload == null || payload.size <= MaximumPayloadBytes)
        val snapshot = payload?.copyOf()
        return locked(scopeHash) {
            val current = readLocked(scopeHash)
            if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
            val generation = (current?.generation ?: 0L) + 1
            check(generation in 1..MaxGeneration)
            val header =
                ByteBuffer
                    .allocate(HeaderSize)
                    .put(Magic)
                    .put(scopeBytes(scopeHash))
                    .putLong(generation)
                    .putInt(snapshot?.size ?: -1)
                    .array()
            val hash = checksum(header, snapshot)
            val staging = Files.createTempFile(root, "staging-", ".tmp", PrivateFileAttribute)
            var publicationAttempted = false
            try {
                FileChannel.open(staging, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                    writeFully(channel, header)
                    snapshot?.let { writeFully(channel, it) }
                    writeFully(channel, hash)
                    channel.force(true)
                }
                checkpoint(AtomicDraftStage.DataForced)
                publicationAttempted = true
                Files.move(staging, recordPath(scopeHash), StandardCopyOption.ATOMIC_MOVE)
                checkpoint(AtomicDraftStage.Published)
                FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
                checkpoint(AtomicDraftStage.DirectoryForced)
            } catch (failure: Exception) {
                // An exception during/after publication is not evidence that the old record won.
                // Keep staging evidence; caller must reread, not mint new IDs or retry blindly.
                if (publicationAttempted) throw AtomicDraftCommitUnknownException(failure)
                throw failure
            }
            AtomicDraftRecord(generation, snapshot)
        }
    }

    /** Resolve a freshly reviewed published generation without republishing it. A read alone
     * cannot prove that a prior unknown move reached the directory durability barrier.
     */
    fun confirmDurable(
        scopeHash: String,
        expectedGeneration: Long,
    ): AtomicDraftRecord =
        locked(scopeHash) {
            val current = requireNotNull(readLocked(scopeHash))
            if (current.generation != expectedGeneration) throw AtomicDraftConflictException()
            try {
                FileChannel.open(recordPath(scopeHash), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { it.force(true) }
                checkpoint(AtomicDraftStage.DataForced)
                FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
                checkpoint(AtomicDraftStage.DirectoryForced)
            } catch (failure: Exception) {
                throw AtomicDraftCommitUnknownException(failure)
            }
            current
        }

    private fun readLocked(scopeHash: String): AtomicDraftRecord? {
        val file = recordPath(scopeHash)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file))
        FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            check(channel.size() in RecordOverhead.toLong()..(RecordOverhead + MaximumPayloadBytes).toLong())
            val header = readFully(channel, HeaderSize)
            val buffer = ByteBuffer.wrap(header)
            check(ByteArray(Magic.size).also(buffer::get).contentEquals(Magic))
            check(ByteArray(32).also(buffer::get).contentEquals(scopeBytes(scopeHash)))
            val generation = buffer.long
            val size = buffer.int
            check(generation in 1..MaxGeneration && size in -1..MaximumPayloadBytes)
            check(channel.size() == RecordOverhead.toLong() + size.coerceAtLeast(0))
            val payload = if (size < 0) null else readFully(channel, size)
            check(MessageDigest.isEqual(readFully(channel, 32), checksum(header, payload)))
            check(channel.read(ByteBuffer.allocate(1)) == -1)
            return AtomicDraftRecord(generation, payload)
        }
    }

    private fun <T> locked(
        scopeHash: String,
        action: () -> T,
    ): T {
        require(scopeHash.matches(Regex("[0-9a-f]{64}")))
        checkPrivateRoot(root)
        val mutex = ProcessLocks.computeIfAbsent(root.toString()) { ReentrantLock() }
        if (mutex.isHeldByCurrentThread || !mutex.tryLock()) throw AtomicDraftBusyException()
        try {
            val lockPath = root.resolve("$scopeHash.lock")
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) check(Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS))
            FileChannel
                .open(
                    lockPath,
                    setOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PrivateFileAttribute,
                ).use { channel ->
                    val lock =
                        try {
                            channel.tryLock() ?: throw AtomicDraftBusyException()
                        } catch (
                            _: OverlappingFileLockException,
                        ) {
                            throw AtomicDraftBusyException()
                        }
                    lock.use { return action() }
                }
        } finally {
            mutex.unlock()
        }
    }

    private fun recordPath(scopeHash: String) = root.resolve("$scopeHash.record")

    private fun scopeBytes(scopeHash: String) = ByteArray(32) { scopeHash.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun checksum(
        header: ByteArray,
        payload: ByteArray?,
    ) = MessageDigest.getInstance("SHA-256").run {
        update(header)
        payload?.let(::update)
        digest()
    }

    private companion object {
        val Magic = "BLDS0001".toByteArray(Charsets.US_ASCII)
        const val HeaderSize = 52
        const val RecordOverhead = 84
        const val MaximumPayloadBytes = 6 * 1024 * 1024
        const val MaxGeneration = 9_007_199_254_740_991L
        val PrivateFileAttribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        val ProcessLocks = ConcurrentHashMap<String, ReentrantLock>()

        fun checkPrivateRoot(path: Path) {
            check(!Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            check(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS) == PosixFilePermissions.fromString("rwx------"))
        }

        fun writeFully(
            channel: FileChannel,
            bytes: ByteArray,
        ) {
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(channel.write(buffer) > 0)
        }

        fun readFully(
            channel: FileChannel,
            length: Int,
        ): ByteArray {
            val buffer = ByteBuffer.allocate(length)
            while (buffer.hasRemaining()) check(channel.read(buffer) > 0)
            return buffer.array()
        }
    }
}
