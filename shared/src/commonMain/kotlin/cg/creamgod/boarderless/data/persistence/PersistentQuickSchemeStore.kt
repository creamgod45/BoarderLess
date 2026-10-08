package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One validated library per durable CAS record. Platform wrappers serialize calls;
 * competing processes are fenced by the storage generation and never silently overwrite. */
internal class PersistentQuickSchemeStore(private val files: DurableLocalRecordStore) : QuickSchemeRepository {
    private val json = Json { encodeDefaults = true }

    @Serializable private data class Library(
        val version: Int = 1,
        val nextId: Long = 1,
        val items: List<QuickScheme> = emptyList(),
    )

    private data class Read(
        val generation: Long?,
        val library: Library,
    )

    private fun validate(library: Library) {
        require(library.version == 1 && library.nextId in 1..Int.MAX_VALUE.toLong() + 1)
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
            require(scheme.schemaVersion in 1..8)
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
        require(root.keys == setOf("version", "nextId", "items"))
        val library = json.decodeFromJsonElement(Library.serializer(), root)
        validate(library)
        return Read(record.generation, library)
    }

    private fun write(
        before: Read,
        library: Library,
    ) {
        validate(library)
        val bytes = json.encodeToString(library).encodeToByteArray()
        require(bytes.size <= 4 * 1024 * 1024) { "Local scheme library is full" }
        files.compareAndSet(Key, before.generation, bytes)
    }

    override fun list(): List<QuickScheme> = read().library.items

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

    private companion object {
        val Key = org.kotlincrypto.hash.sha2.SHA256().digest("BoarderLess.local-schemes.v1".encodeToByteArray()).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    }
}
