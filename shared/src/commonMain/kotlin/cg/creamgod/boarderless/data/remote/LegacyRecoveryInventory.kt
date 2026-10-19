package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import com.russhwolf.settings.Settings

/** Read-only quiescent migration helper, not a writer lock or an atomic snapshot. Only reads
 * the requested recovery prefix; never enumerates values of unrelated settings or secrets.
 */
internal fun <T> captureLegacyRecoveryRecords(
    settings: Settings,
    prefix: String,
    maximumRecords: Int,
    decode: (String, String) -> T,
): List<T> {
    try {
        val keys = settings.keys.filter { it.startsWith(prefix) }.sorted()
        check(keys.size <= maximumRecords) { "Recovery inventory exceeds record limit" }
        var bytes = 0L
        val raw =
            keys.associateWith { key ->
                check(key.removePrefix(prefix).matches(Regex("[0-9a-f]{64}")))
                val value = checkNotNull(settings.getStringOrNull(key))
                check(value.length in 1..4096)
                bytes += value.encodeToByteArray().size
                check(bytes <= 4L * 1024 * 1024) { "Recovery inventory exceeds byte limit" }
                requireBoundedDraftJsonDepth(value)
                value
            }
        val records = keys.map { decode(it, raw.getValue(it)) }
        check(settings.keys.filter { it.startsWith(prefix) }.sorted() == keys) { "Recovery inventory changed" }
        raw.forEach { (key, value) -> check(settings.getStringOrNull(key) == value) { "Recovery record changed" } }
        return records
    } catch (failure: Exception) {
        throw BackendContractException("Legacy recovery inventory requires review", failure)
    }
}
