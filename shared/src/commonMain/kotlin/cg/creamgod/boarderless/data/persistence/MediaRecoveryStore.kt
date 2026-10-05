package cg.creamgod.boarderless.data.persistence

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** Device-only reconciliation reminders. No source handles, URLs, headers or binary data. */
class MediaRecoveryStore(private val settings: Settings = defaultMediaRecoverySettings()) {
    fun list(userId: String, workspaceId: String): List<String> {
        val value = settings.getStringOrNull(key(userId, workspaceId))
            ?: legacyKey(userId, workspaceId).takeIf { it in settings.keys }?.let(settings::getStringOrNull)
            ?: return emptyList()
        val ids = Json.decodeFromString<List<String>>(value)
        require(ids.size <= MaximumEntries && ids.all { it.isNotBlank() && it.length <= 256 })
        return ids.distinct()
    }

    /** Persist before requesting completion. Failure stops completion rather than losing its ID. */
    fun record(userId: String, workspaceId: String, assetId: String) {
        require(assetId.isNotBlank() && assetId.length <= 256)
        val ids = list(userId, workspaceId)
        val updated = if (assetId in ids) ids else {
            check(ids.size < MaximumEntries) { "Media recovery list is full; review existing reminders first" }
            ids + assetId
        }
        save(userId, workspaceId, updated)
    }

    /** Removes a local reminder only. Never deletes a server asset or retries its upload. */
    fun dismiss(userId: String, workspaceId: String, assetId: String) {
        val remaining = list(userId, workspaceId).filterNot { it == assetId }
        save(userId, workspaceId, remaining)
    }

    private fun key(userId: String, workspaceId: String): String {
        // Java Preferences limits keys to 80 chars. Hash the unambiguous scope, not either ID
        // independently or a truncated prefix; all platforms share the same 70-char key.
        val bytes = legacyKey(userId, workspaceId).encodeToByteArray()
        val digest = SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return "mr.v2.$digest"
    }

    private fun legacyKey(userId: String, workspaceId: String): String {
        require(userId.isNotBlank() && workspaceId.isNotBlank())
        return "mediaRecovery.v1:${userId.length}:$userId:${workspaceId.length}:$workspaceId"
    }

    private fun save(userId: String, workspaceId: String, ids: List<String>) {
        val key = key(userId, workspaceId)
        val encoded = ids.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) }
        if (encoded == null) settings.remove(key) else settings.putString(key, encoded)
        check(settings.getStringOrNull(key) == encoded) { "Media recovery write could not be verified" }
        // Read keys first: even get/remove of an overlong absent legacy key can throw on JVM.
        val legacy = legacyKey(userId, workspaceId)
        if (legacy in settings.keys) settings.remove(legacy)
    }

    companion object { const val MaximumEntries = 128 }
}

/** Desktop adds a flush barrier; other platforms retain their existing Settings semantics. */
internal expect fun defaultMediaRecoverySettings(): Settings
