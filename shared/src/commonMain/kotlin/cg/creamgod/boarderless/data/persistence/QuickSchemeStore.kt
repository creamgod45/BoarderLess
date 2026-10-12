package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.i18n.Strings
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable

interface QuickSchemeRepository {
    val cloudSyncRuntime: CloudSchemeRuntime? get() = null
    fun pendingIdentifiedSaves(): List<QuickSchemeSaveIntent> = emptyList()
    fun stageIdentifiedSave(intent: QuickSchemeSaveIntent): Unit = error("Identified scheme save unavailable")
    val supportsIdentifiedSave: Boolean get() = false
    fun saveIdentified(requestId: String, payload: String, name: String? = null, schemaVersion: Int = 1,
        sourceWorkspaceId: String? = null): QuickScheme = error("Identified scheme save unavailable")
    fun save(
        payload: String,
        name: String? = null,
        schemaVersion: Int = 1,
        sourceWorkspaceId: String? = null,
    ): QuickScheme

    fun list(): List<QuickScheme>

    fun latest(): QuickScheme? = list().lastOrNull()

    fun rename(
        id: Int,
        name: String,
    ): QuickScheme?

    fun delete(id: Int): Boolean
}

/** Private pending definition, not a saved library item, canvas operation or cloud receipt. */
@Serializable data class QuickSchemeSaveIntent(val requestId: String, val scheme: QuickScheme) {
    init {
        require(requestId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        require(scheme.id == 0)
    }
}

class QuickSchemeStore(
    private val settings: Settings = Settings(),
) : QuickSchemeRepository {
    override fun save(
        payload: String,
        name: String?,
        schemaVersion: Int,
        sourceWorkspaceId: String?,
    ): QuickScheme {
        require(schemaVersion > 0) { "Schema version must be positive" }
        require(sourceWorkspaceId == null || sourceWorkspaceId.isNotBlank()) { "Source workspace must not be blank" }
        val nextIndex = settings.getInt(CountKey, 0) + 1
        val scheme =
            QuickScheme(
                id = nextIndex,
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: Strings.content.scheme(nextIndex),
                payload = payload,
                schemaVersion = schemaVersion,
                sourceWorkspaceId = sourceWorkspaceId,
            )
        settings.putInt(CountKey, nextIndex)
        settings.putString("$ItemPrefix$nextIndex.name", scheme.name)
        settings.putString("$ItemPrefix$nextIndex.payload", scheme.payload)
        settings.putInt("$ItemPrefix$nextIndex.schemaVersion", scheme.schemaVersion)
        scheme.sourceWorkspaceId?.let { settings.putString("$ItemPrefix$nextIndex.sourceWorkspaceId", it) }
        return scheme
    }

    override fun list(): List<QuickScheme> = (1..settings.getInt(CountKey, 0)).mapNotNull(::read)

    /** Explicit read-only recovery of the legacy indexed catalog. Does not claim unindexed
     * keys are absent, repair partial records, normalize payloads or delete old content. */
    internal fun listForRecovery(): List<QuickScheme> {
        val count = settings.getInt(CountKey, 0)
        require(count in 0..10_000)
        var bytes = 0L
        val result = (1..count).mapNotNull { index ->
            val prefix = "$ItemPrefix$index."
            val payload = settings.getStringOrNull(prefix + "payload")
            if (payload == null) {
                require(listOf("name", "schemaVersion", "sourceWorkspaceId").none { settings.hasKey(prefix + it) }) {
                    "Legacy scheme record is partial"
                }
                return@mapNotNull null
            }
            require(payload.length <= 4 * 1024 * 1024)
            val item = requireNotNull(read(index))
            require(item.payload == payload)
            val size = payload.encodeToByteArray(throwOnInvalidSequence = true).size
            require(size <= 4 * 1024 * 1024)
            bytes += size
            require(bytes <= 32L * 1024 * 1024)
            require(item.name.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096)
            require(item.sourceWorkspaceId?.encodeToByteArray(throwOnInvalidSequence = true)?.size?.let { it <= 4096 } != false)
            item
        }
        require(settings.getInt(CountKey, 0) == count) { "Legacy scheme catalog changed" }
        return result
    }

    override fun latest(): QuickScheme? = list().lastOrNull()

    override fun rename(
        id: Int,
        name: String,
    ): QuickScheme? {
        val scheme = read(id) ?: return null
        val renamed = scheme.copy(name = name.trim().takeIf { it.isNotEmpty() } ?: scheme.name)
        settings.putString("$ItemPrefix$id.name", renamed.name)
        return renamed
    }

    override fun delete(id: Int): Boolean {
        if (read(id) == null) return false
        settings.remove("$ItemPrefix$id.name")
        settings.remove("$ItemPrefix$id.payload")
        settings.remove("$ItemPrefix$id.schemaVersion")
        settings.remove("$ItemPrefix$id.sourceWorkspaceId")
        return true
    }

    private fun read(index: Int): QuickScheme? {
        val payload = settings.getStringOrNull("$ItemPrefix$index.payload") ?: return null
        return QuickScheme(
            id = index,
            name = settings.getString("$ItemPrefix$index.name", Strings.content.scheme(index)),
            payload = payload,
            schemaVersion = settings.getInt("$ItemPrefix$index.schemaVersion", LegacySchemaVersion),
            sourceWorkspaceId = settings.getStringOrNull("$ItemPrefix$index.sourceWorkspaceId")?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val CountKey = "quickScheme.count"
        const val ItemPrefix = "quickScheme.item."
        const val LegacySchemaVersion = 1
    }
}

@Serializable
data class QuickScheme(
    val id: Int,
    val name: String,
    val payload: String,
    val schemaVersion: Int = 1,
    /** Local provenance, not a transferable authorization grant. Null for old records. */
    val sourceWorkspaceId: String? = null,
)
