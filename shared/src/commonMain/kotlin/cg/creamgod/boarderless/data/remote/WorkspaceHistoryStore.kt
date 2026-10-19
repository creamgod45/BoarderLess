package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

/** Complete local history at a confirmed server checkpoint. Not a server receipt,
 * permission to replay drafts, or authority to restore assets. */
@Serializable
internal data class SavedWorkspaceHistory(
    val version: Int = 1,
    val scope: PendingSubmissionScope,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
    val transactionId: String,
    val requestDigest: String,
    val history: WorkspaceHistory,
)

internal data class DurableWorkspaceHistory(val generation: Long, val value: SavedWorkspaceHistory)

/** Full snapshots, immutable content-addressed chunks, and one CAS manifest.
 * No stack trimming, fallback, automatic overwrite after a race, or chunk GC. */
internal class WorkspaceHistoryStore(
    private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PendingSubmissionScope) -> Unit,
) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
    private val maximumBytes = 64 * 1024 * 1024
    private val chunkBytes = 512 * 1024

    @Serializable private data class Chunk(val key: String, val digest: String, val length: Int)
    @Serializable private data class Manifest(val version: Int = 1, val scope: PendingSubmissionScope,
        val length: Int, val digest: String, val chunks: List<Chunk>)

    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(scope: PendingSubmissionScope) = digest(
        "boarderless.history.manifest.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}".encodeToByteArray())
    private fun chunkKey(scope: PendingSubmissionScope, hash: String, length: Int) = digest(
        "boarderless.history.chunk.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}\n$hash\n$length".encodeToByteArray())
    private suspend fun active(scope: PendingSubmissionScope) {
        currentCoroutineContext().ensureActive(); requireScope(scope)
    }
    private fun validate(value: SavedWorkspaceHistory) {
        require(value.version == 1 && value.history.workspace.id.value == value.scope.workspaceId)
        WorkspaceDraftScopeBundleCodec.scopeHash(value.scope)
        require(value.workspaceVersion in 0..RealtimeMaxSequence && value.lastServerSeq in 0..RealtimeMaxSequence)
        requireRealtimeUuid(value.transactionId)
        require(value.requestDigest.matches(Regex("[0-9a-f]{64}")))
        require(value.history.undoOperations.all { it.operationId.isNotBlank() } &&
            value.history.redoOperations.all { it.operationId.isNotBlank() })
        val ids = (value.history.undoOperations + value.history.redoOperations).map { it.operationId }
        require(ids.distinct().size == ids.size)
    }

    private suspend fun load(scope: PendingSubmissionScope, confirmChunks: Boolean = false): DurableWorkspaceHistory? {
        active(scope)
        val manifestRecord = files.read(key(scope)) ?: return null
        active(scope)
        require(manifestRecord.generation in 1 until RealtimeMaxSequence)
        require(manifestRecord.payload.size in 1..256 * 1024)
        val raw = parseStrictJsonObject(manifestRecord.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "scope", "length", "digest", "chunks"))
        require(raw["version"] == JsonPrimitive(1))
        require((raw["length"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
        (raw["chunks"] as? JsonArray ?: error("History chunk list missing")).forEach { item ->
            val ref = item as? JsonObject ?: error("Invalid history chunk")
            require(ref.keys == setOf("key", "digest", "length"))
            require((ref["length"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
        }
        val manifest = json.decodeFromJsonElement(Manifest.serializer(), raw)
        require(manifest.scope == scope && manifest.length in 1..maximumBytes)
        require(manifest.digest.matches(Regex("[0-9a-f]{64}")))
        require(manifest.chunks.size in 1..256 && manifest.chunks.sumOf { it.length.toLong() } == manifest.length.toLong())
        val bytes = ByteArray(manifest.length)
        var offset = 0
        for (ref in manifest.chunks) {
            require(ref.length in 1..chunkBytes && ref.digest.matches(Regex("[0-9a-f]{64}")))
            require(ref.key == chunkKey(scope, ref.digest, ref.length))
            val chunk = requireNotNull(files.read(ref.key)) { "History chunk missing" }
            active(scope)
            require(chunk.generation == 1L && chunk.payload.size == ref.length && digest(chunk.payload) == ref.digest)
            if (confirmChunks) {
                files.confirmDurable(ref.key, 1L)
                active(scope)
            }
            chunk.payload.decodeToString(throwOnInvalidSequence = true)
            chunk.payload.copyInto(bytes, offset); offset += ref.length
        }
        require(offset == bytes.size && digest(bytes) == manifest.digest)
        val payload = parseStrictJsonObject(bytes.decodeToString(throwOnInvalidSequence = true))
        require(payload.keys == setOf("version", "scope", "workspaceVersion", "lastServerSeq", "transactionId", "requestDigest", "history"))
        require(payload["version"] == JsonPrimitive(1))
        listOf("workspaceVersion", "lastServerSeq").forEach {
            require((payload[it] as? JsonPrimitive)?.takeUnless { number -> number.isString }?.longOrNull != null)
        }
        val value = json.decodeFromJsonElement(SavedWorkspaceHistory.serializer(), payload)
        validate(value); require(value.scope == scope)
        val still = requireNotNull(files.read(key(scope)))
        active(scope)
        require(still.generation == manifestRecord.generation && still.payload.contentEquals(manifestRecord.payload)) {
            "History changed during read"
        }
        return DurableWorkspaceHistory(manifestRecord.generation, value)
    }

    suspend fun readDurable(scope: PendingSubmissionScope): DurableWorkspaceHistory? = mutex.withLock {
        val found = load(scope, confirmChunks = true) ?: return@withLock null
        files.confirmDurable(key(scope), found.generation)
        active(scope)
        require(load(scope) == found) { "History changed during confirmation" }
        found
    }

    /** Caller must supply the generation it actually reviewed. Unknown CAS outcomes
     * must be read back; they never authorize an automatic second write. */
    suspend fun save(value: SavedWorkspaceHistory, expectedGeneration: Long?): DurableWorkspaceHistory = mutex.withLock {
        validate(value)
        val bytes = json.encodeToString(SavedWorkspaceHistory.serializer(), value)
            .encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumBytes) { "Complete history exceeds storage limit; nothing trimmed" }
        val before = load(value.scope)
        require(before?.generation == expectedGeneration) { "History generation changed" }
        before?.let {
            require(value.workspaceVersion >= it.value.workspaceVersion && value.lastServerSeq >= it.value.lastServerSeq) {
                "History cannot rewind the confirmed checkpoint"
            }
        }
        if (before != null && before.value == value) {
            files.confirmDurable(key(value.scope), before.generation)
            active(value.scope)
            require(load(value.scope) == before)
            return@withLock before
        }
        val refs = mutableListOf<Chunk>()
        var start = 0
        while (start < bytes.size) {
            active(value.scope)
            var end = minOf(start + chunkBytes, bytes.size)
            if (end < bytes.size) while ((bytes[end].toInt() and 0xc0) == 0x80) end--
            require(end > start)
            val chunk = bytes.copyOfRange(start, end)
            val hash = digest(chunk)
            val chunkKey = chunkKey(value.scope, hash, chunk.size)
            val previous = files.read(chunkKey)
            active(value.scope)
            if (previous == null) files.compareAndSet(chunkKey, null, chunk)
            else require(previous.generation == 1L && previous.payload.contentEquals(chunk))
            files.confirmDurable(chunkKey, 1L)
            active(value.scope)
            val saved = requireNotNull(files.read(chunkKey))
            active(value.scope)
            require(saved.generation == 1L && saved.payload.contentEquals(chunk))
            refs += Chunk(chunkKey, hash, chunk.size); start = end
        }
        val manifest = json.encodeToString(Manifest.serializer(), Manifest(scope = value.scope,
            length = bytes.size, digest = digest(bytes), chunks = refs)).encodeToByteArray(throwOnInvalidSequence = true)
        require(manifest.size <= 256 * 1024)
        require(load(value.scope) == before) { "History changed before publication" }
        active(value.scope)
        files.compareAndSet(key(value.scope), expectedGeneration, manifest)
        val generation = (expectedGeneration ?: 0L) + 1
        files.confirmDurable(key(value.scope), generation)
        active(value.scope)
        val result = requireNotNull(load(value.scope))
        require(result.generation == generation && result.value == value)
        result
    }
}
