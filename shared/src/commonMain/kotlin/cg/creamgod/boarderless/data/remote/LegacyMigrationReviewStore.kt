package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

/** Immutable private review packets, selected by the publication receipt's exact digest.
 * No latest-plan guessing, source deletion, GC or runtime/sequence initialization. */
internal class LegacyMigrationReviewStore(private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PendingSubmissionScope) -> Unit) {
    @Serializable private data class Chunk(val key: String, val length: Int, val digest: String)
    @Serializable private data class Manifest(val version: Int = 1, val target: PendingSubmissionScope,
        val sourceDigest: String, val planDigest: String, val byteLength: Int, val chunks: List<Chunk>)
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(target: PendingSubmissionScope, planDigest: String): String {
        requireScope(target); require(planDigest.matches(Regex("[0-9a-f]{64}")))
        return digest("boarderless.legacy-review.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(target)}\n$planDigest".encodeToByteArray())
    }
    private fun chunkKey(key: String, hash: String, length: Int) =
        digest("boarderless.legacy-review-chunk.v1\n$key\n$hash\n$length".encodeToByteArray())
    private suspend fun active(target: PendingSubmissionScope) { currentCoroutineContext().ensureActive(); requireScope(target) }
    private suspend fun load(target: PendingSubmissionScope, planDigest: String): LegacySubmissionMigrationReview? {
        active(target); val recordKey = key(target, planDigest)
        val record = files.read(recordKey) ?: return null; active(target)
        require(record.generation == 1L && record.payload.size in 1..64 * 1024)
        val raw = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "target", "sourceDigest", "planDigest", "byteLength", "chunks"))
        require(raw["version"] == JsonPrimitive(1))
        require((raw["byteLength"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
        val chunks = raw["chunks"] as? JsonArray ?: error("Review chunks missing")
        require(chunks.size in 1..129)
        chunks.forEach {
            val chunk = it as? JsonObject ?: error("Invalid review chunk")
            require(chunk.keys == setOf("key", "length", "digest"))
            require((chunk["length"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
        }
        val manifest = json.decodeFromJsonElement(Manifest.serializer(), raw)
        require(manifest.target == target && manifest.planDigest == planDigest && manifest.byteLength in 1..64 * 1024 * 1024)
        require(manifest.chunks.sumOf { it.length.toLong() } == manifest.byteLength.toLong())
        val bytes = ByteArray(manifest.byteLength); var offset = 0
        for (ref in manifest.chunks) {
            require(ref.length in 1..512 * 1024 && ref.digest.matches(Regex("[0-9a-f]{64}")))
            require(ref.key == chunkKey(recordKey, ref.digest, ref.length))
            active(target); val chunk = requireNotNull(files.read(ref.key)); active(target)
            require(chunk.generation == 1L && chunk.payload.size == ref.length && digest(chunk.payload) == ref.digest)
            chunk.payload.decodeToString(throwOnInvalidSequence = true)
            chunk.payload.copyInto(bytes, offset); offset += ref.length
        }
        require(offset == bytes.size && digest(bytes) == planDigest)
        val review = LegacySubmissionMigrationReview.decode(bytes)
        require(review.sourceDigest == manifest.sourceDigest && review.scopes.any { it.scope == target })
        files.confirmDurable(recordKey, 1L); active(target)
        val still = requireNotNull(files.read(recordKey)); active(target)
        require(still.generation == 1L && still.payload.contentEquals(record.payload))
        return review
    }
    suspend fun read(target: PendingSubmissionScope, planDigest: String) = mutex.withLock { load(target, planDigest) }
    suspend fun save(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview) = mutex.withLock {
        active(target); require(review.scopes.any { it.scope == target })
        val previous = load(target, review.planDigest)
        if (previous != null) {
            require(previous.sourceDigest == review.sourceDigest && previous.encode().contentEquals(review.encode()))
            return@withLock
        }
        val bytes = review.encode(); val recordKey = key(target, review.planDigest)
        val refs = mutableListOf<Chunk>(); var start = 0
        suspend fun immutable(partKey: String, payload: ByteArray) {
            active(target); var found = files.read(partKey); active(target)
            if (found == null) {
                try { files.compareAndSet(partKey, null, payload) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { active(target); found = files.read(partKey); if (found == null) throw failure }
                if (found == null) found = files.read(partKey)
            }
            active(target); val saved = requireNotNull(found)
            require(saved.generation == 1L && saved.payload.contentEquals(payload))
            files.confirmDurable(partKey, 1L); active(target)
        }
        while (start < bytes.size) {
            var end = minOf(start + 512 * 1024, bytes.size)
            if (end < bytes.size) while ((bytes[end].toInt() and 0xc0) == 0x80) end--
            require(end > start)
            val payload = bytes.copyOfRange(start, end); val hash = digest(payload)
            val partKey = chunkKey(recordKey, hash, payload.size)
            immutable(partKey, payload); refs += Chunk(partKey, payload.size, hash); start = end
        }
        require(refs.size <= 129)
        val manifest = json.encodeToString(Manifest.serializer(), Manifest(target = target, sourceDigest = review.sourceDigest,
            planDigest = review.planDigest, byteLength = bytes.size, chunks = refs)).encodeToByteArray(throwOnInvalidSequence = true)
        require(manifest.size <= 64 * 1024); immutable(recordKey, manifest)
        val saved = requireNotNull(load(target, review.planDigest))
        require(saved.sourceDigest == review.sourceDigest && saved.encode().contentEquals(bytes))
    }
}
