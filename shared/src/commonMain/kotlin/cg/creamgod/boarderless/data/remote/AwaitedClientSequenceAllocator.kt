package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

internal interface AwaitedClientSequenceAllocator {
    suspend fun reserve(scope: PendingSubmissionScope, count: Int): LongRange
    suspend fun advance(scope: PendingSubmissionScope, sequence: Long)
}
internal data class AwaitedClientSequenceRecord(val generation: Long, val highWater: Long)

/** One origin/user/client ledger across all workspaces. Must be explicitly initialized
 * using a verified floor while legacy writers are excluded. Missing/corrupt data never
 * means zero; failed/uncertain reservations are not retried or reused automatically. */
internal class AwaitedAtomicClientSequenceAllocator(private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PendingSubmissionScope) -> Unit) : AwaitedClientSequenceAllocator {
    @Serializable private data class Ledger(val version: Int = 1, val apiBase: String, val userId: String,
        val clientId: String, val highWater: Long)
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private fun key(scope: PendingSubmissionScope): String {
        requireScope(scope)
        require(scope.apiBase.isNotBlank()); requireRealtimeUuid(scope.userId); requireRealtimeUuid(scope.clientId)
        val identity = json.encodeToString(Ledger.serializer(), Ledger(apiBase = scope.apiBase, userId = scope.userId, clientId = scope.clientId, highWater = 0))
        return SHA256().digest("boarderless.awaited-client-sequence.v1\n$identity".encodeToByteArray(throwOnInvalidSequence = true))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    private suspend fun load(scope: PendingSubmissionScope): AwaitedClientSequenceRecord? {
        val stored = files.read(key(scope)) ?: return null
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(stored.generation in 1 until RealtimeMaxSequence && stored.payload.size in 1..16 * 1024)
        val raw = parseStrictJsonObject(stored.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "apiBase", "userId", "clientId", "highWater"))
        require(raw["version"] == JsonPrimitive(1) && (raw["highWater"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
        val ledger = json.decodeFromJsonElement(Ledger.serializer(), raw)
        require(ledger.version == 1 && ledger.apiBase == scope.apiBase && ledger.userId == scope.userId && ledger.clientId == scope.clientId)
        require(ledger.highWater in 0..RealtimeMaxSequence)
        return AwaitedClientSequenceRecord(stored.generation, ledger.highWater)
    }
    suspend fun read(scope: PendingSubmissionScope): AwaitedClientSequenceRecord? = mutex.withLock { load(scope) }
    suspend fun readDurable(scope: PendingSubmissionScope): AwaitedClientSequenceRecord = mutex.withLock {
        val found = requireNotNull(load(scope)) { "Registered sequence ledger missing" }
        files.confirmDurable(key(scope), found.generation)
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(load(scope) == found) { "Sequence ledger changed during durable read" }
        found
    }
    suspend fun initialize(scope: PendingSubmissionScope, verifiedFloor: Long) = mutex.withLock {
        require(verifiedFloor in 0..RealtimeMaxSequence && load(scope) == null)
        publish(scope, null, verifiedFloor)
    }
    private suspend fun publish(scope: PendingSubmissionScope, before: AwaitedClientSequenceRecord?, highWater: Long) {
        require(highWater in 0..RealtimeMaxSequence && (before == null || highWater >= before.highWater))
        val payload = json.encodeToString(Ledger.serializer(), Ledger(apiBase = scope.apiBase, userId = scope.userId,
            clientId = scope.clientId, highWater = highWater)).encodeToByteArray(throwOnInvalidSequence = true)
        require(payload.size <= 16 * 1024)
        currentCoroutineContext().ensureActive(); require(load(scope) == before)
        val recordKey = key(scope)
        if (before != null && highWater == before.highWater) files.confirmDurable(recordKey, before.generation)
        else {
            files.compareAndSet(recordKey, before?.generation, payload)
            files.confirmDurable(recordKey, (before?.generation ?: 0L) + 1)
        }
        currentCoroutineContext().ensureActive()
        requireScope(scope)
    }
    override suspend fun reserve(scope: PendingSubmissionScope, count: Int): LongRange = mutex.withLock {
        require(count in 1..200)
        val before = requireNotNull(load(scope)) { "Sequence ledger needs verified initialization" }
        require(before.highWater <= RealtimeMaxSequence - count)
        publish(scope, before, before.highWater + count)
        (before.highWater + 1)..(before.highWater + count)
    }
    override suspend fun advance(scope: PendingSubmissionScope, sequence: Long) = mutex.withLock {
        require(sequence in 0..RealtimeMaxSequence)
        val before = requireNotNull(load(scope)) { "Sequence ledger needs verified initialization" }
        publish(scope, before, maxOf(before.highWater, sequence))
    }
}
