package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.json.*

/** User-supplied locator hints, never proof of missing records or initialization authority.
 * Exact strings are retained: normalizing URLs/IDs could change legacy hashed keys. */
internal data class LegacyMigrationCatalogInput private constructor(
    val scopes: List<PendingSubmissionScope>, val archives: List<StoppedSubmissionLocator>,
) {
    companion object {
        const val MaximumBytes = 4 * 1024 * 1024
        fun decode(raw: String?): LegacyMigrationCatalogInput {
            if (raw.isNullOrBlank()) return LegacyMigrationCatalogInput(emptyList(), emptyList())
            require(raw.length <= MaximumBytes && raw.encodeToByteArray(throwOnInvalidSequence = true).size <= MaximumBytes)
            requireBoundedDraftJsonDepth(raw)
            val root = parseStrictJsonObject(raw)
            require(root.keys == setOf("version", "scopes", "stopped") && root["version"] == JsonPrimitive(1))
            fun text(objectValue: JsonObject, key: String): String = requireNotNull(
                (objectValue[key] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            ).also { require(it.isNotBlank() && it.length <= 4096) }
            fun scope(value: JsonElement): PendingSubmissionScope {
                val objectValue = value as? JsonObject ?: error("Invalid legacy scope")
                require(objectValue.keys == setOf("apiBase", "userId", "clientId", "workspaceId"))
                return PendingSubmissionScope(text(objectValue, "apiBase"), text(objectValue, "userId"),
                    text(objectValue, "clientId"), text(objectValue, "workspaceId")).also {
                    WorkspaceDraftScopeBundleCodec.scopeHash(it)
                }
            }
            val scopeValues = root["scopes"] as? JsonArray ?: error("Scope catalog missing")
            val archiveValues = root["stopped"] as? JsonArray ?: error("Archive catalog missing")
            require(scopeValues.size <= 256 && archiveValues.size <= 256)
            val scopes = scopeValues.map(::scope)
            val archives = archiveValues.map {
                val value = it as? JsonObject ?: error("Invalid legacy archive locator")
                require(value.keys == setOf("scope", "transactionId"))
                StoppedSubmissionLocator(scope(requireNotNull(value["scope"])), text(value, "transactionId"))
            }
            require(scopes.distinct().size == scopes.size && archives.distinct().size == archives.size)
            return LegacyMigrationCatalogInput(scopes, archives)
        }
    }
}
