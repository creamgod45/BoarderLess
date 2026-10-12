package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.remote.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Readback is a new observation, never proof that the original idempotency key committed.
 * Missing/opaque 404 is not a tombstone or terminal fence. */
internal data class CloudSchemeConflictReview(val task: CloudSchemeTask, val current: CloudSchemeResource?) {
    fun renameOnCurrent(idempotencyKey: String): CloudSchemeMutation {
        require(task.phase == CloudSchemeTaskPhase.Rejected && task.mutation.kind == "update")
        val resource = requireNotNull(current)
        val intendedName = requireNotNull(task.mutation.body).name
        // User explicitly chooses a name-only action. Keep ALL current remote content,
        // rather than combining a fresh ETag with the old frozen payload.
        return CloudSchemeMutation(resource.id, idempotencyKey, "update", resource.etag,
            CloudSchemeBody(name = intendedName, schemaVersion = resource.schemaVersion,
                payload = resource.payload, assetBindings = resource.assetBindings))
    }
    fun deleteCurrent(idempotencyKey: String): CloudSchemeMutation {
        require(task.phase == CloudSchemeTaskPhase.Rejected && task.mutation.kind == "delete")
        val resource = requireNotNull(current)
        return CloudSchemeMutation(resource.id, idempotencyKey, "delete", resource.etag)
    }
    /** Explicit full replacement, not an automatic merge or replay of an unresolved key. */
    fun replaceOnCurrent(idempotencyKey: String): CloudSchemeMutation {
        require(task.phase == CloudSchemeTaskPhase.Rejected && task.mutation.kind == "update")
        val resource = requireNotNull(current)
        require(idempotencyKey != task.mutation.idempotencyKey)
        return CloudSchemeMutation(resource.id, idempotencyKey, "update", resource.etag,
            requireNotNull(task.mutation.body))
    }
    fun saveAsNew(resourceId: String, idempotencyKey: String): CloudSchemeMutation {
        require(task.phase == CloudSchemeTaskPhase.Rejected && task.mutation.kind != "delete")
        require(resourceId != task.mutation.resourceId)
        return CloudSchemeMutation(resourceId, idempotencyKey, "create", body = requireNotNull(task.mutation.body))
    }
}

internal suspend fun reviewCloudSchemeConflict(outbox: CloudSchemeOutbox, gateway: BackendQuickSchemeGateway,
    task: CloudSchemeTask, canContinue: () -> Boolean): CloudSchemeConflictReview {
    val context = currentCoroutineContext()
    fun active() { context.ensureActive(); check(canContinue()) }
    active()
    require(gateway.serverScope == outbox.scope.serverScope && gateway.actorId == outbox.scope.userId)
    require(task.phase in setOf(CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown, CloudSchemeTaskPhase.Rejected))
    require(outbox.tasks().single { it.mutation.idempotencyKey == task.mutation.idempotencyKey } == task)
    val current = try { gateway.get(task.mutation.resourceId) }
        catch (error: CloudSchemeHttpException) { if (error.httpStatus == 404) null else throw error }
    active()
    require(outbox.tasks().single { it.mutation.idempotencyKey == task.mutation.idempotencyKey } == task)
    return CloudSchemeConflictReview(task, current)
}
