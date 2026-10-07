package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Private streamed originals, independent of picker paths and disposable preview caches.
 * Ready metadata is published only after the verified original and its directory are forced.
 */
class DesktopFileAssetGateway(
    private val root: Path,
    private val validateSession: suspend (WorkspaceSession) -> Unit,
) : AssetTransferGateway,
    AssetDownloadGateway {
    private val metadata by lazy {
        privateRoot()
        DesktopAtomicDraftStore(root.resolve("metadata"))
    }
    private val json = Json { encodeDefaults = true }

    @Serializable private data class Stored(
        val id: String,
        val workspaceId: String,
        val ownerId: String,
        val mediaType: String,
        val byteSize: Long,
        val checksum: String,
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
        val status: String,
        val createdAt: String,
    ) {
        fun asset() =
            WorkspaceAsset(
                id,
                WorkspaceId(workspaceId),
                ownerId,
                mediaType,
                byteSize,
                checksum,
                width,
                height,
                durationMs,
                requireNotNull(AssetStatus.fromToken(status)),
                createdAt,
            )
    }

    private fun WorkspaceAsset.stored() =
        Stored(
            id,
            workspaceId.value,
            ownerId,
            mediaType,
            byteSize,
            checksum,
            width,
            height,
            durationMs,
            status.token,
            createdAt,
        )

    private fun privateRoot() {
        require(root.isAbsolute)
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(root, DirectoryPermissions)
        check(!Files.isSymbolicLink(root) && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        check(Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS) == PosixFilePermissions.fromString("rwx------"))
    }

    private fun key(id: String): String {
        require(UUID.fromString(id).toString() == id)
        return hex(MessageDigest.getInstance("SHA-256").digest(id.toByteArray()))
    }

    private fun blob(id: String): Path {
        key(id)
        privateRoot()
        return root.resolve("$id.original")
    }

    private fun read(id: String): WorkspaceAsset {
        val bytes = requireNotNull(metadata.read(key(id))?.payload) { "Local asset is unavailable" }
        val asset = json.decodeFromString<Stored>(bytes.decodeToString(throwOnInvalidSequence = true)).asset()
        require(asset.id == id && asset.byteSize <= MaxWorkspaceAssetBytes)
        return asset
    }

    suspend fun get(
        session: WorkspaceSession,
        id: String,
    ): WorkspaceAsset =
        withContext(Dispatchers.IO) {
            validateSession(session)
            read(id).also { require(it.workspaceId == session.workspace.id && it.ownerId == session.userId) }
        }

    /** Explicit metadata refresh can settle an interrupted confirmation from a verified local
     * original. No source re-upload or new asset ID is needed. */
    suspend fun recover(
        session: WorkspaceSession,
        id: String,
    ): WorkspaceAsset =
        withContext(Dispatchers.IO) {
            val asset = get(session, id)
            if (asset.status == AssetStatus.Pending && Files.exists(blob(id), LinkOption.NOFOLLOW_LINKS)) {
                confirm(session, AssetUploadTicket(asset, "boarderless-local:$id"))
            } else {
                asset
            }
        }

    suspend fun list(session: WorkspaceSession): List<WorkspaceAsset> =
        withContext(Dispatchers.IO) {
            validateSession(session)
            metadata
                .recordKeys()
                .mapNotNull { key ->
                    metadata.read(key)?.payload?.let { bytes ->
                        json.decodeFromString<Stored>(bytes.decodeToString(throwOnInvalidSequence = true)).asset().also {
                            require(key(it.id) == key)
                        }
                    }
                }.filter { it.workspaceId == session.workspace.id && it.ownerId == session.userId }
        }

    override suspend fun prepare(
        session: WorkspaceSession,
        source: AssetTransferSource,
    ): AssetUploadTicket =
        withContext(Dispatchers.IO) {
            validateSession(session)
            validateAssetTransferSource(source)
            require(source.checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
            val asset =
                WorkspaceAsset(
                    UUID.randomUUID().toString(),
                    session.workspace.id,
                    session.userId,
                    source.mediaType.trim().lowercase(),
                    source.byteSize,
                    source.checksum,
                    source.width,
                    source.height,
                    source.durationMs,
                    AssetStatus.Pending,
                    Instant.now().toString(),
                )
            metadata.compareAndSet(key(asset.id), null, json.encodeToString(asset.stored()).encodeToByteArray())
            AssetUploadTicket(asset, "boarderless-local:${asset.id}")
        }

    override suspend fun upload(
        ticket: AssetUploadTicket,
        source: AssetTransferSource,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(read(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Pending)
        require(
            ticket.asset.byteSize == source.byteSize && ticket.asset.checksum == source.checksum &&
                ticket.asset.mediaType == source.mediaType.trim().lowercase(),
        )
        val target = blob(ticket.asset.id)
        check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        val temporary = Files.createTempFile(root, ".import-", ".part", FilePermissions)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                var offset = 0L
                while (offset < source.byteSize) {
                    currentCoroutineContext().ensureActive()
                    val bytes = source.readChunk(offset, minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt())
                    require(bytes.isNotEmpty() && bytes.size <= minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset)) {
                        "Local source is truncated or oversized"
                    }
                    digest.update(bytes)
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) check(channel.write(buffer) > 0)
                    offset += bytes.size
                    onProgress(offset)
                }
                require(("sha256:" + hex(digest.digest())).equals(source.checksum, ignoreCase = true)) { "Local source checksum changed" }
                channel.force(true)
            }
            currentCoroutineContext().ensureActive()
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            forceDirectory()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override suspend fun confirm(
        session: WorkspaceSession,
        ticket: AssetUploadTicket,
    ): WorkspaceAsset =
        withContext(Dispatchers.IO) {
            val current = get(session, ticket.asset.id)
            require(current == ticket.asset || current == ticket.asset.copy(status = AssetStatus.Ready))
            verify(current)
            FileChannel.open(blob(current.id), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { it.force(true) }
            forceDirectory()
            val record = requireNotNull(metadata.read(key(current.id)))
            if (current.status == AssetStatus.Ready) {
                metadata.confirmDurable(key(current.id), record.generation)
            } else {
                metadata.compareAndSet(
                    key(current.id),
                    record.generation,
                    json.encodeToString(current.copy(status = AssetStatus.Ready).stored()).encodeToByteArray(),
                )
            }
            current.copy(status = AssetStatus.Ready)
        }

    override suspend fun awaitReady(
        session: WorkspaceSession,
        assetId: String,
        onStatus: (AssetStatus) -> Unit,
    ): WorkspaceAsset = get(session, assetId)

    override suspend fun thumbnailAssetId(
        session: WorkspaceSession,
        assetId: String,
    ): String? {
        get(session, assetId)
        return null
    }

    override suspend fun abandon(
        session: WorkspaceSession,
        assetId: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            val current = get(session, assetId)
            if (current.status == AssetStatus.Pending) {
                val record = requireNotNull(metadata.read(key(assetId)))
                metadata.compareAndSet(key(assetId), record.generation, null)
                Files.deleteIfExists(blob(assetId))
                forceDirectory()
            }
        }

    override suspend fun authorize(
        session: WorkspaceSession,
        assetId: String,
    ): AssetDownloadTicket {
        val asset = get(session, assetId)
        require(asset.status == AssetStatus.Ready)
        return AssetDownloadTicket(asset, "boarderless-local:$assetId")
    }

    override suspend fun download(
        ticket: AssetDownloadTicket,
        onChunk: suspend (ByteArray) -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            require(read(ticket.asset.id) == ticket.asset && ticket.asset.status == AssetStatus.Ready)
            verify(ticket.asset)
            Files.newInputStream(blob(ticket.asset.id), LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onChunk(buffer.copyOf(count))
                }
            }
        }

    private suspend fun verify(asset: WorkspaceAsset) {
        val path = blob(asset.id)
        check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
        check(Files.size(path) == asset.byteSize)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(DefaultAssetUploadChunkBytes)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        check(("sha256:" + hex(digest.digest())).equals(asset.checksum, ignoreCase = true))
    }

    private fun forceDirectory() {
        FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
    }

    private companion object {
        val DirectoryPermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        val FilePermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

        fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
