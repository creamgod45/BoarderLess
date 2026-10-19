package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PreparedWorkspaceSubmission
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.CanvasStyle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

internal fun preparedRequestDigest(request: SubmitOperationsRequest): String =
    SHA256().digest(Json { encodeDefaults = true }.encodeToString(SubmitOperationsRequest.serializer(), request).encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

/** Reconstruction is validation only. Mapper-generated UUIDs are replaced with saved IDs.
 * Any mapper/schema drift or altered domain payload fails before network access. */
internal fun rebuildPreparedRequest(identity: PreparedWorkspaceSubmission, operation: WorkspaceOperation,
    capabilities: BackendCanvasReadCapabilities = BackendCanvasReadCapabilities()): SubmitOperationsRequest {
    require(operation.operationId == identity.localOperationId)
    val expanded = operation.toExpandedDtos(0, capabilities)
    require(expanded.size == identity.operationIds.size)
    val request = SubmitOperationsRequest(clientId = identity.clientId, transactionId = identity.transactionId,
        baseVersion = identity.baseVersion, operations = expanded.mapIndexed { index, dto ->
            dto.copy(operationId = identity.operationIds[index], clientSeq = identity.clientSequences[index])
        })
    require(preparedRequestDigest(request) == identity.requestDigest) { "Prepared request reconstruction differs" }
    return request
}

/** Lossless legacy contract projection only. Round-trip check forbids dropping newer fields. */
internal fun recoveryProjection(baseline: WorkspaceSession, capabilities: BackendCanvasReadCapabilities = BackendCanvasReadCapabilities()): WorkspaceStateDto {
    val workspace = baseline.workspace
    require(capabilities.sequenceDiagrams || (workspace.sequenceDiagrams.isEmpty() && workspace.sequenceDiagramsVersion == 0L))
    require(capabilities.canvasStyle || (workspace.canvasStyle == CanvasStyle() && workspace.canvasStyleVersion == 0L))
    val objects = workspace.objects.values.map { node ->
        val p = CreateObjectsOperation("recovery-projection", listOf(node)).toExpandedDtos(0, capabilities).single().payload
        CanvasObjectDto(node.id.value, p.getValue("objectType").jsonPrimitive.content, node.version,
            node.parentId?.value, node.zIndex, node.locked, p.getValue("transform").jsonObject, p.getValue("properties").jsonObject)
    }
    val relations = workspace.relations.values.map { edge ->
        val p = CreateRelationsOperation("recovery-projection", listOf(edge)).toExpandedDtos(0, capabilities).single().payload
        RelationDto(edge.id.value, edge.version, edge.sourceObjectId.value, edge.targetObjectId.value,
            p.getValue("direction").jsonPrimitive.content, edge.intent, edge.label, p.getValue("style").jsonObject)
    }
    val result = WorkspaceStateDto(workspace.id.value, baseline.workspaceVersion, baseline.lastServerSeq, objects, relations,
        canvasStyle = workspace.canvasStyle, canvasStyleVersion = workspace.canvasStyleVersion,
        sequenceDiagrams = workspace.sequenceDiagrams.values.sortedBy { it.containerId.value }, sequenceDiagramsVersion = workspace.sequenceDiagramsVersion)
    require(result.toDomainWorkspace(workspace.title, capabilities) == workspace) { "Recovery projection would lose snapshot fields" }
    return result
}

/** Stop exactly at the original transaction boundary; later head records are not its baseline.
 * Bounded complete history includes other transactions committed before the original. */
internal suspend fun collectCommittedThrough(afterSeq: Long, throughSeq: Long,
    readNext: suspend (Long) -> FullCatchUpOperationsDto): FullCatchUpOperationsDto {
    require(afterSeq in 0 until 9_007_199_254_740_991L && throughSeq in (afterSeq + 1)..9_007_199_254_740_991L)
    require(throughSeq - afterSeq <= MaxCatchUpReplayRecords)
    val records = mutableListOf<CommittedWorkspaceOperationDto>()
    var cursor = afterSeq
    var head = throughSeq
    repeat(8) {
        currentCoroutineContext().ensureActive()
        val page = readNext(cursor)
        require(page.lastServerSeq in head..9_007_199_254_740_991L && page.operations.size in 1..1000)
        head = page.lastServerSeq
        for (record in page.operations) {
            require(record.serverSeq == cursor + 1 && record.serverSeq <= head)
            records.add(record); cursor = record.serverSeq
            if (cursor == throughSeq) return FullCatchUpOperationsDto(records.toList(), throughSeq, false)
        }
        require(page.hasMore && cursor < throughSeq) { "Original committed history is incomplete" }
    }
    error("Original committed history exceeds recovery bound")
}
