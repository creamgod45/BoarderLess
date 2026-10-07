package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.MediaKind
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** An immutable, user-selected snapshot, not a live path or URL. Reads are bounded and positional. */
interface SchemeBundleSource {
    val byteSize: Long
    suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray
    suspend fun release()
}

/** New destination only. Commit publishes a complete file; abort removes only its own partial file. */
interface SchemeBundleDestination {
    suspend fun writeChunk(bytes: ByteArray)
    suspend fun commit()
    suspend fun abort()
}

@Serializable data class SchemeBundleAsset(
    val sourceId: String,
    val mediaType: String,
    val byteSize: Long,
    val checksum: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
)

@Serializable internal data class SchemeBundleManifest(
    val format: String = "boarderless.scheme-bundle",
    val version: Int = 1,
    val scheme: String,
    val assets: List<SchemeBundleAsset>,
)

internal class ReviewedSchemeBundle internal constructor(
    val scheme: QuickScheme,
    val assets: List<SchemeBundleAsset>,
    internal val source: SchemeBundleSource,
    internal val offsets: List<Long>,
    internal val digest: ByteArray,
) {
    suspend fun release() = source.release()
}

/** A framed UTF-8 manifest followed by exact originals and a whole-container SHA-256 footer.
 * No ZIP entries, path names, credentials, signed URLs or device handles enter the file.
 */
internal object QuickSchemeBundleCodec {
    private val magic = "BLQS0001".encodeToByteArray()
    private val json = Json { encodeDefaults = true }
    const val ChunkBytes = 1024 * 1024
    const val MaximumBundleBytes = 1024L * 1024 * 1024
    private const val MaximumManifestBytes = 4 * 1024 * 1024

    private fun referencedAssets(payload: ClipboardPayload): Set<String> =
        payload.media.flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.toSet()

    private fun validate(manifest: SchemeBundleManifest): QuickScheme {
        require(manifest.format == "boarderless.scheme-bundle" && manifest.version == 1)
        val scheme = QuickSchemeTransferCodec.decode(manifest.scheme)
        val payload = requireNotNull(decodeQuickSchemePayload(scheme))
        require(manifest.assets.size <= 64)
        require(manifest.assets.map { it.sourceId }.toSet() == referencedAssets(payload))
        require(manifest.assets.map { it.sourceId }.distinct().size == manifest.assets.size)
        var total = 0L
        manifest.assets.forEach { asset ->
            require(asset.sourceId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            require(asset.mediaType == asset.mediaType.trim().lowercase() && mediaKindForAssetMediaType(asset.mediaType) != null)
            require(asset.byteSize in 1..MaxWorkspaceAssetBytes)
            require(asset.checksum.matches(Regex("sha256:[0-9a-f]{64}")))
            require(asset.width == null || asset.width > 0)
            require(asset.height == null || asset.height > 0)
            require(asset.durationMs == null || asset.durationMs >= 0)
            total += asset.byteSize
            require(total <= MaximumBundleBytes - MaximumManifestBytes - 44)
        }
        val byId = manifest.assets.associateBy { it.sourceId }
        payload.media.forEach { node ->
            require(mediaKindForAssetMediaType(byId.getValue(node.assetId).mediaType)?.token == node.mediaKind)
            node.thumbnailAssetId?.let { require(mediaKindForAssetMediaType(byId.getValue(it).mediaType) == MediaKind.Image) }
        }
        return scheme
    }

    private fun hashText(digest: ByteArray) = "sha256:" + digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private suspend fun checkScope(isCurrent: () -> Boolean) {
        currentCoroutineContext().ensureActive()
        check(isCurrent()) { "Scheme bundle scope changed" }
    }

    suspend fun export(
        scheme: QuickScheme,
        sourceSession: WorkspaceSession,
        gateway: AssetDownloadGateway,
        destination: SchemeBundleDestination,
        isCurrent: () -> Boolean,
        readFresh: suspend () -> QuickScheme?,
    ) {
        var committed = false
        try {
            checkScope(isCurrent)
            require(readFresh() == scheme)
            checkScope(isCurrent)
            val payload = requireNotNull(decodeQuickSchemePayload(scheme))
            if (payload.media.isNotEmpty()) require(scheme.sourceWorkspaceId == sourceSession.workspace.id.value)
            val tickets = referencedAssets(payload).sorted().map { id ->
                checkScope(isCurrent)
                val ticket = gateway.authorize(sourceSession, id)
                checkScope(isCurrent)
                require(ticket.asset.id == id && ticket.asset.workspaceId == sourceSession.workspace.id && ticket.asset.status == AssetStatus.Ready)
                ticket
            }
            val manifest = SchemeBundleManifest(scheme = QuickSchemeTransferCodec.encode(scheme), assets = tickets.map { ticket ->
                val a = ticket.asset
                SchemeBundleAsset(a.id, a.mediaType, a.byteSize, a.checksum, a.width, a.height, a.durationMs)
            })
            validate(manifest)
            val text = json.encodeToString(manifest).encodeToByteArray(throwOnInvalidSequence = true)
            require(text.size in 1..MaximumManifestBytes)
            val whole = SHA256()
            suspend fun write(bytes: ByteArray) {
                checkScope(isCurrent)
                require(bytes.isNotEmpty() && bytes.size <= ChunkBytes)
                whole.update(bytes)
                destination.writeChunk(bytes)
                checkScope(isCurrent)
            }
            write(magic)
            write(ByteArray(4) { n -> (text.size ushr (24 - n * 8)).toByte() })
            var cursor = 0
            while (cursor < text.size) {
                val next = minOf(cursor + ChunkBytes, text.size)
                write(text.copyOfRange(cursor, next))
                cursor = next
            }
            tickets.forEach { ticket ->
                var size = 0L
                val assetHash = SHA256()
                gateway.download(ticket) { bytes ->
                    checkScope(isCurrent)
                    require(bytes.isNotEmpty() && bytes.size <= ChunkBytes && size + bytes.size <= ticket.asset.byteSize)
                    assetHash.update(bytes)
                    write(bytes)
                    size += bytes.size
                }
                require(size == ticket.asset.byteSize && hashText(assetHash.digest()) == ticket.asset.checksum)
            }
            checkScope(isCurrent)
            require(readFresh() == scheme)
            checkScope(isCurrent)
            destination.writeChunk(whole.digest())
            checkScope(isCurrent)
            destination.commit()
            committed = true
            checkScope(isCurrent)
        } finally {
            if (!committed) withContext(NonCancellable) { runCatching { destination.abort() } }
        }
    }

    private suspend fun readExactly(source: SchemeBundleSource, offset: Long, size: Int, isCurrent: () -> Boolean): ByteArray {
        require(size in 1..ChunkBytes && offset >= 0 && offset + size <= source.byteSize)
        val result = ByteArray(size)
        var copied = 0
        while (copied < size) {
            checkScope(isCurrent)
            val chunk = source.readChunk(offset + copied, size - copied)
            checkScope(isCurrent)
            require(chunk.isNotEmpty() && chunk.size <= size - copied)
            chunk.copyInto(result, copied)
            copied += chunk.size
        }
        return result
    }

    /** Complete verification precedes review; this stage creates no assets, scheme or canvas nodes. */
    suspend fun review(source: SchemeBundleSource, isCurrent: () -> Boolean): ReviewedSchemeBundle {
        try {
            checkScope(isCurrent)
            require(source.byteSize in 45..MaximumBundleBytes)
            val header = readExactly(source, 0, 12, isCurrent)
            require(header.copyOfRange(0, 8).contentEquals(magic))
            var length = 0L
            for (n in 8..11) length = (length shl 8) or (header[n].toLong() and 255)
            require(length in 1..MaximumManifestBytes.toLong() && 12 + length + 32 <= source.byteSize)
            val text = ByteArray(length.toInt())
            var cursor = 0
            while (cursor < text.size) {
                val chunk = readExactly(source, 12L + cursor, minOf(ChunkBytes, text.size - cursor), isCurrent)
                chunk.copyInto(text, cursor)
                cursor += chunk.size
            }
            val raw = text.decodeToString(throwOnInvalidSequence = true)
            val document = parseStrictJsonObject(raw)
            require(document.keys == setOf("format", "version", "scheme", "assets"))
            val manifest = json.decodeFromJsonElement(SchemeBundleManifest.serializer(), document)
            val scheme = validate(manifest)
            val whole = SHA256()
            whole.update(header)
            whole.update(text)
            var offset = 12 + length
            val offsets = manifest.assets.map { asset ->
                val start = offset
                val digest = SHA256()
                var remaining = asset.byteSize
                while (remaining > 0) {
                    val bytes = readExactly(source, offset, minOf(remaining, ChunkBytes.toLong()).toInt(), isCurrent)
                    digest.update(bytes)
                    whole.update(bytes)
                    offset += bytes.size
                    remaining -= bytes.size
                }
                require(hashText(digest.digest()) == asset.checksum)
                start
            }
            require(offset + 32 == source.byteSize) { "Bundle contains trailing or missing data" }
            val footer = readExactly(source, offset, 32, isCurrent)
            require(whole.digest().contentEquals(footer))
            checkScope(isCurrent)
            return ReviewedSchemeBundle(scheme, manifest.assets, source, offsets, footer)
        } catch (error: Throwable) {
            withContext(NonCancellable) { runCatching { source.release() } }
            throw error
        }
    }

    /** Caller must bind this exact review to fresh user confirmation. All imports remain unreferenced
     * until every Ready result is validated and a complete replacement scheme can be published. */
    suspend fun materialize(
        reviewed: ReviewedSchemeBundle,
        destination: WorkspaceSession,
        gateway: AssetTransferGateway,
        isCurrent: () -> Boolean,
        readFresh: suspend () -> WorkspaceSession,
        beforeComplete: suspend (sourceId: String, destinationId: String) -> Unit,
        readImported: suspend (asset: SchemeBundleAsset) -> ImportedMedia?,
        recordReady: suspend (sourceId: String, imported: ImportedMedia) -> Unit,
        onStage: (sourceId: String, stage: AssetImportStage) -> Unit = { _, _ -> },
    ): QuickScheme {
        checkScope(isCurrent)
        require(destination.canEditContent)
        val verifiedAgain = review(reviewed.source, isCurrent)
        require(verifiedAgain.digest.contentEquals(reviewed.digest) && verifiedAgain.assets == reviewed.assets && verifiedAgain.scheme == reviewed.scheme)
        suspend fun authority() {
            checkScope(isCurrent)
            val fresh = readFresh()
            checkScope(isCurrent)
            require(fresh.canEditContent && fresh.userId == destination.userId && fresh.clientId == destination.clientId &&
                fresh.workspace.id == destination.workspace.id && fresh.workspaceVersion == destination.workspaceVersion &&
                fresh.lastServerSeq == destination.lastServerSeq && fresh.workspace == destination.workspace)
        }
        authority()
        val mapping = mutableMapOf<String, ImportedMedia>()
        reviewed.assets.forEachIndexed { index, asset ->
            authority()
            var readBytes = 0L
            val digest = SHA256()
            val source = object : AssetTransferSource {
                override val displayName = "scheme-asset-${index + 1}"
                override val mediaType = asset.mediaType
                override val byteSize = asset.byteSize
                override val checksum = asset.checksum
                override val width = asset.width
                override val height = asset.height
                override val durationMs = asset.durationMs
                override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
                    checkScope(isCurrent)
                    require(offset == readBytes && maximumBytes in 1..ChunkBytes)
                    if (offset == byteSize) return byteArrayOf()
                    val bytes = readExactly(reviewed.source, reviewed.offsets[index] + offset,
                        minOf(maximumBytes.toLong(), byteSize - offset).toInt(), isCurrent)
                    digest.update(bytes)
                    readBytes += bytes.size
                    return bytes
                }
            }
            val coordinator = AssetImportCoordinator(gateway) { destinationId ->
                require(readBytes == asset.byteSize && hashText(digest.digest()) == asset.checksum)
                authority()
                beforeComplete(asset.sourceId, destinationId)
                checkScope(isCurrent)
            }
            val imported = readImported(asset) ?: coordinator.import(destination, source) { onStage(asset.sourceId, it) }
            checkScope(isCurrent)
            require(imported.asset.status == AssetStatus.Ready && imported.asset.workspaceId == destination.workspace.id &&
                imported.asset.ownerId == destination.userId && imported.asset.mediaType == asset.mediaType &&
                imported.asset.byteSize == asset.byteSize && imported.asset.checksum == asset.checksum &&
                imported.asset.width == asset.width && imported.asset.height == asset.height && imported.asset.durationMs == asset.durationMs &&
                imported.mediaKind == mediaKindForAssetMediaType(asset.mediaType))
            authority()
            recordReady(asset.sourceId, imported)
            checkScope(isCurrent)
            require(mapping.values.none { it.asset.id == imported.asset.id })
            mapping[asset.sourceId] = imported
        }
        authority()
        val original = requireNotNull(decodeQuickSchemePayload(reviewed.scheme))
        val remapped = original.copy(media = original.media.map { node ->
            val media = mapping.getValue(node.assetId)
            node.copy(assetId = media.asset.id, thumbnailAssetId = node.thumbnailAssetId?.let { mapping.getValue(it).asset.id })
        })
        require(validateClipboardPayload(remapped) == null)
        return reviewed.scheme.copy(payload = ClipboardJson.encodeToString(ClipboardPayload.serializer(), remapped),
            sourceWorkspaceId = if (remapped.media.isEmpty()) null else destination.workspace.id.value)
    }
}
