package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.data.persistence.DurableLocalRecord
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable internal data class RealtimeCheckpointScope(val serverScope: String, val userId: String,
    val clientId: String, val workspaceId: String) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")))
        listOf(userId, clientId, workspaceId).forEach(::requireRealtimeUuid)
    }
}
@Serializable internal data class RealtimeCheckpointRecord(val version: Int = 1, val scope: RealtimeCheckpointScope,
    val state: WorkspaceStateDto, val metadata: BackendRealtimeMetadata)
@Serializable private data class RealtimeCheckpointChunk(val key: String, val length: Int, val digest: String)
@Serializable private data class RealtimeCheckpointManifest(val version: Int = 2, val scope: RealtimeCheckpointScope,
    val byteLength: Int, val digest: String, val chunks: List<RealtimeCheckpointChunk>)

/** Full confirmed content + metadata published by one atomic manifest. No Settings
 * fallback, secret/ticket/Presence or optimistic session is stored. Each handle retains its
 * expected record: another writer's advance must conflict, never become our new baseline. */
internal class RealtimeCheckpointStore(private val files: AwaitedDurableLocalRecordStore,
    private val scope: RealtimeCheckpointScope, private val capabilities: BackendCanvasReadCapabilities) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private val key = SHA256().digest(("boarderless.realtime-checkpoint.v1\n" +
        json.encodeToString(RealtimeCheckpointScope.serializer(), scope)).encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private var opened = false
    private var expectedFile: DurableLocalRecord? = null
    private var expected: RealtimeCheckpointRecord? = null
    private val chunkBytes = 512 * 1024
    private val maximumBytes = 64 * 1024 * 1024
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun chunkKey(hash: String, length: Int) = digest("boarderless.realtime-chunk.v1\n$key\n$hash\n$length".encodeToByteArray())

    private fun validate(record: RealtimeCheckpointRecord) {
        require(record.version == 1 && record.scope == scope && record.state.workspaceId == scope.workspaceId)
        require(record.state.workspaceVersion in 0..RealtimeMaxSequence && record.state.throughServerSeq in 0..RealtimeMaxSequence)
        require((record.state.workspaceVersion == 0L) == (record.state.throughServerSeq == 0L))
        record.state.toDomainWorkspace(record.metadata.title, capabilities)
    }
    private suspend fun read(): Pair<DurableLocalRecord?, RealtimeCheckpointRecord?> {
        val stored = files.read(key) ?: return null to null
        require(stored.generation in 1 until Long.MAX_VALUE)
        require(stored.payload.size in 1..4 * 1024 * 1024)
        val root = parseStrictJsonObject(stored.payload.decodeToString(throwOnInvalidSequence = true))
        val raw = if (root["version"] == JsonPrimitive(2)) {
            require(root.keys == setOf("version", "scope", "byteLength", "digest", "chunks"))
            val manifest = json.decodeFromJsonElement(RealtimeCheckpointManifest.serializer(), root)
            require(manifest.scope == scope && manifest.version == 2 && manifest.byteLength in 1..maximumBytes)
            require(manifest.digest.matches(Regex("[0-9a-f]{64}")) && manifest.chunks.size in 1..129)
            require(manifest.chunks.sumOf { it.length.toLong() } == manifest.byteLength.toLong())
            val assembled = ByteArray(manifest.byteLength); var offset = 0
            for (ref in manifest.chunks) {
                currentCoroutineContext().ensureActive()
                require(ref.length in 1..chunkBytes && ref.digest.matches(Regex("[0-9a-f]{64}")))
                require(ref.key == chunkKey(ref.digest, ref.length))
                val chunk = requireNotNull(files.read(ref.key)) { "Checkpoint chunk missing" }
                require(chunk.generation == 1L && chunk.payload.size == ref.length && digest(chunk.payload) == ref.digest)
                chunk.payload.decodeToString(throwOnInvalidSequence = true)
                chunk.payload.copyInto(assembled, offset); offset += ref.length
            }
            require(offset == manifest.byteLength && digest(assembled) == manifest.digest)
            // Never mix reads across a concurrent manifest publication.
            val still = requireNotNull(files.read(key))
            require(still.generation == stored.generation && still.payload.contentEquals(stored.payload))
            parseStrictJsonObject(assembled.decodeToString(throwOnInvalidSequence = true))
        } else root // Legacy single-record v1 remains readable without rewriting it on open.
        require(raw.keys == setOf("version", "scope", "state", "metadata"))
        val record = json.decodeFromJsonElement(RealtimeCheckpointRecord.serializer(), raw)
        validate(record)
        return stored to record
    }
    /** Explicit startup read only; opening a populated handle twice cannot silently rebase. */
    suspend fun open(): RealtimeCheckpointRecord? = mutex.withLock {
        require(!opened)
        currentCoroutineContext().ensureActive()
        val (stored, record) = read()
        if (stored != null) files.confirmDurable(key, stored.generation)
        currentCoroutineContext().ensureActive()
        expectedFile = stored; expected = record; opened = true; record
    }
    /** Caller supplies a fresh authenticated full REST read only when no record exists. */
    suspend fun initialize(state: WorkspaceStateDto, metadata: BackendRealtimeMetadata): RealtimeCheckpointRecord = mutex.withLock {
        require(opened && expected == null)
        write(RealtimeCheckpointRecord(scope = scope, state = state, metadata = metadata))
    }
    suspend fun saveProjection(state: WorkspaceStateDto): Unit = mutex.withLock {
        val old = requireNotNull(expected); require(opened)
        require(state.workspaceVersion >= old.state.workspaceVersion && state.throughServerSeq >= old.state.throughServerSeq)
        if (state.workspaceVersion == old.state.workspaceVersion || state.throughServerSeq == old.state.throughServerSeq)
            require(state == old.state) { "Realtime checkpoint content cannot change without an advance" }
        write(old.copy(state = state)); Unit
    }
    suspend fun saveMetadata(metadata: BackendRealtimeMetadata): Unit = mutex.withLock {
        val old = requireNotNull(expected); require(opened)
        require(metadata.revision >= old.metadata.revision && (metadata.revision != old.metadata.revision || metadata == old.metadata))
        write(old.copy(metadata = metadata)); Unit
    }
    private suspend fun write(next: RealtimeCheckpointRecord): RealtimeCheckpointRecord {
        validate(next); currentCoroutineContext().ensureActive()
        val bytes = json.encodeToString(RealtimeCheckpointRecord.serializer(), next).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumBytes) { "Realtime checkpoint exceeds the complete-document limit" }
        val (currentFile, current) = read()
        val oldGeneration = expectedFile?.generation
        require(oldGeneration == null || oldGeneration in 1 until Long.MAX_VALUE)
        if (current == next && currentFile != null &&
            (currentFile.generation == oldGeneration || currentFile.generation == (oldGeneration ?: 0L) + 1)) {
            // Recover only the exact original local write when its durability ACK was lost.
            files.confirmDurable(key, currentFile.generation)
            currentCoroutineContext().ensureActive()
            expectedFile = currentFile; expected = next; return next
        }
        require(current == expected && currentFile?.generation == oldGeneration) { "Realtime checkpoint changed in another writer" }
        val manifest = publishChunks(bytes)
        files.compareAndSet(key, oldGeneration, manifest)
        val generation = (oldGeneration ?: 0L) + 1
        files.confirmDurable(key, generation); currentCoroutineContext().ensureActive()
        expectedFile = DurableLocalRecord(generation, manifest); expected = next
        return next
    }

    private suspend fun publishChunks(bytes: ByteArray): ByteArray {
        val refs = mutableListOf<RealtimeCheckpointChunk>(); var start = 0
        while (start < bytes.size) {
            currentCoroutineContext().ensureActive()
            var end = minOf(start + chunkBytes, bytes.size)
            // Browser records carry UTF-8 strings, not arbitrary binary. Do not split
            // inside a code point; each immutable chunk is independently valid UTF-8.
            if (end < bytes.size) while ((bytes[end].toInt() and 0xc0) == 0x80) end--
            require(end > start)
            val payload = bytes.copyOfRange(start, end)
            payload.decodeToString(throwOnInvalidSequence = true)
            val hash = digest(payload); val chunkKey = chunkKey(hash, payload.size)
            var existing = files.read(chunkKey)
            if (existing == null) {
                try { files.compareAndSet(chunkKey, null, payload) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    // Another writer may have published the identical immutable chunk,
                    // or the original CAS ACK may be lost. Never overwrite its key.
                    existing = files.read(chunkKey)
                    if (existing == null) throw failure
                }
                if (existing == null) existing = files.read(chunkKey)
            }
            val saved = requireNotNull(existing)
            require(saved.generation == 1L && saved.payload.contentEquals(payload)) { "Immutable checkpoint chunk mismatch" }
            files.confirmDurable(chunkKey, 1L); currentCoroutineContext().ensureActive()
            refs += RealtimeCheckpointChunk(chunkKey, payload.size, hash); start = end
        }
        require(refs.size <= 129)
        return json.encodeToString(RealtimeCheckpointManifest.serializer(),
            RealtimeCheckpointManifest(scope = scope, byteLength = bytes.size, digest = digest(bytes), chunks = refs))
            .encodeToByteArray(throwOnInvalidSequence = true)
        // Unreferenced chunks are retained. GC requires a separate quiescent reader-safe
        // policy; it must not race a pending manifest or remove a reader's old checkpoint.
    }

    /** Fixed-scope assembly. Startup only loads/initializes the checkpoint; the returned
     * owner's run is the separate act that obtains a ticket and opens a socket. */
    suspend fun createOwner(baseUrl: String, subscriptionId: String, canContinue: () -> Boolean,
        readMetadata: suspend () -> BackendRealtimeMetadata, readSnapshot: suspend () -> WorkspaceStateDto): BackendRealtimeOwner {
        val apiBase = "${baseUrl.trimEnd('/')}/api/v1"
        val serverScope = SHA256().digest(apiBase.encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        require(serverScope == scope.serverScope)
        suspend fun active() { currentCoroutineContext().ensureActive(); check(canContinue()) }
        active()
        val record = open() ?: run {
            active(); val state = readSnapshot(); active()
            val metadata = readMetadata(); active()
            initialize(state, metadata).also { active() }
        }
        active()
        return BackendRealtimeOwner(baseUrl, scope.workspaceId, scope.userId, scope.clientId, subscriptionId,
            record.state, record.metadata, capabilities, canContinue,
            persistProjection = { active(); saveProjection(it); active() },
            persistMetadata = { active(); saveMetadata(it); active() },
            readMetadata = { active(); readMetadata().also { active() } },
            readSnapshot = { active(); readSnapshot().also { active() } })
    }
}
