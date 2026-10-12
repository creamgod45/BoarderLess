package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.MediaReference
import cg.creamgod.boarderless.domain.history.MediaReferenceChange
import cg.creamgod.boarderless.domain.history.mediaReference
import cg.creamgod.boarderless.domain.model.MediaNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal fun requireMediaReferenceWire(sent: OperationDto, change: MediaReferenceChange) {
    require(sent.kind == "update_object" && sent.payload["objectId"] == JsonPrimitive(change.objectId.value))
    require(sent.expectedObjectVersions == mapOf(change.objectId.value to change.expectedVersion))
    val properties = sent.payload["properties"] as? JsonObject ?: error("Original media reference patch missing")
    require(properties.keys == setOf("assetId", "mediaKind", "thumbnailAssetId"))
    require(properties["assetId"] == JsonPrimitive(change.after.assetId) &&
        properties["mediaKind"] == JsonPrimitive(change.after.mediaKind.token) &&
        properties["thumbnailAssetId"] == (change.after.thumbnailAssetId?.let(::JsonPrimitive) ?: JsonNull))
}

/** Only an exact accepted original wire plus its saved BEFORE snapshot can yield proof.
 * Cross-kind snapshots remain retained, but cannot grant restoration on current F. */
internal fun mediaReferenceRestorationsFromAcknowledgement(entry: PendingWorkspaceSubmission,
    ack: AcceptedOperationsDto): List<BackendMediaReferenceRestoration> {
    validatePendingWorkspaceSubmission(entry)
    ack.validateAcknowledgement(entry.request, entry.scope.userId)
    return entry.mediaReferenceChanges.filter { it.change.before.mediaKind == it.change.after.mediaKind }.map { saved ->
        val original = entry.request.operations.single { it.operationId == saved.wireOperationId }
        requireMediaReferenceWire(original, saved.change)
        require(ack.operations.single { it.operationId == original.operationId }.payload == original.payload)
        BackendMediaReferenceRestoration(entry.scope.workspaceId, entry.scope.userId, entry.request.transactionId,
            original.operationId, saved.change.objectId.value, saved.change.before, saved.change.after)
    }
}

/** Saved original provenance, not permission or a replacement for a fresh server receipt.
 * Never manufacture this from matching asset IDs or an inverse operationId suffix. */
@Serializable internal data class BackendMediaReferenceRestoration(
    val workspaceId: String,
    val actorId: String,
    val originalTransactionId: String,
    val originalOperationId: String,
    val objectId: String,
    val before: MediaReference,
    val after: MediaReference,
) {
    init {
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        require(listOf(workspaceId, actorId, originalTransactionId, originalOperationId, objectId, before.assetId, after.assetId).all(uuid::matches))
        require(listOfNotNull(before.thumbnailAssetId, after.thumbnailAssetId).all(uuid::matches))
        // Current F provenance does not include mediaKind. Cross-kind restoration cannot
        // reproduce APP history and must wait for an expanded backend contract.
        require(before.mediaKind == after.mediaKind)
        require(before != after)
    }

    fun request(session: WorkspaceSession, targetSide: String, operationId: String, clientSequence: Long): OperationDto {
        require(session.workspace.id.value == workspaceId && session.userId == actorId && session.canEditContent)
        require(targetSide in setOf("before", "after"))
        val current = session.workspace.objects.values.single { it.id.value == objectId } as? MediaNode
            ?: throw BackendContractException("Media restoration object unavailable")
        require(!current.locked && current.mediaReference() == if (targetSide == "before") after else before)
        require(current.version in 1 until 9_007_199_254_740_991L && clientSequence in 1..9_007_199_254_740_991L)
        require(operationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return OperationDto(operationId, clientSequence, "restore_media_references", payload = buildJsonObject {
            put("objectId", JsonPrimitive(objectId))
            put("expectedObjectVersion", JsonPrimitive(current.version))
            put("originalTransactionId", JsonPrimitive(originalTransactionId))
            put("originalOperationId", JsonPrimitive(originalOperationId))
            put("targetSide", JsonPrimitive(targetSide))
        })
    }
}

internal fun mediaReferenceRestorationFromAcknowledgement(
    baseline: WorkspaceSession, change: MediaReferenceChange,
    request: SubmitOperationsRequest, ack: AcceptedOperationsDto,
): BackendMediaReferenceRestoration {
    ack.validateAcknowledgement(request, baseline.userId)
    require(request.baseVersion == baseline.workspaceVersion && request.clientId == baseline.clientId)
    val original = baseline.workspace.objects[change.objectId] as? MediaNode ?: error("Missing original media node")
    require(original.version == change.expectedVersion && original.mediaReference() == change.before)
    val sent = request.operations.single { it.kind == "update_object" && it.payload["objectId"] == JsonPrimitive(change.objectId.value) }
    require(sent.expectedObjectVersions == mapOf(change.objectId.value to change.expectedVersion))
    val properties = sent.payload["properties"] as? JsonObject ?: error("Missing original reference patch")
    require(properties["assetId"] == JsonPrimitive(change.after.assetId) && properties["mediaKind"] == JsonPrimitive(change.after.mediaKind.token) &&
        properties["thumbnailAssetId"] == (change.after.thumbnailAssetId?.let(::JsonPrimitive) ?: JsonNull))
    val committed = ack.operations.single { it.operationId == sent.operationId }
    require(committed.payload == sent.payload)
    return BackendMediaReferenceRestoration(baseline.workspace.id.value, baseline.userId, request.transactionId,
        sent.operationId, change.objectId.value, change.before, change.after)
}
