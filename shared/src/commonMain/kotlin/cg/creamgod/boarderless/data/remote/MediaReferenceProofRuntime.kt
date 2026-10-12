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

/** Private confirmed-original storage, not server authorization or permission to resend. */
class MediaReferenceProofRuntime internal constructor(private val records: () -> AwaitedDurableLocalRecordStore) {
    internal fun store(requireScope: (PendingSubmissionScope) -> Unit) = MediaReferenceCommitStore(records(), requireScope)
    internal fun historyStore(requireScope: (PendingSubmissionScope) -> Unit) = WorkspaceHistoryStore(records(), requireScope)
    internal fun pickedAssetUploads(requireScope: (cg.creamgod.boarderless.data.persistence.PickedAssetUploadScope) -> Unit) =
        cg.creamgod.boarderless.data.persistence.PickedAssetUploadStore(records(), requireScope)
}

@Serializable internal data class MediaReferenceCommitEvidence(val version: Int = 1,
    val scope: PendingSubmissionScope, val localOperationId: String, val transactionId: String,
    val requestDigest: String, val workspaceVersion: Long, val fromServerSeq: Long, val toServerSeq: Long,
    val changes: List<PendingMediaReferenceChange>)

/** Immutable per-original-transaction record. Partial local failures retain pending;
 * repeated settlement confirms exact evidence, never replaces an original history. */
internal class MediaReferenceCommitStore(private val files: AwaitedDurableLocalRecordStore,
    private val requireScope: (PendingSubmissionScope) -> Unit) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(scope: PendingSubmissionScope, transactionId: String): String {
        requireScope(scope); requireRealtimeUuid(transactionId)
        return digest("boarderless.media-reference-commit.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}\n$transactionId".encodeToByteArray())
    }
    private fun validate(value: MediaReferenceCommitEvidence) {
        require(value.version == 1 && value.localOperationId.length in 1..4096)
        listOf(value.scope.userId, value.scope.clientId, value.scope.workspaceId, value.transactionId).forEach(::requireRealtimeUuid)
        require(value.requestDigest.matches(Regex("[0-9a-f]{64}")))
        require(value.workspaceVersion in 1..RealtimeMaxSequence && value.fromServerSeq in 1..RealtimeMaxSequence &&
            value.toServerSeq in value.fromServerSeq..RealtimeMaxSequence)
        require(value.changes.size in 1..200 && value.changes.map { it.wireOperationId }.distinct().size == value.changes.size)
        value.changes.forEach {
            requireRealtimeUuid(it.wireOperationId); requireRealtimeUuid(it.change.objectId.value)
            require(it.change.expectedVersion in 1 until RealtimeMaxSequence && it.change.before != it.change.after)
            listOf(it.change.before, it.change.after).forEach { ref ->
                requireRealtimeUuid(ref.assetId); ref.thumbnailAssetId?.let(::requireRealtimeUuid)
            }
        }
    }
    private suspend fun load(scope: PendingSubmissionScope, transactionId: String): MediaReferenceCommitEvidence? {
        val record = files.read(key(scope, transactionId)) ?: return null
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(record.generation == 1L && record.payload.size in 1..512 * 1024)
        val raw = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "scope", "localOperationId", "transactionId", "requestDigest", "workspaceVersion", "fromServerSeq", "toServerSeq", "changes"))
        require(raw["version"] == JsonPrimitive(1))
        listOf("workspaceVersion", "fromServerSeq", "toServerSeq").forEach {
            require((raw[it] as? JsonPrimitive)?.takeUnless { number -> number.isString }?.longOrNull != null)
        }
        val changes = raw["changes"] as? JsonArray ?: error("Media reference evidence missing")
        require(changes.size in 1..200)
        changes.forEach {
            val change = ((it as? JsonObject)?.get("change") as? JsonObject) ?: error("Invalid saved media reference")
            require((change["expectedVersion"] as? JsonPrimitive)?.takeUnless { number -> number.isString }?.longOrNull != null)
        }
        val value = json.decodeFromJsonElement(MediaReferenceCommitEvidence.serializer(), raw)
        validate(value); require(value.scope == scope && value.transactionId == transactionId)
        return value
    }
    suspend fun read(scope: PendingSubmissionScope, transactionId: String): MediaReferenceCommitEvidence? = mutex.withLock {
        val found = load(scope, transactionId) ?: return@withLock null
        files.confirmDurable(key(scope, transactionId), 1L)
        currentCoroutineContext().ensureActive(); requireScope(scope)
        require(load(scope, transactionId) == found)
        found
    }
    suspend fun record(entry: PendingWorkspaceSubmission, ack: AcceptedOperationsDto) = mutex.withLock {
        validatePendingWorkspaceSubmission(entry); ack.validateAcknowledgement(entry.request, entry.scope.userId)
        require(entry.mediaReferenceChanges.isNotEmpty())
        entry.mediaReferenceChanges.forEach { saved ->
            val wire = entry.request.operations.single { it.operationId == saved.wireOperationId }
            requireMediaReferenceWire(wire, saved.change)
            require(ack.operations.single { it.operationId == wire.operationId }.payload == wire.payload)
        }
        val value = MediaReferenceCommitEvidence(scope = entry.scope, localOperationId = entry.localOperationId,
            transactionId = entry.request.transactionId, requestDigest = digest(json.encodeToString(
                SubmitOperationsRequest.serializer(), entry.request).encodeToByteArray(throwOnInvalidSequence = true)),
            workspaceVersion = ack.workspaceVersion, fromServerSeq = ack.fromServerSeq, toServerSeq = ack.toServerSeq,
            changes = entry.mediaReferenceChanges.toList())
        validate(value)
        val bytes = json.encodeToString(MediaReferenceCommitEvidence.serializer(), value).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size <= 512 * 1024)
        val before = load(entry.scope, value.transactionId)
        require(before == null || before == value) { "Original media reference evidence differs" }
        currentCoroutineContext().ensureActive(); requireScope(entry.scope)
        if (before == null) files.compareAndSet(key(entry.scope, value.transactionId), null, bytes)
        files.confirmDurable(key(entry.scope, value.transactionId), 1L)
        currentCoroutineContext().ensureActive(); requireScope(entry.scope)
        val saved = requireNotNull(load(entry.scope, value.transactionId))
        require(saved == value)
        saved
    }
}

internal fun MediaReferenceCommitEvidence.historyOrigins(): List<cg.creamgod.boarderless.domain.history.MediaReferenceHistoryOrigin> =
    changes.filter { it.change.before.mediaKind == it.change.after.mediaKind }.map {
        cg.creamgod.boarderless.domain.history.MediaReferenceHistoryOrigin(scope.workspaceId, scope.userId,
            transactionId, it.wireOperationId, it.change.objectId, it.change.before, it.change.after, it.change.expectedVersion)
    }
