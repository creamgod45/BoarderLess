package cg.creamgod.boarderless.data.ai

import kotlinx.serialization.Serializable

/** Non-secret connection settings only. The token never appears in this record. */
@Serializable
internal data class AiSavedSettings(
    val endpoint: String = "",
    val model: String = "",
    val dialect: String = "OpenAiChat",
    val authentication: String = "Bearer",
    val anthropicVersion: String = "",
    val maxTokens: String = "",
    val temperature: String = "",
    val chatTokenLimitField: String = "MaxCompletionTokens",
    val allowInsecureLocalHttp: Boolean = false,
    val traceToolCalls: Boolean = false,
)

/** Settings go to a private file; the token goes only to an OS-protected vault. When no vault is
 * available the token is never persisted, so callers must keep it memory-only for the session. */
internal interface AiSettingsStore {
    /** True when a token can be saved under operating-system protection on this machine. */
    val tokenProtected: Boolean

    suspend fun loadSettings(): AiSavedSettings?

    suspend fun saveSettings(settings: AiSavedSettings): Boolean

    suspend fun loadToken(): String?

    /** Returns false (and stores nothing) when no protected vault exists or the write failed. */
    suspend fun saveToken(token: String): Boolean

    suspend fun clearToken(): Boolean
}

/** Desktop only for now; other platforms return null and keep tokens in memory. */
internal expect fun aiSettingsStore(): AiSettingsStore?
