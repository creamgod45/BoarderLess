package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Discoverability for legacy hashed manifests; NOT atomic persistence or writer exclusion.
 * Registration happens before draft writes. A catalog-only scope is harmless; missing
 * manifests/catalog entries never become permission to ignore unknown published keys. */
internal class LegacyMigrationCatalogStore(private val settings: Settings) {
    @Serializable private data class Catalog(val version: Int = 1,
        val scopes: List<PendingSubmissionScope> = emptyList())
    private val json = Json { encodeDefaults = true }
    private val key = "backend.legacyMigrationScopeCatalog.v1"
    private fun validate(scopes: List<PendingSubmissionScope>) {
        require(scopes.size <= 256 && scopes.distinct().size == scopes.size)
        scopes.forEach {
            require(listOf(it.apiBase, it.userId, it.clientId, it.workspaceId).all { value -> value.length in 1..4096 })
            WorkspaceDraftScopeBundleCodec.scopeHash(it)
        }
    }
    fun read(): List<PendingSubmissionScope> {
        val raw = settings.getStringOrNull(key) ?: return emptyList() // Not a complete-source assertion.
        require(raw.encodeToByteArray(throwOnInvalidSequence = true).size <= 4 * 1024 * 1024)
        val root = parseStrictJsonObject(raw)
        require(root.keys == setOf("version", "scopes") && root["version"] == JsonPrimitive(1))
        val catalog = json.decodeFromJsonElement(Catalog.serializer(), root)
        validate(catalog.scopes)
        return catalog.scopes
    }
    /** Caller serializes legacy mutations with submissionMutex and excludes other processes. */
    fun register(scope: PendingSubmissionScope) {
        val before = read()
        if (scope in before) return
        val next = (before + scope).sortedBy(WorkspaceDraftScopeBundleCodec::scopeHash)
        validate(next)
        require(read() == before) { "Legacy migration catalog changed" }
        val raw = json.encodeToString(Catalog.serializer(), Catalog(scopes = next))
        require(raw.encodeToByteArray(throwOnInvalidSequence = true).size <= 4 * 1024 * 1024)
        settings.putString(key, raw)
        require(settings.getStringOrNull(key) == raw && read() == next) { "Legacy migration catalog not saved" }
    }
}
