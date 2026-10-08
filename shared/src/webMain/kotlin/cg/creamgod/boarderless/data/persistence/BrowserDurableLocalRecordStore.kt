package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Checksummed records. CAS compares the entire previously validated document in one IDB transaction. */
internal class BrowserDurableLocalRecordStore(private val library: Boolean = false) : AwaitedDurableLocalRecordStore {
    private val maximumBytes = if (library) 4 * 1024 * 1024 else 1024 * 1024
    @Serializable private data class Record(val generation: Long, val payload: String, val checksum: String)
    private val json = Json { encodeDefaults = true }
    private fun checksum(generation: Long, payload: ByteArray): String {
        val hash = SHA256(); hash.update("$generation\n".encodeToByteArray()); hash.update(payload)
        return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    private fun decode(raw: String): DurableLocalRecord {
        require(raw.length <= 2 * maximumBytes + 4096)
        val obj = parseStrictJsonObject(raw)
        require(obj.keys == setOf("generation", "payload", "checksum"))
        val record = json.decodeFromJsonElement(Record.serializer(), obj)
        require(record.generation in 1..9007199254740991L)
        val bytes = record.payload.encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumBytes && checksum(record.generation, bytes) == record.checksum)
        return DurableLocalRecord(record.generation, bytes)
    }
    private suspend fun operation(action: String, key: String, expected: String? = null, next: String? = null): String? {
        require(key.matches(Regex("[0-9a-f]{64}")))
        val id = randomUuid()
        return suspendCancellableCoroutine { waiter ->
            fun cancel() { if (library) browserCancelLibraryRecordOperation(id) else browserCancelRecordOperation(id) }
            waiter.invokeOnCancellation { cancel() }
            try {
                val callback: (String?, String?) -> Unit = { value, error ->
                    if (waiter.isActive) {
                        if (error != null) waiter.resumeWithException(IllegalStateException("Browser durable storage failed"))
                        else waiter.resume(value)
                    }
                }
                if (library) browserLibraryRecordOperation(id, action, key, expected, next, callback)
                else browserRecordOperation(id, action, key, expected, next, callback)
            } catch (_: Exception) {
                cancel()
                if (waiter.isActive) waiter.resumeWithException(IllegalStateException("Browser durable storage unavailable"))
            }
        }
    }
    override suspend fun read(key: String): DurableLocalRecord? = operation("read", key)?.let(::decode)
    override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
        require(payload.size in 1..maximumBytes)
        val old = operation("read", key)
        check(old?.let(::decode)?.generation == generation) { "Record generation changed" }
        require(generation == null || generation in 1 until 9007199254740991L)
        val next = (generation ?: 0L) + 1
        val document = json.encodeToString(Record(next, payload.decodeToString(throwOnInvalidSequence = true), checksum(next, payload)))
        operation("cas", key, old, document)
    }
    override suspend fun confirmDurable(key: String, generation: Long) {
        val old = checkNotNull(operation("read", key))
        check(decode(old).generation == generation)
        operation("cas", key, old, old) // Strict transaction re-publication; never just a request-success ACK.
    }
}

internal expect fun browserRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
internal expect fun browserCancelRecordOperation(id: String)

internal expect fun browserLibraryRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
internal expect fun browserCancelLibraryRecordOperation(id: String)
