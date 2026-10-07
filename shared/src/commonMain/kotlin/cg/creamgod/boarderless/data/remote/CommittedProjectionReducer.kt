package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.json.*

private const val MaxProjectionSequence = 9_007_199_254_740_991L

private fun invalidReplay(): Nothing = throw BackendContractException("Invalid committed projection transaction")

private fun JsonObject.keysOnly(vararg keys: String) {
    if (this.keys.any { it !in keys }) invalidReplay()
}

private fun JsonElement.replayString(): String =
    (this as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content ?: invalidReplay()

private fun JsonObject.id(key: String): String = get(key)?.replayString()?.takeIf { it.isNotBlank() } ?: invalidReplay()

private fun JsonObject.optionalText(key: String): String? =
    when (val value = get(key)) {
        null, JsonNull -> null
        else -> value.replayString()
    }

private fun JsonObject.optionalObject(key: String): JsonObject? =
    if (key in this) {
        get(key) as? JsonObject ?: invalidReplay()
    } else {
        null
    }

private fun JsonObject.ids(key: String): List<String> {
    val values = get(key) as? JsonArray ?: invalidReplay()
    if (values.size !in 1..500) invalidReplay()
    return values.map { it.replayString().takeIf(String::isNotBlank) ?: invalidReplay() }
}

private fun nextProjectionVersion(version: Long): Long = if (version in 1 until MaxProjectionSequence) version + 1 else invalidReplay()

/** A complete bounded catch-up window, assembled without exposing page fragments. */
internal fun reduceCommittedCatchUpPage(
    current: WorkspaceStateDto,
    page: FullCatchUpOperationsDto,
): WorkspaceStateDto {
    if (page.hasMore || page.lastServerSeq !in current.throughServerSeq..MaxProjectionSequence ||
        page.operations.size > MaxCatchUpReplayRecords ||
        // Backend uniqueness is workspace + actor + operation, not workspace + operation.
        page.operations
            .map { it.actorId to it.operationId }
            .toSet()
            .size != page.operations.size
    ) {
        invalidReplay()
    }
    if (page.operations.isEmpty()) {
        if (page.lastServerSeq != current.throughServerSeq) invalidReplay()
        return current
    }
    if (page.operations.last().serverSeq != page.lastServerSeq) invalidReplay()
    var result = current
    var offset = 0
    while (offset < page.operations.size) {
        val first = page.operations[offset]
        var end = offset + 1
        while (end < page.operations.size && page.operations[end].transactionId == first.transactionId &&
            page.operations[end].workspaceVersion == first.workspaceVersion
        ) {
            end++
        }
        val records = page.operations.subList(offset, end)
        result =
            reduceCommittedProjection(
                result,
                current.workspaceId,
                records,
                first.serverSeq,
                records.last().serverSeq,
                first.workspaceVersion,
            )
        offset = end
    }
    return result
}

/** Replay a complete, already scoped transaction onto an immutable authoritative snapshot.
 * All mutation is private until final domain validation succeeds. Unknown operations, gaps or
 * invalid group/relation projections require authoritative resync, never partial publication.
 * Workspace identity comes from the authenticated envelope; records alone do not prove scope.
 */
internal fun reduceCommittedProjection(
    current: WorkspaceStateDto,
    workspaceId: String,
    records: List<CommittedWorkspaceOperationDto>,
    fromServerSeq: Long,
    toServerSeq: Long,
    workspaceVersion: Long,
): WorkspaceStateDto {
    if (current.workspaceId != workspaceId || current.workspaceVersion !in 0 until MaxProjectionSequence ||
        current.throughServerSeq !in 0 until MaxProjectionSequence ||
        workspaceVersion != current.workspaceVersion + 1 || fromServerSeq != current.throughServerSeq + 1 ||
        !isValidCommittedTransaction(records, fromServerSeq, toServerSeq, workspaceVersion) ||
        records.first().baseVersion > current.workspaceVersion
    ) {
        invalidReplay()
    }
    current.toDomainWorkspace("Replay validation")
    val objects = current.objects.associateBy { it.objectId }.toMutableMap()
    val relations = current.relations.associateBy { it.relationId }.toMutableMap()

    fun updateObject(p: JsonObject) {
        p.keysOnly("objectId", "parentId", "zIndex", "locked", "transform", "properties")
        val id = p.id("objectId")
        val old = objects[id] ?: invalidReplay()
        val zIndex =
            if ("zIndex" in p) {
                (p["zIndex"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.longOrNull ?: invalidReplay()
            } else {
                old.zIndex
            }
        val locked =
            if ("locked" in p) {
                (p["locked"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.booleanOrNull ?: invalidReplay()
            } else {
                old.locked
            }
        objects[id] =
            old.copy(
                objectVersion = nextProjectionVersion(old.objectVersion),
                parentId = if ("parentId" in p) p.optionalText("parentId") else old.parentId,
                zIndex = zIndex,
                locked = locked,
                transform = p.optionalObject("transform") ?: old.transform,
                properties = JsonObject(old.properties + p.optionalObject("properties").orEmpty()),
            )
    }
    for (record in records) {
        val p = record.payload
        when (record.operationType) {
            "create_object" -> {
                p.keysOnly("objectId", "objectType", "parentId", "zIndex", "locked", "transform", "properties")
                val id = p.id("objectId")
                if (id in objects) invalidReplay()
                // Reuse the strict DTO parser, supplying only server create defaults/version.
                val fields =
                    buildJsonObject {
                        p.forEach { (key, value) -> put(key, value) }
                        put("objectVersion", 1)
                        if ("zIndex" !in p) put("zIndex", 0)
                        if ("locked" !in p) put("locked", false)
                        if ("transform" !in p) put("transform", JsonObject(emptyMap()))
                        if ("properties" !in p) put("properties", JsonObject(emptyMap()))
                    }
                objects[id] =
                    try {
                        Json.decodeFromJsonElement<CanvasObjectDto>(fields)
                    } catch (_: Exception) {
                        invalidReplay()
                    }
            }

            "update_object" -> {
                updateObject(p)
            }

            "move_objects" -> {
                p.keysOnly("moves")
                val moves = p["moves"] as? JsonArray ?: invalidReplay()
                if (moves.size !in 1..500) invalidReplay()
                moves.forEach { value ->
                    val move = value as? JsonObject ?: invalidReplay()
                    move.keysOnly("objectId", "transform")
                    move.optionalObject("transform") ?: invalidReplay()
                    updateObject(move)
                }
            }

            "delete_objects" -> {
                p.keysOnly("objectIds", "cascadedRelationIds")
                val ids = p.ids("objectIds").toSet()
                if (ids.any { it !in objects }) invalidReplay()
                val cascades = p["cascadedRelationIds"] as? JsonArray ?: invalidReplay()
                val cascadeIds = cascades.map { it.replayString() }.toSet()
                val touching =
                    relations.values
                        .filter { it.sourceObjectId in ids || it.targetObjectId in ids }
                        .map { it.relationId }
                        .toSet()
                if (cascadeIds != touching || cascades.size != cascadeIds.size) invalidReplay()
                ids.forEach(objects::remove)
                cascadeIds.forEach(relations::remove)
            }

            "create_relation" -> {
                p.keysOnly("relationId", "sourceObjectId", "targetObjectId", "direction", "intent", "label", "style")
                val id = p.id("relationId")
                if (id in relations) invalidReplay()
                relations[id] =
                    RelationDto(
                        id,
                        1,
                        p.id("sourceObjectId"),
                        p.id("targetObjectId"),
                        p.id("direction"),
                        p.optionalText("intent"),
                        p.optionalText("label"),
                        p.optionalObject("style") ?: JsonObject(emptyMap()),
                    )
            }

            "update_relation" -> {
                p.keysOnly("relationId", "direction", "intent", "label", "style")
                val id = p.id("relationId")
                val old = relations[id] ?: invalidReplay()
                relations[id] =
                    old.copy(
                        relationVersion = nextProjectionVersion(old.relationVersion),
                        direction = if ("direction" in p) p.id("direction") else old.direction,
                        intent = if ("intent" in p) p.optionalText("intent") else old.intent,
                        label = if ("label" in p) p.optionalText("label") else old.label,
                        style = JsonObject(old.style + p.optionalObject("style").orEmpty()),
                    )
            }

            "delete_relations" -> {
                p.keysOnly("relationIds")
                val ids = p.ids("relationIds")
                if (ids.any { it !in relations }) invalidReplay()
                ids.forEach(relations::remove)
            }

            else -> {
                invalidReplay()
            }
        }
    }
    val result =
        current.copy(
            workspaceVersion = workspaceVersion,
            throughServerSeq = toServerSeq,
            objects = objects.values.toList(),
            relations = relations.values.toList(),
        )
    result.toDomainWorkspace("Replay validation")
    return result
}
