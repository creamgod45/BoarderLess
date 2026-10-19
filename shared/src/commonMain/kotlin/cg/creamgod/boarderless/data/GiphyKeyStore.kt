package cg.creamgod.boarderless.data

/**
 * Device-local, operating-system-protected storage for the user's own GIPHY key.
 * Implementations must never fall back to plaintext files or preferences; when no protected
 * storage exists the platform passes no store and the key stays in memory for the session.
 */
interface GiphyKeyStore {
    suspend fun load(): String?

    /** Returns false, storing nothing, when protected storage is unavailable or the write failed. */
    suspend fun save(key: String): Boolean

    suspend fun clear(): Boolean
}
