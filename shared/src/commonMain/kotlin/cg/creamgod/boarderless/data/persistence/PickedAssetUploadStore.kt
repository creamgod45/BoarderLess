package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import cg.creamgod.boarderless.domain.model.WorkspaceId

@Serializable internal data class PickedAssetUploadScope(
    val serverScope: String, val userId: String, val clientId: String, val workspaceId: WorkspaceId,
) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")))
        listOf(userId, clientId, workspaceId.value).forEach {
            require(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        }
    }
}
internal fun AssetPreparationWireRequest.pickerScope() = PickedAssetUploadScope(serverScope, userId, clientId, workspaceId)

internal data class DurablePickedAssetUpload(val generation: Long, val value: PickedAssetUploadPreparation)

/** Fixed request identity, CAS successors and awaited readback. No retry, deletion,
 * file handles, signed URLs, server receipts or automatic resumption authority.
 * Discovery is published first; an index-only entry means unfinished local reservation,
 * not a server receipt or permission to allocate another requestId. */
internal class PickedAssetUploadStore(
    private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PickedAssetUploadScope) -> Unit,
) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private val maximumBytes = 512 * 1024
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable private data class Index(val version: Int = 1, val scope: PickedAssetUploadScope,
        val requests: List<AssetPreparationWireRequest> = emptyList(),
        @EncodeDefault(EncodeDefault.Mode.NEVER) val archivedRequestIds: List<String> = emptyList())
    private fun indexKey(scope: PickedAssetUploadScope): String = SHA256().digest(
        "boarderless.picked-upload-index.v2\n${scope.serverScope}\n${scope.userId}\n${scope.clientId}\n${scope.workspaceId.value}"
            .encodeToByteArray(throwOnInvalidSequence = true),
    ).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(request: AssetPreparationWireRequest): String = SHA256().digest(
        "boarderless.picked-upload.v2\n${request.serverScope}\n${request.userId}\n${request.clientId}\n${request.workspaceId.value}\n${request.requestId}"
            .encodeToByteArray(throwOnInvalidSequence = true),
    ).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private suspend fun active(request: AssetPreparationWireRequest) {
        active(request.pickerScope())
    }
    private suspend fun active(scope: PickedAssetUploadScope) {
        currentCoroutineContext().ensureActive()
        requireScope(scope)
    }

    private suspend fun loadIndex(scope: PickedAssetUploadScope): Pair<Long?, Index> {
        active(scope)
        val record = files.read(indexKey(scope))
        active(scope)
        if (record == null) return null to Index(scope = scope)
        require(record.generation in 1 until 9_007_199_254_740_991L)
        require(record.payload.size in 1..maximumBytes)
        val raw = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        val value = json.decodeFromJsonElement(Index.serializer(), raw)
        require(raw == Json.parseToJsonElement(json.encodeToString(value)))
        require(value.version == 1 && value.scope == scope && value.requests.size <= 1024)
        require(value.requests.all { it.pickerScope() == scope })
        require(value.requests.map { it.requestId }.distinct().size == value.requests.size)
        require(value.archivedRequestIds.distinct().size == value.archivedRequestIds.size)
        require(value.archivedRequestIds.all { id -> value.requests.any { it.requestId == id } })
        return record.generation to value
    }

    private suspend fun confirmedIndex(scope: PickedAssetUploadScope): Pair<Long?, Index> {
        val found = loadIndex(scope)
        if (found.first != null) {
            files.confirmDurable(indexKey(scope), requireNotNull(found.first))
            active(scope)
            require(loadIndex(scope) == found) { "Upload index changed during confirmation" }
        }
        return found
    }

    /** Local read only; missing request record is surfaced, never hidden from discovery. */
    suspend fun discover(scope: PickedAssetUploadScope, archived: Boolean? = null): List<AssetPreparationWireRequest> = mutex.withLock {
        val index = confirmedIndex(scope).second
        index.requests.filter { archived == null || (it.requestId in index.archivedRequestIds) == archived }
    }

    /** Explicit local visibility only. All original identities, intents and leases remain.
     * Terminal receipt/consent is checked by the controller, never inferred from this bit. */
    suspend fun setArchived(request: AssetPreparationWireRequest, archived: Boolean) = mutex.withLock {
        val scope = request.pickerScope()
        val before = confirmedIndex(scope)
        require(before.second.requests.single { it.requestId == request.requestId } == request)
        requireNotNull(confirmed(request))
        val ids = before.second.archivedRequestIds
        val nextIds = if (archived) (ids + request.requestId).distinct() else ids - request.requestId
        if (nextIds == ids) return@withLock
        val next = before.second.copy(archivedRequestIds = nextIds)
        val generation = requireNotNull(before.first) + 1
        require(generation in 1 until 9_007_199_254_740_991L)
        val bytes = json.encodeToString(next).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumBytes)
        active(scope)
        files.compareAndSet(indexKey(scope), before.first, bytes)
        active(scope)
        files.confirmDurable(indexKey(scope), generation)
        active(scope)
        require(loadIndex(scope) == (generation to next)) { "Upload archive visibility is unconfirmed" }
    }

    private suspend fun indexRequest(request: AssetPreparationWireRequest) {
        val scope = request.pickerScope()
        val before = loadIndex(scope)
        val existing = before.second.requests.singleOrNull { it.requestId == request.requestId }
        require(existing == null || existing == request) { "Indexed upload identity changed" }
        val next = if (existing == null) before.second.copy(requests = before.second.requests + request) else before.second
        require(next.requests.size <= 1024) { "Upload recovery index is full" }
        val generation = requireNotNull(if (existing == null) (before.first ?: 0L) + 1L else before.first)
        require(generation in 1 until 9_007_199_254_740_991L)
        if (existing == null) {
            val bytes = json.encodeToString(next).encodeToByteArray(throwOnInvalidSequence = true)
            require(bytes.size in 1..maximumBytes)
            active(scope)
            files.compareAndSet(indexKey(scope), before.first, bytes)
            active(scope)
        }
        files.confirmDurable(indexKey(scope), generation)
        active(scope)
        require(loadIndex(scope) == (generation to next)) { "Upload index publication is unconfirmed" }
    }

    private suspend fun load(request: AssetPreparationWireRequest): DurablePickedAssetUpload? {
        active(request)
        val record = files.read(key(request))
        active(request)
        if (record == null) return null
        require(record.generation in 1 until 9_007_199_254_740_991L)
        require(record.payload.size in 1..maximumBytes)
        val raw = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        val value = json.decodeFromJsonElement(PickedAssetUploadPreparation.serializer(), raw)
        // Reject unknown fields, coercible numeric strings and missing explicit nulls.
        require(raw == Json.parseToJsonElement(json.encodeToString(value)))
        require(value.request == request) { "Upload reservation identity changed" }
        return DurablePickedAssetUpload(record.generation, value)
    }

    private suspend fun confirmed(request: AssetPreparationWireRequest): DurablePickedAssetUpload? {
        val found = load(request) ?: return null
        files.confirmDurable(key(request), found.generation)
        active(request)
        require(load(request) == found) { "Upload reservation changed during confirmation" }
        return found
    }

    suspend fun readDurable(request: AssetPreparationWireRequest): DurablePickedAssetUpload? = mutex.withLock {
        val index = confirmedIndex(request.pickerScope())
        val indexed = index.second.requests.singleOrNull { it.requestId == request.requestId }
        require(indexed == null || indexed == request) { "Indexed upload identity changed" }
        val saved = confirmed(request)
        require(saved == null || indexed == request) { "Upload reservation discovery is missing" }
        saved
    }

    /** Null expectedGeneration allocates only this original request, not a new ID.
     * Any unknown/CAS outcome stops; caller must re-read instead of overwriting. */
    suspend fun save(expectedGeneration: Long?, next: PickedAssetUploadPreparation): DurablePickedAssetUpload = mutex.withLock {
        val request = next.request
        // Index first: a crash can leave a discoverable intent, never a hidden request.
        // No HTTP caller may proceed until both publications have been confirmed.
        indexRequest(request)
        val before = load(request)
        require(before?.generation == expectedGeneration) { "Upload reservation generation changed" }
        if (before == null) {
            require(next.remoteIdentity == null && next.ticketRequestIds.isEmpty() && next.leases.isEmpty())
            require(!next.prepareAttempted && next.uploadAttemptLeaseIds.isEmpty() && next.completionAttemptLeaseIds.isEmpty())
        } else {
            next.requireSuccessorOf(before.value)
            if (next == before.value) return@withLock requireNotNull(confirmed(request))
        }
        val generation = (expectedGeneration ?: 0L) + 1L
        require(generation in 1 until 9_007_199_254_740_991L)
        val bytes = json.encodeToString(next).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..maximumBytes) { "Upload reservation exceeds durable record limit" }
        active(request)
        files.compareAndSet(key(request), expectedGeneration, bytes)
        active(request)
        val saved = requireNotNull(confirmed(request))
        require(saved.generation == generation && saved.value == next) { "Upload reservation publication is unconfirmed" }
        saved
    }
}
