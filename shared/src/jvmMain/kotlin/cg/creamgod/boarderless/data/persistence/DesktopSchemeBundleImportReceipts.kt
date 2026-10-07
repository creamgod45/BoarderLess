package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.feature.canvas.SchemeBundleAsset
import cg.creamgod.boarderless.feature.canvas.SchemeBundleReceipts
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import java.nio.file.Path

/** Scope-bound durable IDs written before completion. A pending identity is reconciled by GET;
 * it never silently becomes permission to allocate another upload on restart. */
internal class DesktopSchemeBundleImportReceipts(
    root: Path,
    private val owner: WorkspaceSession,
    private val bundleDigest: String,
    assets: List<SchemeBundleAsset>,
) : SchemeBundleReceipts {
    private val files = DesktopAtomicDraftStore(root)
    private val json = Json { encodeDefaults = true }
    private val expected = assets.toList()
    @Serializable private data class Entry(val source: SchemeBundleAsset, val destinationId: String, val ready: Boolean = false)
    @Serializable private data class Document(val version: Int = 1, val userId: String, val clientId: String,
        val workspaceId: String, val bundleDigest: String, val entries: List<Entry> = emptyList())
    private val empty = Document(userId = owner.userId, clientId = owner.clientId, workspaceId = owner.workspace.id.value, bundleDigest = bundleDigest)
    private val key = SHA256().digest(json.encodeToString(empty).encodeToByteArray()).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
    init {
        require(bundleDigest.matches(Regex("[0-9a-f]{64}")))
        require(expected.size <= 64 && expected.map { it.sourceId }.distinct().size == expected.size)
    }
    private fun read(): Pair<Long?, Document> {
        val record = files.read(key) ?: return null to empty
        val raw = requireNotNull(record.payload).decodeToString(throwOnInvalidSequence = true)
        val document = json.decodeFromJsonElement(Document.serializer(), parseStrictJsonObject(raw))
        require(document.copy(entries = emptyList()) == empty)
        require(document.entries.size <= expected.size && document.entries.map { it.source.sourceId }.distinct().size == document.entries.size)
        require(document.entries.map { it.destinationId }.distinct().size == document.entries.size)
        document.entries.forEach {
            require(it.source in expected && it.destinationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        }
        return record.generation to document
    }
    private fun update(asset: SchemeBundleAsset, destinationId: String, ready: Boolean) {
        require(asset in expected && destinationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val (generation, document) = read()
        val previous = document.entries.singleOrNull { it.source.sourceId == asset.sourceId }
        if (previous != null) require(previous.source == asset && previous.destinationId == destinationId)
        if (ready) require(previous != null)
        val entry = Entry(asset, destinationId, ready || previous?.ready == true)
        val next = document.copy(entries = document.entries.filterNot { it.source.sourceId == asset.sourceId } + entry)
        if (next == document) files.confirmDurable(key, requireNotNull(generation))
        else files.compareAndSet(key, generation, json.encodeToString(next).encodeToByteArray())
    }
    override fun beforeComplete(asset: SchemeBundleAsset, destinationId: String) = update(asset, destinationId, false)
    override fun ready(asset: SchemeBundleAsset, imported: ImportedMedia) {
        require(imported.asset.status == AssetStatus.Ready && imported.asset.workspaceId == owner.workspace.id && imported.asset.ownerId == owner.userId)
        require(imported.asset.mediaType == asset.mediaType && imported.asset.byteSize == asset.byteSize && imported.asset.checksum == asset.checksum)
        update(asset, imported.asset.id, true)
    }
    override suspend fun recovered(asset: SchemeBundleAsset, repository: AssetRepository): ImportedMedia? {
        require(asset in expected)
        val entry = read().second.entries.singleOrNull { it.source == asset } ?: return null
        val fresh = repository.getAsset(owner, entry.destinationId)
        require(fresh.id == entry.destinationId && fresh.workspaceId == owner.workspace.id && fresh.ownerId == owner.userId)
        require(fresh.status == AssetStatus.Ready) { "Resolve pending bundle asset before retrying import" }
        require(fresh.mediaType == asset.mediaType && fresh.byteSize == asset.byteSize && fresh.checksum == asset.checksum &&
            fresh.width == asset.width && fresh.height == asset.height && fresh.durationMs == asset.durationMs)
        return ImportedMedia(fresh, requireNotNull(mediaKindForAssetMediaType(fresh.mediaType)))
    }
}
