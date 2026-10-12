package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.remote.BackendQuickSchemeGateway
import cg.creamgod.boarderless.data.remote.CloudSchemeHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One explicitly approved attempt. Durable Sending precedes any mutation HTTP. Result
 * persistence failure is not retried; Sending itself preserves uncertainty on reopen. */
internal suspend fun sendCloudSchemeTask(outbox: CloudSchemeOutbox, gateway: BackendQuickSchemeGateway,
    reviewed: CloudSchemeTask, canContinue: () -> Boolean): CloudSchemeTask {
    val context = currentCoroutineContext()
    fun active() { context.ensureActive(); check(canContinue()) }
    active()
    require(gateway.serverScope == outbox.scope.serverScope && gateway.actorId == outbox.scope.userId)
    val recovering = reviewed.phase in setOf(CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown)
    val observation = gateway.observeCapabilities()
    active()
    val ttl = observation.capabilities.idempotencyKeyTtlSeconds
    val sending = if (recovering) outbox.beginRecovery(reviewed, observation.requireFresh(), ttl)
        else outbox.beginAttempt(reviewed, observation.serverMillis?.let {
            CloudSchemeRetryWindow(observation.requireFresh(), ttl)
        })
    try {
        active()
        val result = gateway.execute(sending.mutation, canContinue, observation) {
            active()
            if (recovering) requireNotNull(sending.retryWindow).requireOpen(observation.requireFresh(), ttl)
            else if (sending.retryWindow != null) observation.requireFresh()
        }
        active()
        return outbox.recordAccepted(sending, result.httpStatus, result.resource)
    } catch (cancelled: CancellationException) {
        // Do not force an uncancellable write or infer server rollback. Durable Sending
        // remains uncertain and is not automatically executable after reopen.
        throw cancelled
    } catch (failure: CloudSchemeHttpException) {
        active()
        // A denied replay cannot prove that the original request never committed.
        if (!recovering && failure.httpStatus in 400..499) return outbox.recordRejected(sending, failure.httpStatus)
        outbox.recordUnknown(sending)
        throw failure
    } catch (failure: Exception) {
        active()
        // ACK may already have been saved. Never overwrite a committed record with Unknown.
        if (outbox.tasks().single { it.mutation.idempotencyKey == sending.mutation.idempotencyKey } == sending)
            outbox.recordUnknown(sending)
        throw failure
    }
}
