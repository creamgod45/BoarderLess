package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.withVersion
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
internal data class CommittedDeletionEvidence(
    val scope: PendingSubmissionScope,
    val entity: String,
    val tombstoneVersion: Long,
    val transactionId: String,
    val throughServerSeq: Long,
    val snapshotDigest: String,
)

/** One scoped, bounded record per entity. Written after validating the exact acknowledgement and
 * before clearing pending wire. Retry of that same wire may republish evidence idempotently.
 * Settings does not guarantee atomic multi-key publication or power-loss fsync; pending remains
 * the recovery boundary if publication fails. No automatic restore on startup.
 */
internal class CommittedDeletionStore(
    private val settings: Settings,
    private val native: RecoverySafetyPersistence? = null,
) {
    private val json = Json { encodeDefaults = true }

    /** Local provenance is retained, not upgraded to authority by a migration capture. */
    fun inventoryForMigration(): List<CommittedDeletionEvidence> =
        captureLegacyRecoveryRecords(settings, "wdel1.", 4096) { storageKey, raw ->
            json.decodeFromString<CommittedDeletionEvidence>(raw).also {
                WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
                check(
                    (it.entity.startsWith("object:") || it.entity.startsWith("relation:")) &&
                        it.entity.substringAfter(':').isNotBlank(),
                )
                check(it.tombstoneVersion in 2..9_007_199_254_740_990L && it.transactionId.isNotBlank())
                check(it.throughServerSeq in 1..9_007_199_254_740_991L)
                check(it.snapshotDigest.matches(Regex("[0-9a-f]{64}")))
                check(storageKey == key(it.scope, it.entity))
            }
        }

    fun requireRestoration(
        scope: PendingSubmissionScope,
        operations: List<OperationDto>,
        local: WorkspaceOperation,
    ) {
        val snapshots = local.restorationSnapshotDigests()
        for (operation in operations.filter { it.kind.startsWith("restore_") }) {
            validateRestorationWire(operation)
            val type = if (operation.kind == "restore_objects") "object" else "relation"
            val versions = checkNotNull(operation.expectedObjectVersions)
            for ((id, version) in versions) {
                val entity = "$type:$id"
                val evidence =
                    if (native != null) {
                        checkNotNull(native.deletion(scope, entity)) {
                            "Restoration requires confirmed deletion evidence"
                        }
                    } else {
                        val raw =
                            checkNotNull(settings.getStringOrNull(key(scope, entity))) {
                                "Restoration requires confirmed deletion evidence"
                            }
                        check(raw.length <= 4096)
                        json.decodeFromString<CommittedDeletionEvidence>(raw)
                    }
                check(
                    evidence.scope == scope && evidence.entity == entity &&
                        evidence.tombstoneVersion == version && evidence.transactionId.isNotBlank() &&
                        evidence.throughServerSeq > 0 && evidence.snapshotDigest == snapshots[entity],
                ) {
                    "Restoration evidence does not match history"
                }
            }
        }
    }

    fun record(
        entry: PendingWorkspaceSubmission,
        accepted: AcceptedOperationsDto,
    ) {
        val confirmed = linkedMapOf<String, Long>()
        val cascadeIds = mutableSetOf<String>()
        for ((index, record) in accepted.operations.withIndex()) {
            val sent = entry.request.operations[index]
            when (record.operationType) {
                "delete_objects", "delete_relations" -> {
                    if (entry.deletionVersions.isEmpty()) continue
                    val objects = record.operationType == "delete_objects"
                    val idsKey = if (objects) "objectIds" else "relationIds"
                    val ids = record.payload.ids(idsKey)
                    check(ids == sent.payload.ids(idsKey)) { "Deletion acknowledgement changed IDs" }
                    val expected = checkNotNull(sent.expectedObjectVersions)
                    check(expected.keys == ids.toSet())
                    for (id in ids) {
                        val entity = "${if (objects) "object" else "relation"}:$id"
                        val prior = expected.getValue(id)
                        check(prior in 1..9_007_199_254_740_989L)
                        check(confirmed.put(entity, prior + 1) == null)
                    }
                    if (objects) {
                        for (id in record.payload.ids("cascadedRelationIds")) {
                            check(cascadeIds.add(id))
                            val entity = "relation:$id"
                            check(confirmed.put(entity, checkNotNull(entry.deletionVersions[entity])) == null)
                        }
                    }
                }

                "restore_objects", "restore_relations" -> {
                    val idsKey = if (record.operationType == "restore_objects") "objectIds" else "relationIds"
                    check(record.payload.ids(idsKey) == sent.payload.ids(idsKey))
                    val expected = checkNotNull(sent.expectedObjectVersions)
                    val restored = record.payload.getValue("restoredVersions").jsonObject
                    check(restored.keys == expected.keys)
                    check(expected.all { (id, version) -> restored.getValue(id).jsonPrimitive.long == version + 1 })
                }
            }
        }
        // Legacy pending records have no cascade proof; they never create restoration authority.
        if (entry.deletionVersions.isEmpty()) return
        check(confirmed == entry.deletionVersions) { "Committed deletion does not match saved history" }
        val records =
            confirmed.map { (entity, version) ->
                CommittedDeletionEvidence(
                    entry.scope,
                    entity,
                    version,
                    entry.request.transactionId,
                    accepted.toServerSeq,
                    entry.deletionSnapshotDigests.getValue(entity),
                )
            }
        native?.let {
            it.recordDeletions(entry.scope, records)
            return
        }
        for (record in records) {
            val value = json.encodeToString(record)
            check(value.length <= 4096)
            settings.putString(key(entry.scope, record.entity), value)
            check(settings.getStringOrNull(key(entry.scope, record.entity)) == value)
        }
    }

    private fun key(
        scope: PendingSubmissionScope,
        entity: String,
    ): String {
        val bytes = json.encodeToString(scope).plus("|$entity").encodeToByteArray()
        return "wdel1." +
            SHA256().digest(bytes).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
    }
}

private fun JsonObject.ids(key: String): List<String> =
    getValue(key)
        .jsonArray
        .map {
            it.jsonPrimitive.also { value -> check(value.isString) }.content
        }.also { check(it.distinct().size == it.size) }

internal fun validateRestorationWire(operation: OperationDto) {
    require(operation.kind in setOf("restore_objects", "restore_relations"))
    val key = if (operation.kind == "restore_objects") "objectIds" else "relationIds"
    require(operation.payload.keys == setOf(key))
    val ids = operation.payload.ids(key)
    require(ids.size in 1..500)
    val versions = requireNotNull(operation.expectedObjectVersions)
    require(versions.keys == ids.toSet() && versions.values.all { it in 2..9_007_199_254_740_990L })
}

internal fun WorkspaceOperation.deletionVersions(): Map<String, Long> =
    buildMap {
        fun collect(operation: WorkspaceOperation) {
            fun add(
                entity: String,
                version: Long,
            ) {
                require(version in 1..9_007_199_254_740_989L)
                check(put(entity, version + 1) == null) { "Repeated deletion in one transaction" }
            }
            when (operation) {
                is TransactionOperation -> {
                    operation.operations.forEach(::collect)
                }

                is DeleteObjectsOperation -> {
                    operation.objects.forEach { add("object:${it.id.value}", it.version) }
                    operation.relations.forEach { add("relation:${it.id.value}", it.version) }
                }

                is DeleteRelationsOperation -> {
                    operation.relations.forEach { add("relation:${it.id.value}", it.version) }
                }

                else -> {
                    Unit
                }
            }
        }
        collect(this@deletionVersions)
    }

internal fun WorkspaceOperation.deletionSnapshotDigests(): Map<String, String> = snapshotDigests(deleted = true)

private fun WorkspaceOperation.restorationSnapshotDigests(): Map<String, String> = snapshotDigests(deleted = false)

private fun WorkspaceOperation.snapshotDigests(deleted: Boolean): Map<String, String> =
    buildMap {
        fun digest(raw: String) =
            SHA256().digest(raw.encodeToByteArray()).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }

        fun visit(operation: WorkspaceOperation) {
            when (operation) {
                is TransactionOperation -> {
                    operation.operations.forEach(::visit)
                }

                is DeleteObjectsOperation -> {
                    if (deleted) {
                        operation.objects.forEach {
                            put(
                                "object:${it.id.value}",
                                digest(
                                    Json.encodeToString<CanvasObject>(
                                        it.withVersion(it.version + 2),
                                    ),
                                ),
                            )
                        }
                        operation.relations.forEach {
                            put(
                                "relation:${it.id.value}",
                                digest(Json.encodeToString(it.copy(version = it.version + 2))),
                            )
                        }
                    }
                }

                is DeleteRelationsOperation -> {
                    if (deleted) {
                        operation.relations.forEach {
                            put("relation:${it.id.value}", digest(Json.encodeToString(it.copy(version = it.version + 2))))
                        }
                    }
                }

                is CreateObjectsOperation -> {
                    if (!deleted && operation.restoreVersions.isNotEmpty()) {
                        operation.objects.forEach {
                            put("object:${it.id.value}", digest(Json.encodeToString<CanvasObject>(it)))
                        }
                    }
                }

                is CreateRelationsOperation -> {
                    if (!deleted && operation.restoreVersions.isNotEmpty()) {
                        operation.relations.forEach {
                            put("relation:${it.id.value}", digest(Json.encodeToString(it)))
                        }
                    }
                }

                else -> {
                    Unit
                }
            }
        }
        visit(this@snapshotDigests)
    }
