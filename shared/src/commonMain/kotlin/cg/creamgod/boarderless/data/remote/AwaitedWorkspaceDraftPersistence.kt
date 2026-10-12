package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

/** Async boundary: browser transaction completion must be awaited before network or UI
 * acknowledgement. This is deliberately not an implementation of the synchronous legacy
 * interface; no blocking bridge or Settings fallback is permitted. */
internal interface AwaitedWorkspaceDraftPersistence {
    suspend fun read(scope: PendingSubmissionScope): AwaitedDraftBundle?
    suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation)
    suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory? = null) {
        check(intent == cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit)
        append(scope, session, before, operation)
    }
    suspend fun stage(entry: PendingWorkspaceSubmission)
    suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long)
    suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
        history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) {
        check(history == null) { "Confirmed history handover is unavailable" }
        acknowledge(entry, version, seq)
    }
    suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession): WorkspaceDraftJournal
    suspend fun remove(scope: PendingSubmissionScope, id: String): Boolean
    suspend fun stop(scope: PendingSubmissionScope, transactionId: String)
    suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, expectedGeneration: Long,
        submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence)
}
internal data class AwaitedDraftBundle(val generation: Long, val bundle: WorkspaceDraftScopeBundle?)

/** One complete scope bundle published by an atomic manifest. Tombstones retain generation and
 * scope, so removing a draft cannot revive old bytes. Same-bundle retries confirm durability
 * only; concurrent changes fail instead of rebasing or generating a replacement request. */
internal class AwaitedAtomicWorkspaceDraftPersistence(private val files: AwaitedDurableLocalRecordStore,
    private val maximumRecordBytes: Int = 4 * 1024 * 1024,
    private val requireScope: (PendingSubmissionScope) -> Unit) : AwaitedWorkspaceDraftPersistence {
    @Serializable private data class Envelope(val version: Int = 1, val scope: PendingSubmissionScope, val bundle: JsonObject?)
    @Serializable private data class Chunk(val key: String, val length: Int, val digest: String)
    @Serializable private data class Manifest(val version: Int = 2, val scope: PendingSubmissionScope,
        val byteLength: Int, val digest: String, val chunks: List<Chunk>)
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    init { require(maximumRecordBytes in 1024..6 * 1024 * 1024) }
    // The domain codec's complete-bundle limit remains intact; chunking does not
    // silently raise operation/archive limits or truncate any retained evidence.
    private val maximumDocumentBytes = 6 * 1024 * 1024 + 16 * 1024
    private val chunkBytes = minOf(maximumRecordBytes, 512 * 1024)
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun chunkKey(scope: PendingSubmissionScope, hash: String, length: Int) =
        digest("boarderless.awaited-draft-chunk.v1\n${key(scope)}\n$hash\n$length".encodeToByteArray())
    private fun active(scope: PendingSubmissionScope) { requireScope(scope) }
    private fun key(scope: PendingSubmissionScope): String {
        requireScope(scope)
        val scoped = WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        return SHA256().digest("boarderless.awaited-draft-bundle.v1\n$scoped".encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    private suspend fun load(scope: PendingSubmissionScope): AwaitedDraftBundle? {
        val record = files.read(key(scope)) ?: return null
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(record.generation in 1 until 9_007_199_254_740_991L && record.payload.size in 1..maximumRecordBytes)
        val stored = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        val root = if (stored["version"] == JsonPrimitive(2)) {
            require(stored.keys == setOf("version", "scope", "byteLength", "digest", "chunks"))
            require((stored["byteLength"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
            val rawChunks = stored["chunks"] as? JsonArray ?: error("Draft chunks missing")
            require(rawChunks.size in 1..(maximumDocumentBytes / (chunkBytes - 3) + 1))
            rawChunks.forEach {
                val raw = it as? JsonObject ?: error("Invalid draft chunk")
                require(raw.keys == setOf("key", "length", "digest"))
                require((raw["length"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != null)
            }
            val manifest = json.decodeFromJsonElement(Manifest.serializer(), stored)
            require(manifest.scope == scope && manifest.byteLength in 1..maximumDocumentBytes)
            require(manifest.digest.matches(Regex("[0-9a-f]{64}")))
            require(manifest.chunks.size in 1..(maximumDocumentBytes / (chunkBytes - 3) + 1))
            require(manifest.chunks.sumOf { it.length.toLong() } == manifest.byteLength.toLong())
            val assembled = ByteArray(manifest.byteLength); var offset = 0
            for (ref in manifest.chunks) {
                currentCoroutineContext().ensureActive(); active(scope)
                require(ref.length in 1..chunkBytes && ref.digest.matches(Regex("[0-9a-f]{64}")))
                require(ref.key == chunkKey(scope, ref.digest, ref.length))
                val saved = requireNotNull(files.read(ref.key)) { "Draft chunk missing" }
                currentCoroutineContext().ensureActive(); active(scope)
                require(saved.generation == 1L && saved.payload.size == ref.length && digest(saved.payload) == ref.digest)
                saved.payload.decodeToString(throwOnInvalidSequence = true)
                saved.payload.copyInto(assembled, offset); offset += ref.length
            }
            require(offset == manifest.byteLength && digest(assembled) == manifest.digest)
            val still = requireNotNull(files.read(key(scope)))
            currentCoroutineContext().ensureActive(); active(scope)
            require(still.generation == record.generation && still.payload.contentEquals(record.payload)) {
                "Draft manifest changed during read"
            }
            parseStrictJsonObject(assembled.decodeToString(throwOnInvalidSequence = true))
        } else stored // Legacy v1 opens without rewriting or initializing a new namespace.
        require(root.keys == setOf("version", "scope", "bundle"))
        require(root["version"] == JsonPrimitive(1))
        val decoded = json.decodeFromJsonElement(Envelope.serializer(), root)
        require(decoded.version == 1 && decoded.scope == scope)
        val bundle = decoded.bundle?.let { WorkspaceDraftScopeBundleCodec.decode(it.toString().encodeToByteArray(throwOnInvalidSequence = true), scope) }
        return AwaitedDraftBundle(record.generation, bundle)
    }
    override suspend fun read(scope: PendingSubmissionScope): AwaitedDraftBundle? = mutex.withLock {
        currentCoroutineContext().ensureActive(); load(scope).also { currentCoroutineContext().ensureActive() }
    }
    suspend fun readDurable(scope: PendingSubmissionScope): AwaitedDraftBundle = mutex.withLock {
        val found = requireNotNull(load(scope)) { "Registered draft missing" }
        files.confirmDurable(key(scope), found.generation)
        currentCoroutineContext().ensureActive(); active(scope)
        require(load(scope) == found) { "Draft changed during durable read" }
        found
    }
    /** Explicit publication under a migration coordinator's Preparing receipt and writer
     * exclusion. Already-advanced state needs receipt reconciliation, not replacement.
     * Null publishes a scoped tombstone: verified absence, never an inferred missing draft. */
    suspend fun adoptReviewedBundle(scope: PendingSubmissionScope, reviewed: WorkspaceDraftScopeBundle?): AwaitedDraftBundle = mutex.withLock {
        require(reviewed == null || reviewed.scope == scope)
        val before = load(scope)
        if (before != null) require(before.bundle == reviewed) { "Existing draft state needs activation-receipt reconciliation" }
        save(scope, before, reviewed)
        val result = requireNotNull(load(scope))
        require(result.bundle == reviewed && result.generation == (before?.generation ?: 1L))
        result
    }
    private suspend fun save(scope: PendingSubmissionScope, before: AwaitedDraftBundle?, next: WorkspaceDraftScopeBundle?) {
        require(next == null || next.scope == scope)
        val bundle = next?.let { parseStrictJsonObject(WorkspaceDraftScopeBundleCodec.encode(it).decodeToString(throwOnInvalidSequence = true)) }
        val payload = json.encodeToString(Envelope.serializer(), Envelope(scope = scope, bundle = bundle)).encodeToByteArray(throwOnInvalidSequence = true)
        require(payload.size <= maximumDocumentBytes) { "Complete draft exceeds the domain limit; nothing truncated" }
        currentCoroutineContext().ensureActive()
        requireScope(scope)
        require(load(scope) == before) { "Draft changed in another writer" }
        val recordKey = key(scope)
        if (before != null && before.bundle == next) {
            files.confirmDurable(recordKey, before.generation)
        } else {
            val manifest = publishChunks(scope, payload)
            currentCoroutineContext().ensureActive(); active(scope)
            require(load(scope) == before) { "Draft changed before manifest publication" }
            files.compareAndSet(recordKey, before?.generation, manifest)
            files.confirmDurable(recordKey, (before?.generation ?: 0L) + 1)
        }
        currentCoroutineContext().ensureActive()
        active(scope)
        val saved = requireNotNull(load(scope))
        require(saved.bundle == next && saved.generation ==
            if (before != null && before.bundle == next) before.generation else (before?.generation ?: 0L) + 1)
    }
    private suspend fun publishChunks(scope: PendingSubmissionScope, bytes: ByteArray): ByteArray {
        val refs = mutableListOf<Chunk>(); var start = 0
        while (start < bytes.size) {
            currentCoroutineContext().ensureActive(); active(scope)
            var end = minOf(start + chunkBytes, bytes.size)
            if (end < bytes.size) while ((bytes[end].toInt() and 0xc0) == 0x80) end--
            require(end > start)
            val payload = bytes.copyOfRange(start, end)
            payload.decodeToString(throwOnInvalidSequence = true)
            val hash = digest(payload); val partKey = chunkKey(scope, hash, payload.size)
            var existing = files.read(partKey)
            currentCoroutineContext().ensureActive(); active(scope)
            if (existing == null) {
                try { files.compareAndSet(partKey, null, payload) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    currentCoroutineContext().ensureActive(); active(scope)
                    existing = files.read(partKey)
                    if (existing == null) throw failure
                }
                if (existing == null) existing = files.read(partKey)
            }
            currentCoroutineContext().ensureActive(); active(scope)
            val saved = requireNotNull(existing)
            require(saved.generation == 1L && saved.payload.contentEquals(payload)) { "Immutable draft chunk mismatch" }
            files.confirmDurable(partKey, 1L)
            currentCoroutineContext().ensureActive(); active(scope)
            refs += Chunk(partKey, payload.size, hash); start = end
        }
        val manifest = json.encodeToString(Manifest.serializer(), Manifest(scope = scope,
            byteLength = bytes.size, digest = digest(bytes), chunks = refs)).encodeToByteArray(throwOnInvalidSequence = true)
        require(manifest.size <= maximumRecordBytes) { "Draft manifest exceeds platform record limit" }
        return manifest // Orphans/old chunks are retained; reader-safe GC is a separate policy.
    }
    override suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) = mutex.withLock {
        val saved = load(scope)
        save(scope, saved, WorkspaceDraftBundleTransitions.append(saved?.bundle, scope, session, before, operation, randomUuid()))
    }
    override suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) = mutex.withLock {
        val saved = load(scope)
        save(scope, saved, WorkspaceDraftBundleTransitions.append(saved?.bundle, scope, session, before, operation, randomUuid(), intent, beforeHistory))
    }
    override suspend fun stage(entry: PendingWorkspaceSubmission) = mutex.withLock {
        val saved = requireNotNull(load(entry.scope)); val bundle = requireNotNull(saved.bundle)
        require(bundle.journal != null) { "Wire-only legacy requests need reconciliation before dispatch" }
        save(entry.scope, saved, WorkspaceDraftBundleTransitions.stage(bundle, entry))
    }
    override suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) = mutex.withLock {
        val saved = requireNotNull(load(entry.scope))
        save(entry.scope, saved, WorkspaceDraftBundleTransitions.acknowledge(requireNotNull(saved.bundle), entry, version, seq))
    }
    override suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
        history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) = mutex.withLock {
        val saved = requireNotNull(load(entry.scope))
        save(entry.scope, saved, WorkspaceDraftBundleTransitions.acknowledge(requireNotNull(saved.bundle), entry, version, seq, history))
    }
    override suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession): WorkspaceDraftJournal = mutex.withLock {
        val saved = requireNotNull(load(scope))
        val next = WorkspaceDraftBundleTransitions.restore(requireNotNull(saved.bundle), id, current)
        save(scope, saved, next); requireNotNull(next.journal)
    }
    override suspend fun remove(scope: PendingSubmissionScope, id: String): Boolean = mutex.withLock {
        val saved = load(scope) ?: return@withLock false
        val bundle = saved.bundle ?: return@withLock false
        if (bundle.journal?.id != id) return@withLock false
        WorkspaceDraftBundleTransitions.requireRemovable(bundle, id)
        save(scope, saved, if (bundle.retainedStopped.isEmpty()) null else bundle.copy(journal = null, acknowledged = null))
        true
    }
    override suspend fun stop(scope: PendingSubmissionScope, transactionId: String) = mutex.withLock {
        val saved = requireNotNull(load(scope))
        save(scope, saved, WorkspaceDraftBundleTransitions.stop(requireNotNull(saved.bundle), transactionId))
    }
    override suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, expectedGeneration: Long,
        submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence) = mutex.withLock {
        val saved = requireNotNull(load(scope))
        require(saved.generation == expectedGeneration && submitted.scope == scope)
        save(scope, saved, WorkspaceDraftBundleTransitions.recordStoppedEvidence(requireNotNull(saved.bundle), submitted, evidence))
    }
}
