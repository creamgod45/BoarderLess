package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.feature.canvas.QuickSchemeBundleCodec
import cg.creamgod.boarderless.feature.canvas.SchemeBundleDestination
import cg.creamgod.boarderless.feature.canvas.SchemeBundleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

/** Private immutable picker snapshot. Neither the selected path nor the snapshot path leaves JVM. */
internal class DesktopSchemeBundleSource private constructor(
    private val directory: Path,
    private val path: Path,
    override val byteSize: Long,
) : SchemeBundleSource {
    private var released = false
    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(!released)
        require(offset in 0..byteSize && maximumBytes in 1..QuickSchemeBundleCodec.ChunkBytes)
        if (offset == byteSize) return@withContext byteArrayOf()
        val buffer = ByteBuffer.allocate(minOf(maximumBytes.toLong(), byteSize - offset).toInt())
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            require(channel.size() == byteSize)
            channel.position(offset)
            while (buffer.hasRemaining()) {
                currentCoroutineContext().ensureActive()
                check(channel.read(buffer) > 0) { "Truncated bundle snapshot" }
            }
        }
        buffer.array()
    }

    override suspend fun release() = withContext(Dispatchers.IO) {
        if (!released) {
            released = true
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
        Unit
    }

    companion object {
        suspend fun snapshot(selected: Path, canRead: () -> Boolean): DesktopSchemeBundleSource = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            check(canRead())
            val before = Files.readAttributes(selected, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            require(before.isRegularFile && !before.isSymbolicLink && before.size() in 45..QuickSchemeBundleCodec.MaximumBundleBytes)
            val directory = Files.createTempDirectory("boarderless-scheme-bundle-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            val path = directory.resolve("snapshot.bundle")
            try {
                FileChannel.open(selected, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                    FileChannel.open(path, setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).use { output ->
                        val buffer = ByteBuffer.allocate(QuickSchemeBundleCodec.ChunkBytes)
                        var copied = 0L
                        while (copied < before.size()) {
                            currentCoroutineContext().ensureActive()
                            check(canRead())
                            buffer.clear()
                            buffer.limit(minOf(buffer.capacity().toLong(), before.size() - copied).toInt())
                            val count = input.read(buffer)
                            check(count > 0)
                            buffer.flip()
                            while (buffer.hasRemaining()) output.write(buffer)
                            copied += count
                        }
                        check(input.read(ByteBuffer.allocate(1)) == -1)
                        output.force(true)
                    }
                }
                val after = Files.readAttributes(selected, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                require(before.fileKey() == after.fileKey() && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime())
                currentCoroutineContext().ensureActive()
                check(canRead())
                DesktopSchemeBundleSource(directory, path, before.size())
            } catch (error: Throwable) {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
                throw error
            }
        }
    }
}

/** Force all bytes before atomically linking a NEW final filename. An existing destination is
 * never replaced. After publication uncertainty, abort removes only the private partial link. */
internal class DesktopSchemeBundleDestination(
    private val target: Path,
    private val canWrite: () -> Boolean,
) : SchemeBundleDestination {
    private var partial: Path? = null
    private var channel: FileChannel? = null
    private var finished = false
    private var published = false
    private var count = 0L

    override suspend fun writeChunk(bytes: ByteArray) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(!finished && !published && canWrite())
        require(bytes.isNotEmpty() && bytes.size <= QuickSchemeBundleCodec.ChunkBytes)
        require(count + bytes.size <= QuickSchemeBundleCodec.MaximumBundleBytes)
        if (partial == null) {
            require(target.isAbsolute && Files.isDirectory(target.parent))
            check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            partial = Files.createTempFile(target.parent, ".boarderless-bundle-", ".part", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            channel = FileChannel.open(partial, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        }
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            currentCoroutineContext().ensureActive()
            check(canWrite())
            check(channel!!.write(buffer) > 0)
        }
        count += bytes.size
    }

    override suspend fun commit() = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(!finished && !published && canWrite() && count >= 45)
        val file = checkNotNull(partial)
        checkNotNull(channel).force(true)
        channel!!.close()
        channel = null
        currentCoroutineContext().ensureActive()
        check(canWrite())
        Files.createLink(target, file)
        published = true
        Files.delete(file)
        partial = null
        FileChannel.open(target.parent, StandardOpenOption.READ).use { it.force(true) }
        finished = true
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        try { channel?.close() } finally {
            channel = null
            partial?.let { Files.deleteIfExists(it) }
            partial = null
            finished = true
        }
        Unit
    }
}

/** Public runtime boundaries keep platform paths out of the shared review model. */
suspend fun desktopSchemeBundleSource(path: String, canRead: () -> Boolean): SchemeBundleSource =
    DesktopSchemeBundleSource.snapshot(Path.of(path), canRead)

fun desktopSchemeBundleDestination(path: String, canWrite: () -> Boolean): SchemeBundleDestination =
    DesktopSchemeBundleDestination(Path.of(path), canWrite)

fun desktopSchemeBundleReceipts(owner: cg.creamgod.boarderless.data.WorkspaceSession, digest: String,
    assets: List<cg.creamgod.boarderless.feature.canvas.SchemeBundleAsset>): cg.creamgod.boarderless.feature.canvas.SchemeBundleReceipts =
    DesktopSchemeBundleImportReceipts(Path.of(System.getProperty("user.home"), ".boarderless-storage", "bundle-imports"), owner, digest, assets)
