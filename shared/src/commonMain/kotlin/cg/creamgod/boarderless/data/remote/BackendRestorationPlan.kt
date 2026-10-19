package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.json.*

/** Derived only from a complete committed deletion transaction and its prior authoritative state.
 * Not a receipt lookup, permission check or executable history item. Caller must retain this
 * provenance, refresh ACL/version and obtain user confirmation before submitting restoration.
 */
internal data class BackendRestorationPlan(
    val workspaceId: String,
    val deletionTransactionId: String,
    val throughServerSeq: Long,
    val objectTombstoneVersions: Map<String, Long>,
    val relationTombstoneVersions: Map<String, Long>,
) {
    fun operations(startingSequence: Long): List<OperationDto> {
        require(startingSequence >= 0 && startingSequence <= MaxSafe - 2)
        return buildList {
            fun addRestore(
                kind: String,
                key: String,
                versions: Map<String, Long>,
            ) {
                if (versions.isEmpty()) return
                add(
                    OperationDto(
                        randomUuid(),
                        startingSequence + size + 1,
                        kind,
                        expectedObjectVersions = versions,
                        payload = buildJsonObject { put(key, JsonArray(versions.keys.map(::JsonPrimitive))) },
                    ),
                )
            }
            // Restore ALL groups/children before any relation endpoints. Server checks atomically.
            addRestore("restore_objects", "objectIds", objectTombstoneVersions)
            addRestore("restore_relations", "relationIds", relationTombstoneVersions)
        }
    }
}

private const val MaxSafe = 9_007_199_254_740_991L

internal fun planCommittedDeletionRestoration(
    before: WorkspaceStateDto,
    records: List<CommittedWorkspaceOperationDto>,
): BackendRestorationPlan {
    fun invalid(): Nothing = throw BackendContractException("Cannot prove deletion restoration provenance")
    if (records.isEmpty() || records.any { it.operationType !in setOf("delete_objects", "delete_relations") }) invalid()
    before.toDomainWorkspace("Restoration provenance validation")
    val first = records.first()
    val last = records.last()
    // Existing reducer enforces checkpoint, complete transaction, known objects, exact cascade and
    // valid resulting hierarchy. No partial transaction or unknown deletion may produce a plan.
    reduceCommittedProjection(before, before.workspaceId, records, first.serverSeq, last.serverSeq, first.workspaceVersion)
    val objectVersions = before.objects.associate { it.objectId to it.objectVersion }
    val relationVersions = before.relations.associate { it.relationId to it.relationVersion }
    val objects = linkedMapOf<String, Long>()
    val relations = linkedMapOf<String, Long>()

    fun collect(
        payload: JsonObject,
        key: String,
        prior: Map<String, Long>,
        target: MutableMap<String, Long>,
    ) {
        val ids = payload[key] as? JsonArray ?: invalid()
        for (value in ids) {
            val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
            val version = prior[id] ?: invalid()
            if (version !in 1..(MaxSafe - 2) || target.put(id, version + 1) != null) invalid()
        }
    }
    for (record in records) {
        if (record.operationType == "delete_objects") {
            collect(record.payload, "objectIds", objectVersions, objects)
            collect(record.payload, "cascadedRelationIds", relationVersions, relations)
        } else {
            collect(record.payload, "relationIds", relationVersions, relations)
        }
    }
    if (objects.size > 500 || relations.size > 500) invalid()
    return BackendRestorationPlan(
        before.workspaceId,
        first.transactionId,
        last.serverSeq,
        objects.toMap(),
        relations.toMap(),
    )
}
