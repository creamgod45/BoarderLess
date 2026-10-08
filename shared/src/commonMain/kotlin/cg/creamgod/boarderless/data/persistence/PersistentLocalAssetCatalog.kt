package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** One forced catalog supports native enumeration without scanning or trusting original filenames. */
internal class PersistentLocalAssetCatalog(
    loadFiles: () -> DurableLocalRecordStore,
    private val lock: LocalDocumentLock,
) {
    private val files by lazy(loadFiles)
    private val json = Json { encodeDefaults = true }
    @Serializable private data class Stored(val id: String, val workspaceId: String, val ownerId: String,
        val mediaType: String, val byteSize: Long, val checksum: String, val width: Int?, val height: Int?,
        val durationMs: Long?, val status: String, val createdAt: String) {
        fun asset() = WorkspaceAsset(id, WorkspaceId(workspaceId), ownerId, mediaType, byteSize, checksum, width, height,
            durationMs, requireNotNull(AssetStatus.fromToken(status)), createdAt)
    }
    @Serializable private data class Catalog(val version: Int = 1, val entries: List<Stored> = emptyList())
    private fun WorkspaceAsset.stored() = Stored(id, workspaceId.value, ownerId, mediaType, byteSize, checksum,
        width, height, durationMs, status.token, createdAt)
    private fun read(): Pair<Long?, Catalog> {
        val record = files.read(Key) ?: return null to Catalog()
        val raw = record.payload.decodeToString(throwOnInvalidSequence = true)
        val catalog = json.decodeFromJsonElement(Catalog.serializer(), parseStrictJsonObject(raw))
        require(catalog.version == 1 && catalog.entries.size <= 4096 && catalog.entries.map { it.id }.distinct().size == catalog.entries.size)
        catalog.entries.forEach {
            validateLocalAssetId(it.id)
            require(it.byteSize in 1..MaxWorkspaceAssetBytes && it.checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
            require(it.status == AssetStatus.Pending.token || it.status == AssetStatus.Ready.token)
            it.asset()
        }
        return record.generation to catalog
    }
    private fun write(generation: Long?, catalog: Catalog) {
        val bytes = json.encodeToString(catalog).encodeToByteArray()
        require(bytes.size <= 4 * 1024 * 1024)
        files.compareAndSet(Key, generation, bytes)
    }
    fun get(id: String): WorkspaceAsset = lock.withLock {
        validateLocalAssetId(id)
        read().second.entries.single { it.id == id }.asset()
    }
    fun list(session: WorkspaceSession): List<WorkspaceAsset> = lock.withLock {
        read().second.entries.filter { it.workspaceId == session.workspace.id.value && it.ownerId == session.userId }.map { it.asset() }
    }
    fun add(asset: WorkspaceAsset) = lock.withLock {
        validateLocalAssetId(asset.id)
        require(asset.status == AssetStatus.Pending && asset.byteSize in 1..MaxWorkspaceAssetBytes)
        require(asset.checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
        val (generation, catalog) = read()
        require(catalog.entries.size < 4096 && catalog.entries.none { it.id == asset.id })
        write(generation, catalog.copy(entries = catalog.entries + asset.stored()))
    }
    /** Called only after the original's hash and publication barriers have succeeded. */
    fun ready(expected: WorkspaceAsset): WorkspaceAsset = lock.withLock {
        val (generation, catalog) = read()
        val current = catalog.entries.single { it.id == expected.id }.asset()
        val ready = expected.copy(status = AssetStatus.Ready)
        require(expected.status == AssetStatus.Pending || expected.status == AssetStatus.Ready)
        require(current == expected || current == ready)
        if (current.status == AssetStatus.Ready) files.confirmDurable(Key, requireNotNull(generation))
        else write(generation, catalog.copy(entries = catalog.entries.map { if (it.id == current.id) ready.stored() else it }))
        ready
    }
    fun abandon(expected: WorkspaceAsset) = lock.withLock {
        val (generation, catalog) = read()
        val current = catalog.entries.single { it.id == expected.id }.asset()
        require(current == expected && current.status == AssetStatus.Pending)
        write(generation, catalog.copy(entries = catalog.entries.filterNot { it.id == current.id }))
    }
    private companion object {
        val Key = SHA256().digest("BoarderLess.local-asset-catalog.v1".encodeToByteArray()).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    }
}

internal fun validateLocalAssetId(id: String) {
    require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
}
