@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kotlincrypto.hash.sha2.SHA256
import platform.Foundation.*
import platform.posix.*
import kotlin.time.Clock

/** Private streamed originals. Ready follows verified bytes, file force and directory force. */
internal class IosFileAssetGateway(
    private val root: String,
    private val validateSession: suspend (WorkspaceSession) -> Unit,
) : AssetTransferGateway, AssetDownloadGateway {
    private val catalog by lazy {
        privateRoot()
        PersistentLocalAssetCatalog({ IosAtomicLocalRecordStore("$root/metadata", 4 * 1024 * 1024) },
            object : LocalDocumentLock {
                private val lock = NSRecursiveLock()
                override fun <T> withLock(block: () -> T): T { lock.lock(); try { return block() } finally { lock.unlock() } }
            })
    }
    private fun verifyStat(info: stat, directory: Boolean) {
        check(info.st_uid == getuid() && info.st_mode.toInt() and 0xf000 == if (directory) 0x4000 else 0x8000)
        check(info.st_mode.toInt() and 0x1ff == if (directory) 448 else 384)
    }
    private fun exists(path: String): Boolean = memScoped {
        val info = alloc<stat>()
        if (lstat(path, info.ptr) == 0) true else { check(errno == ENOENT); false }
    }
    private fun forceDirectory(path: String = root) {
        val fd = open(path, O_RDONLY or O_DIRECTORY or O_NOFOLLOW)
        check(fd >= 0)
        try { check(fsync(fd) == 0) } finally { check(close(fd) == 0) }
    }
    private fun privateRoot() {
        require(root.startsWith('/') && '\u0000' !in root && root.last() != '/')
        check(NSURL.fileURLWithPath(root).URLByResolvingSymlinksInPath?.path == root)
        memScoped {
            val info = alloc<stat>()
            if (lstat(root, info.ptr) != 0) {
                check(errno == ENOENT)
                check(mkdir(root, 448u) == 0 || errno == EEXIST)
                check(lstat(root, info.ptr) == 0)
            }
            verifyStat(info, true)
        }
        forceDirectory(root.substringBeforeLast('/'))
    }
    private fun blob(id: String): String { validateLocalAssetId(id); privateRoot(); return "$root/$id.original" }
    private suspend fun <T> locked(block: suspend () -> T): T = writers.withLock {
        privateRoot()
        val fd = open("$root/writer.lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_NONBLOCK, 384)
        check(fd >= 0)
        try {
            memScoped { val info = alloc<stat>(); check(fstat(fd, info.ptr) == 0); verifyStat(info, false) }
            check(flock(fd, LOCK_EX) == 0)
            try { block() } finally { check(flock(fd, LOCK_UN) == 0) }
        } finally { check(close(fd) == 0) }
    }
    suspend fun get(session: WorkspaceSession, id: String): WorkspaceAsset = withContext(Dispatchers.Default) {
        validateSession(session)
        catalog.get(id).also { require(it.workspaceId == session.workspace.id && it.ownerId == session.userId) }
    }
    suspend fun list(session: WorkspaceSession): List<WorkspaceAsset> = withContext(Dispatchers.Default) {
        validateSession(session); catalog.list(session)
    }
    suspend fun recover(session: WorkspaceSession, id: String): WorkspaceAsset {
        val asset = get(session, id)
        return if (asset.status == AssetStatus.Pending && withContext(Dispatchers.Default) { exists(blob(id)) })
            confirm(session, AssetUploadTicket(asset, "boarderless-local:$id")) else asset
    }
    override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket = withContext(Dispatchers.Default) {
        validateSession(session)
        validateAssetTransferSource(source)
        val asset = WorkspaceAsset(NSUUID().UUIDString.lowercase(), session.workspace.id, session.userId,
            source.mediaType.trim().lowercase(), source.byteSize, source.checksum, source.width, source.height, source.durationMs,
            AssetStatus.Pending, Clock.System.now().toString())
        catalog.add(asset)
        AssetUploadTicket(asset, "boarderless-local:${asset.id}")
    }
    private fun writeAll(fd: Int, bytes: ByteArray) = bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            val count = write(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
            if (count < 0 && errno == EINTR) continue
            check(count > 0); offset += count.toInt()
        }
    }
    override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) = withContext(Dispatchers.Default) {
        locked {
            require(catalog.get(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Pending)
            validateAssetTransferSource(source)
            require(ticket.asset.byteSize == source.byteSize && ticket.asset.checksum == source.checksum && ticket.asset.mediaType == source.mediaType.trim().lowercase())
            val target = blob(ticket.asset.id)
            check(!exists(target))
            val temporary = "$root/.import-${NSUUID().UUIDString}.part"
            val fd = open(temporary, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, 384)
            check(fd >= 0)
            var descriptorOpen = true
            try {
                val hash = SHA256()
                var offset = 0L
                while (offset < source.byteSize) {
                    currentCoroutineContext().ensureActive()
                    val limit = minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt()
                    val bytes = source.readChunk(offset, limit)
                    require(bytes.isNotEmpty() && bytes.size <= limit)
                    writeAll(fd, bytes); hash.update(bytes); offset += bytes.size; onProgress(offset)
                }
                require(("sha256:" + hex(hash.digest())).equals(source.checksum, ignoreCase = true))
                check(fsync(fd) == 0)
                val closed = close(fd); descriptorOpen = false; check(closed == 0)
                currentCoroutineContext().ensureActive()
                check(!exists(target) && rename(temporary, target) == 0)
                forceDirectory()
            } finally {
                if (descriptorOpen) check(close(fd) == 0)
                check(unlink(temporary) == 0 || errno == ENOENT)
            }
        }
    }
    private suspend fun stream(asset: WorkspaceAsset, onChunk: suspend (ByteArray) -> Unit) {
        val fd = open(blob(asset.id), O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
        check(fd >= 0)
        try {
            memScoped { val info = alloc<stat>(); check(fstat(fd, info.ptr) == 0); verifyStat(info, false); check(info.st_size == asset.byteSize) }
            val hash = SHA256()
            val buffer = ByteArray(DefaultAssetUploadChunkBytes)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = buffer.usePinned { pinned ->
                    var n: Long
                    do { n = read(fd, pinned.addressOf(0), buffer.size.toULong()) } while (n < 0 && errno == EINTR)
                    check(n >= 0); n.toInt()
                }
                if (count == 0) break
                total += count; check(total <= asset.byteSize)
                val bytes = buffer.copyOf(count)
                hash.update(bytes); onChunk(bytes)
            }
            check(total == asset.byteSize && ("sha256:" + hex(hash.digest())).equals(asset.checksum, ignoreCase = true))
        } finally { check(close(fd) == 0) }
    }
    override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset = withContext(Dispatchers.Default) {
        locked {
            val current = get(session, ticket.asset.id)
            require(current == ticket.asset || current == ticket.asset.copy(status = AssetStatus.Ready))
            stream(current) {}
            val fd = open(blob(current.id), O_WRONLY or O_NOFOLLOW)
            check(fd >= 0)
            try { check(fsync(fd) == 0) } finally { check(close(fd) == 0) }
            forceDirectory()
            catalog.ready(current)
        }
    }
    override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit) = get(session, assetId)
    override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? { get(session, assetId); return null }
    override suspend fun abandon(session: WorkspaceSession, assetId: String) = withContext(Dispatchers.Default) {
        locked {
            val asset = get(session, assetId)
            if (asset.status == AssetStatus.Pending) {
                catalog.abandon(asset)
                val path = blob(assetId)
                if (exists(path)) check(unlink(path) == 0)
                forceDirectory()
            }
        }
    }
    override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
        val asset = get(session, assetId)
        require(asset.status == AssetStatus.Ready)
        return AssetDownloadTicket(asset, "boarderless-local:$assetId")
    }
    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = withContext(Dispatchers.Default) {
        locked {
            require(catalog.get(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Ready)
            stream(ticket.asset) {}
            stream(ticket.asset, onChunk)
        }
    }
    private companion object {
        val writers = Mutex()
        fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
