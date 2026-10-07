package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*

internal enum class DraftMergeChoice { KeepRemote, UseDraft }
internal data class DraftMergeFieldId(val relation: Boolean, val entityId: String, val path: List<String>)
internal data class DraftMergeField(
    val id: DraftMergeFieldId,
    val baseline: JsonElement?,
    val draft: JsonElement?,
    val remote: JsonElement?,
) {
    val conflicts: Boolean get() = remote != baseline && remote != draft
}

/** Explicit three-way selection only. Target is a proposal, never an authorized live workspace.
 * A later caller must refresh ACL/state, reconcile unconfirmed submissions, validate media,
 * create NEW operations and obtain confirmation before applying anything.
 */
internal class WorkspaceDraftMergePlan(val review: WorkspaceDraftReview) {
    private val json = Json { encodeDefaults = true }
    val fields: List<DraftMergeField> = buildList {
        (review.baseline.objects.keys + review.proposed.objects.keys).sortedBy { it.value }.forEach { id ->
            addFields(false, id.value, review.baseline.objects[id]?.let(::encode),
                review.proposed.objects[id]?.let(::encode), review.current.objects[id]?.let(::encode))
        }
        (review.baseline.relations.keys + review.proposed.relations.keys).sortedBy { it.value }.forEach { id ->
            addFields(true, id.value, review.baseline.relations[id]?.let(::encode),
                review.proposed.relations[id]?.let(::encode), review.current.relations[id]?.let(::encode))
        }
    }

    /** Every row requires a decision, including nonconflicting rows; unknown/stale selections fail. */
    fun resolve(choices: Map<DraftMergeFieldId, DraftMergeChoice>): Workspace {
        require(choices.keys == fields.map { it.id }.toSet())
        val objects = review.current.objects.toMutableMap()
        val relations = review.current.relations.toMutableMap()
        fields.groupBy { it.id.relation to it.id.entityId }.forEach { (entity, rows) ->
            val (relation, rawId) = entity
            var document = if (relation) relations[RelationId(rawId)]?.let(::encode)
                else objects[CanvasObjectId(rawId)]?.let(::encode)
            val version = document?.getValue("version") ?: JsonPrimitive(1L)
            rows.filter { choices.getValue(it.id) == DraftMergeChoice.UseDraft }.forEach { row ->
                document = if (row.id.path.isEmpty()) row.draft as JsonObject?
                    else patch(checkNotNull(document), row.id.path, checkNotNull(row.draft))
            }
            if (document != null) document = JsonObject(checkNotNull(document).toMutableMap().apply { put("version", version) })
            if (relation) {
                val id = RelationId(rawId)
                if (document == null) relations.remove(id)
                else relations[id] = json.decodeFromJsonElement(Relation.serializer(), checkNotNull(document))
            } else {
                val id = CanvasObjectId(rawId)
                if (document == null) objects.remove(id)
                else objects[id] = json.decodeFromJsonElement(CanvasObject.serializer(), checkNotNull(document))
            }
        }
        val target = review.current.copy(objects = objects, relations = relations)
        validateTarget(target)
        review.current.objects.values.filter { it.locked }.forEach { before ->
            val after = target.objects[before.id]
            val explicitUnlock = fields.any { it.id == DraftMergeFieldId(false, before.id.value, listOf("locked")) &&
                it.draft == JsonPrimitive(false) && choices[it.id] == DraftMergeChoice.UseDraft }
            // Only an explicitly selected unlock can authorize a later edit in the proposal.
            // The operation builder must emit unlock BEFORE other modifications; never delete locked data.
            require(after == before || (after != null && !after.locked && explicitUnlock))
        }
        return target
    }

    private fun MutableList<DraftMergeField>.addFields(relation: Boolean, id: String,
        before: JsonObject?, draft: JsonObject?, remote: JsonObject?) {
        if (withoutVersion(before) == withoutVersion(draft)) return
        if (before == null || draft == null || remote == null ||
            before["type"] != draft["type"] || draft["type"] != remote["type"]) {
            add(DraftMergeField(DraftMergeFieldId(relation, id, emptyList()),
                withoutVersion(before), withoutVersion(draft), withoutVersion(remote)))
            return
        }
        fun walk(path: List<String>, a: JsonElement?, b: JsonElement?, c: JsonElement?) {
            if (a == b) return
            if (a is JsonObject && b is JsonObject && c is JsonObject) {
                (a.keys + b.keys).sorted().forEach { key ->
                    if (path.isNotEmpty() || key !in setOf("id", "version", "type"))
                        walk(path + key, a[key], b[key], c[key])
                }
            } else add(DraftMergeField(DraftMergeFieldId(relation, id, path), a, b, c))
        }
        walk(emptyList(), before, draft, remote)
    }

    private fun encode(obj: CanvasObject) = json.encodeToJsonElement(CanvasObject.serializer(), obj).jsonObject
    private fun encode(relation: Relation) = json.encodeToJsonElement(Relation.serializer(), relation).jsonObject
    private fun withoutVersion(value: JsonObject?) = value?.let { JsonObject(it.filterKeys { key -> key != "version" }) }
    private fun patch(document: JsonObject, path: List<String>, value: JsonElement): JsonObject {
        val key = path.first()
        return JsonObject(document.toMutableMap().apply {
            put(key, if (path.size == 1) value else patch(document.getValue(key).jsonObject, path.drop(1), value))
        })
    }
    private fun validateTarget(target: Workspace) {
        require(listOf(review.baseline.id, review.proposed.id, review.current.id).all { it == target.id })
        require(target.objects.size <= 10_000 && target.relations.size <= 10_000)
        target.objects.forEach { (id, obj) ->
            require(id == obj.id)
            val visited = mutableSetOf(id)
            var parent = obj.parentId
            while (parent != null) {
                require(visited.size <= 64 && visited.add(parent))
                parent = (target.objects[parent] as? GroupFrame ?: error("Merge needs a valid group")).parentId
            }
        }
        target.relations.forEach { (id, edge) ->
            require(id == edge.id && edge.sourceObjectId != edge.targetObjectId)
            require(edge.sourceObjectId in target.objects && edge.targetObjectId in target.objects)
        }
    }
}
