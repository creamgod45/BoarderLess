package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.json.*

internal sealed interface RealtimeSubmissionResult {
    data class Accepted(val acknowledgement: AcceptedOperationsDto) : RealtimeSubmissionResult
    data class Conflict(val workspaceVersion: Long, val lastServerSeq: Long) : RealtimeSubmissionResult
    data class Rejected(val code: String) : RealtimeSubmissionResult
}
internal class RealtimeSubmissionUnconfirmed : IllegalStateException("Realtime submission is unconfirmed; inspect original receipt")
internal class RealtimeSubmissionRejected(val code: String) : IllegalStateException("Realtime submission rejected")
internal val RealtimeSubmissionCodes = setOf("idempotency_mismatch", "transaction_fenced", "partial_duplicate", "asset_not_ready", "forbidden",
    "rate_limited", "unsupported_object_type", "unsupported_change", "unsupported_protocol_version", "internal_error", "server_rejected")

/** ACK is bound to the frozen durable request, not just a transaction UUID. It never
 * applies content or saves a checkpoint; fan-out and original-write settlement differ. */
internal fun decodeRealtimeAcknowledgement(frame: JsonObject, request: SubmitOperationsRequest, actor: String): RealtimeSubmissionResult {
    fun text(key: String) = requireNotNull((frame[key] as? JsonPrimitive)?.takeIf { it.isString }?.content)
    fun sequence(key: String) = requireNotNull((frame[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
        .also { require(it in 0..RealtimeMaxSequence) }
    require(text("type") == "operation_ack" && text("transactionId") == request.transactionId)
    val ids = frame["operationIds"] as? JsonArray ?: error("Missing ACK operation identities")
    require(ids.map { requireNotNull((it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content) } == request.operations.map { it.operationId })
    val envelope = setOf("type", "protocolVersion", "workspaceId", "connectionEpoch", "transactionId", "operationIds", "status")
    return when (text("status")) {
        "accepted", "duplicate" -> {
            require(frame.keys == envelope + setOf("fromServerSeq", "toServerSeq", "workspaceVersion", "operations"))
            sequence("fromServerSeq"); sequence("toServerSeq"); sequence("workspaceVersion")
            val operations = frame["operations"] as? JsonArray ?: error("Missing ACK records")
            require(operations.size == request.operations.size && operations.size in 1..200)
            operations.forEach { element ->
                val record = element as? JsonObject ?: error("Invalid ACK record")
                listOf("serverSeq", "clientSeq", "baseVersion", "workspaceVersion", "schemaVersion").forEach { key ->
                    require((record[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.let { it in 0..RealtimeMaxSequence } == true)
                }
            }
            val body = JsonObject(frame.filterKeys { it in setOf("status", "fromServerSeq", "toServerSeq", "workspaceVersion", "operations") })
            val ack = Json.decodeFromJsonElement(AcceptedOperationsDto.serializer(), body)
            ack.validateAcknowledgement(request, actor)
            ack.operations.zip(request.operations).forEach { (record, submitted) ->
                require(record.payload.preservesSubmittedValues(submitted.payload)) { "ACK original payload mismatch" }
            }
            RealtimeSubmissionResult.Accepted(ack)
        }
        "conflict" -> {
            val required = envelope + setOf("conflicts", "workspaceVersion", "lastServerSeq", "missingOperations")
            require(frame.keys.containsAll(required) && frame.keys.all { it in required + setOf("code", "canvasStyle", "sequenceDiagrams") })
            require(frame["conflicts"] is JsonArray && frame["missingOperations"] is JsonArray)
            RealtimeSubmissionResult.Conflict(sequence("workspaceVersion"), sequence("lastServerSeq"))
        }
        "rejected" -> {
            require(frame.keys.containsAll(envelope + "code") && frame.keys.all { it in envelope + setOf("code", "retryAfterMs", "details") })
            if ("retryAfterMs" in frame) sequence("retryAfterMs")
            RealtimeSubmissionResult.Rejected(text("code").takeIf { it in RealtimeSubmissionCodes } ?: "server_rejected")
        }
        else -> error("Unsupported ACK status")
    }
}
