package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One validated library per durable CAS record. Platform wrappers serialize calls;
 * competing processes are fenced by the storage generation and never silently overwrite. */
internal class PersistentQuickSchemeStore(private val files: DurableLocalRecordStore) : QuickSchemeRepository {
    override val supportsIdentifiedSave = true
    private val json = Json { encodeDefaults = true }

    @Serializable private data class Library(
        val version: Int = 3,
        val nextId: Long = 1,
        val items: List<QuickScheme> = emptyList(),
        val saveReceipts: List<SaveReceipt> = emptyList(),
        val pendingSaves: List<QuickSchemeSaveIntent> = emptyList(),
    )
    @Serializable private data class SaveReceipt(val requestId: String, val digest: String, val schemeId: Int)
    @Serializable private data class SaveIdentity(val payload: String, val name: String?, val schemaVersion: Int, val sourceWorkspaceId: String?)

    private data class Read(
        val generation: Long?,
        val library: Library,
    )

    private fun validate(library: Library) {
        require(library.version in 1..3 && library.nextId in 1..Int.MAX_VALUE.toLong() + 1)
        require(library.version >= 2 || library.saveReceipts.isEmpty())
        require(library.version == 3 || library.pendingSaves.isEmpty())
        require(library.pendingSaves.size <= 128 && library.pendingSaves.map { it.requestId }.distinct().size == library.pendingSaves.size)
        require(library.pendingSaves.none { pending -> library.saveReceipts.any { it.requestId == pending.requestId } })
        library.pendingSaves.forEach {
            require(it.scheme.name == it.scheme.name.trim())
            validate(Library(items = listOf(it.scheme.copy(id = 1)), nextId = 2))
        }
        require(library.saveReceipts.size <= 4096 && library.saveReceipts.map { it.requestId }.distinct().size == library.saveReceipts.size)
        library.saveReceipts.forEach {
            require(it.requestId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            require(it.digest.matches(Regex("[0-9a-f]{64}")) && it.schemeId.toLong() in 1 until library.nextId)
        }
        require(library.items.size <= 512)
        require(
            library.items.map { it.id } ==
                library.items
                    .map { it.id }
                    .distinct()
                    .sorted(),
        )
        library.items.forEach { scheme ->
            require(scheme.id.toLong() in 1 until library.nextId)
            require(scheme.name.isNotBlank() && scheme.name.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096)
            require(scheme.payload.isNotBlank() && scheme.payload.encodeToByteArray(throwOnInvalidSequence = true).size <= 1024 * 1024)
            require(scheme.schemaVersion in 1..9)
            parseStrictJsonObject(scheme.payload)
            require(
                cg.creamgod.boarderless.feature.canvas
                    .decodeQuickSchemePayload(scheme) != null,
            ) { "Invalid scheme definition or version" }
            require(
                scheme.sourceWorkspaceId == null ||
                    (
                        scheme.sourceWorkspaceId.isNotBlank() &&
                            scheme.sourceWorkspaceId.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096
                    ),
            )
        }
    }

    private fun read(): Read {
        val record = files.read(Key) ?: return Read(null, Library())
        val bytes = record.payload
        require(bytes.size <= 4 * 1024 * 1024)
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        val root = parseStrictJsonObject(text)
        require(root.keys == setOf("version", "nextId", "items") || root.keys == setOf("version", "nextId", "items", "saveReceipts") ||
            root.keys == setOf("version", "nextId", "items", "saveReceipts", "pendingSaves"))
        val library = json.decodeFromJsonElement(Library.serializer(), root)
        require(library.version < 2 || "saveReceipts" in root) { "Scheme save receipts are missing" }
        require(library.version < 3 || "pendingSaves" in root) { "Pending scheme saves are missing" }
        validate(library)
        return Read(record.generation, library)
    }

    private fun write(
        before: Read,
        library: Library,
    ) {
        validate(library)
        val bytes = json.encodeToString(library.copy(version = 3)).encodeToByteArray()
        require(bytes.size <= 4 * 1024 * 1024) { "Local scheme library is full" }
        files.compareAndSet(Key, before.generation, bytes)
    }

    override fun list(): List<QuickScheme> = read().library.items
    override fun pendingIdentifiedSaves(): List<QuickSchemeSaveIntent> = read().library.pendingSaves
    override fun stageIdentifiedSave(intent: QuickSchemeSaveIntent) {
        val normalized = intent.copy(scheme = intent.scheme.copy(name = intent.scheme.name.trim()))
        val before = read()
        if (before.library.saveReceipts.any { it.requestId == intent.requestId }) {
            saveIdentified(intent.requestId, normalized.scheme.payload, normalized.scheme.name,
                normalized.scheme.schemaVersion, normalized.scheme.sourceWorkspaceId)
            return
        }
        before.library.pendingSaves.singleOrNull { it.requestId == intent.requestId }?.let {
            require(it == normalized) { "Pending scheme definition changed" }
            files.confirmDurable(Key, requireNotNull(before.generation)); return
        }
        require(before.library.pendingSaves.size < 128)
        write(before, before.library.copy(version = 3, pendingSaves = before.library.pendingSaves + normalized))
    }

    override fun saveIdentified(requestId: String, payload: String, name: String?, schemaVersion: Int,
        sourceWorkspaceId: String?): QuickScheme {
        require(requestId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        val identity = SaveIdentity(payload, name?.trim()?.takeIf { it.isNotEmpty() }, schemaVersion, sourceWorkspaceId)
        val digest = org.kotlincrypto.hash.sha2.SHA256().digest(json.encodeToString(identity).encodeToByteArray(throwOnInvalidSequence = true))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val before = read()
        before.library.pendingSaves.singleOrNull { it.requestId == requestId }?.let {
            require(it.scheme == QuickScheme(0, requireNotNull(identity.name), payload, schemaVersion, sourceWorkspaceId)) {
                "Pending scheme definition changed"
            }
        }
        before.library.saveReceipts.singleOrNull { it.requestId == requestId }?.let { receipt ->
            require(receipt.digest == digest) { "Scheme save request changed" }
            val saved = requireNotNull(before.library.items.singleOrNull { it.id == receipt.schemeId }) { "Saved scheme was deleted; request cannot recreate it" }
            require(saved.payload == payload && saved.schemaVersion == schemaVersion && saved.sourceWorkspaceId == sourceWorkspaceId &&
                saved.name == (identity.name ?: Strings.content.scheme(saved.id))) { "Saved scheme changed; old request cannot overwrite it" }
            files.confirmDurable(Key, requireNotNull(before.generation))
            return saved
        }
        require(before.library.saveReceipts.size < 4096 && before.library.nextId <= Int.MAX_VALUE && before.library.items.size < 512)
        val id = before.library.nextId.toInt()
        val scheme = QuickScheme(id, identity.name ?: Strings.content.scheme(id), payload, schemaVersion, sourceWorkspaceId)
        write(before, before.library.copy(version = 3, nextId = id.toLong() + 1, items = before.library.items + scheme,
            saveReceipts = before.library.saveReceipts + SaveReceipt(requestId, digest, id),
            pendingSaves = before.library.pendingSaves.filterNot { it.requestId == requestId }))
        return scheme
    }

    override fun save(
        payload: String,
        name: String?,
        schemaVersion: Int,
        sourceWorkspaceId: String?,
    ): QuickScheme {
        val before = read()
        require(before.library.nextId <= Int.MAX_VALUE && before.library.items.size < 512)
        val id = before.library.nextId.toInt()
        val scheme =
            QuickScheme(
                id,
                name?.trim()?.takeIf { it.isNotEmpty() } ?: Strings.content.scheme(id),
                payload,
                schemaVersion,
                sourceWorkspaceId,
            )
        write(before, before.library.copy(nextId = id.toLong() + 1, items = before.library.items + scheme))
        return scheme
    }

    override fun rename(
        id: Int,
        name: String,
    ): QuickScheme? {
        val before = read()
        val scheme = before.library.items.singleOrNull { it.id == id } ?: return null
        val renamed = scheme.copy(name = name.trim().takeIf { it.isNotEmpty() } ?: scheme.name)
        if (renamed != scheme) write(before, before.library.copy(items = before.library.items.map { if (it.id == id) renamed else it }))
        return renamed
    }

    override fun delete(id: Int): Boolean {
        val before = read()
        if (before.library.items.none { it.id == id }) return false
        write(before, before.library.copy(items = before.library.items.filterNot { it.id == id }))
        return true
    }

    internal companion object {
        val Key = org.kotlincrypto.hash.sha2.SHA256().digest("BoarderLess.local-schemes.v1".encodeToByteArray()).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    }
}
