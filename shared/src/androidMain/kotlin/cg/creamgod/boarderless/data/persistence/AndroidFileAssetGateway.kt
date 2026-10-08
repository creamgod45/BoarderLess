package cg.creamgod.boarderless.data.persistence

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest
import java.util.UUID

/** Private originals on API 24+. Uses no picker path, URL or full-file byte array. */
internal class AndroidFileAssetGateway(
    private val root: File,
    private val validateSession: suspend (WorkspaceSession) -> Unit,
) : AssetTransferGateway, AssetDownloadGateway {
    private val catalog by lazy {
        privateRoot()
        PersistentLocalAssetCatalog({ AndroidAtomicLocalRecordStore(File(root, "metadata"), 4 * 1024 * 1024) },
            object : LocalDocumentLock { override fun <T> withLock(block: () -> T): T = synchronized(this) { block() } })
    }
    private fun verifyFile(path: File, directory: Boolean) {
        val stat = Os.lstat(path.path)
        check(stat.st_uid == Os.getuid() && stat.st_mode and 0xf000 == if (directory) 0x4000 else 0x8000)
        check(stat.st_mode and 0x1ff == if (directory) 0x1c0 else 0x180)
    }
    private fun exists(path: File): Boolean = try { Os.lstat(path.path); true } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) false else throw e
    }
    private fun forceDirectory(directory: File = root) {
        val fd = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try { check(Os.fstat(fd).st_mode and 0xf000 == 0x4000); Os.fsync(fd) } finally { Os.close(fd) }
    }
    private fun privateRoot() {
        check(root.isAbsolute && root.canonicalFile == root.absoluteFile)
        if (!exists(root)) {
            try { Os.mkdir(root.path, 0x1c0) } catch (e: ErrnoException) { if (e.errno != OsConstants.EEXIST) throw e }
        }
        verifyFile(root, true)
        forceDirectory(checkNotNull(root.parentFile))
    }
    private fun blob(id: String): File { validateLocalAssetId(id); privateRoot(); return File(root, "$id.original") }
    private suspend fun <T> locked(block: suspend () -> T): T = writers.withLock {
        privateRoot()
        val path = File(root, "writer.lock")
        val fd = Os.open(path.path, OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW, 0x180)
        FileOutputStream(fd).use { stream ->
            verifyFile(path, false)
            stream.channel.lock().use { block() }
        }
    }
    suspend fun get(session: WorkspaceSession, id: String): WorkspaceAsset = withContext(Dispatchers.IO) {
        validateSession(session)
        catalog.get(id).also { require(it.workspaceId == session.workspace.id && it.ownerId == session.userId) }
    }
    suspend fun list(session: WorkspaceSession): List<WorkspaceAsset> = withContext(Dispatchers.IO) {
        validateSession(session); catalog.list(session)
    }
    suspend fun recover(session: WorkspaceSession, id: String): WorkspaceAsset {
        val asset = get(session, id)
        return if (asset.status == AssetStatus.Pending && withContext(Dispatchers.IO) { exists(blob(id)) })
            confirm(session, AssetUploadTicket(asset, "boarderless-local:$id")) else asset
    }
    override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket = withContext(Dispatchers.IO) {
        validateSession(session)
        validateAssetTransferSource(source)
        val asset = WorkspaceAsset(UUID.randomUUID().toString(), session.workspace.id, session.userId,
            source.mediaType.trim().lowercase(), source.byteSize, source.checksum, source.width, source.height, source.durationMs,
            AssetStatus.Pending, androidLocalDocumentTimestamp())
        catalog.add(asset)
        AssetUploadTicket(asset, "boarderless-local:${asset.id}")
    }
    override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        locked {
            require(catalog.get(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Pending)
            validateAssetTransferSource(source)
            require(ticket.asset.byteSize == source.byteSize && ticket.asset.checksum == source.checksum && ticket.asset.mediaType == source.mediaType.trim().lowercase())
            val target = blob(ticket.asset.id)
            check(!exists(target))
            val temporary = File(root, ".import-${UUID.randomUUID()}.part")
            val fd = Os.open(temporary.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 0x180)
            try {
                FileOutputStream(fd).use { output ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    var offset = 0L
                    while (offset < source.byteSize) {
                        currentCoroutineContext().ensureActive()
                        val limit = minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt()
                        val bytes = source.readChunk(offset, limit)
                        require(bytes.isNotEmpty() && bytes.size <= limit)
                        output.write(bytes); digest.update(bytes); offset += bytes.size; onProgress(offset)
                    }
                    require(("sha256:" + hex(digest.digest())).equals(source.checksum, ignoreCase = true))
                    output.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                check(!exists(target))
                Os.rename(temporary.path, target.path)
                forceDirectory()
            } finally { if (exists(temporary)) Os.remove(temporary.path) }
        }
    }
    private suspend fun verify(asset: WorkspaceAsset) {
        val path = blob(asset.id)
        verifyFile(path, false)
        val fd = Os.open(path.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        FileInputStream(fd).use { input ->
            val stat = Os.fstat(fd)
            check(stat.st_uid == Os.getuid() && stat.st_mode and 0xf000 == 0x8000 && stat.st_mode and 0x1ff == 0x180 && stat.st_size == asset.byteSize)
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DefaultAssetUploadChunkBytes)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) { total += count; check(total <= asset.byteSize); hash.update(buffer, 0, count) }
            }
            check(total == asset.byteSize && ("sha256:" + hex(hash.digest())).equals(asset.checksum, ignoreCase = true))
        }
    }
    override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset = withContext(Dispatchers.IO) {
        locked {
            val current = get(session, ticket.asset.id)
            require(current == ticket.asset || current == ticket.asset.copy(status = AssetStatus.Ready))
            verify(current)
            val fd = Os.open(blob(current.id).path, OsConstants.O_WRONLY or OsConstants.O_NOFOLLOW, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
            forceDirectory()
            catalog.ready(current)
        }
    }
    override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit) = get(session, assetId)
    override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? { get(session, assetId); return null }
    override suspend fun abandon(session: WorkspaceSession, assetId: String) = withContext(Dispatchers.IO) {
        locked {
            val asset = get(session, assetId)
            if (asset.status == AssetStatus.Pending) {
                catalog.abandon(asset)
                val path = blob(assetId)
                if (exists(path)) Os.remove(path.path)
                forceDirectory()
            }
        }
    }
    override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
        val asset = get(session, assetId)
        require(asset.status == AssetStatus.Ready)
        return AssetDownloadTicket(asset, "boarderless-local:$assetId")
    }
    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        locked {
            require(catalog.get(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Ready)
            verify(ticket.asset)
            val fd = Os.open(blob(ticket.asset.id).path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            FileInputStream(fd).use { input ->
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onChunk(buffer.copyOf(count))
                }
            }
        }
    }
    private companion object {
        val writers = Mutex()
        fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
