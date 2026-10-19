package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.json.*

internal enum class RealtimeWritePrerequisite { DurableCheckpoint, SequenceLedger, AtomicRecovery }

internal val RealtimeProtocolCodes = setOf("authentication_required", "ticket_expired", "ticket_used", "scope_mismatch",
    "unsupported_protocol_version", "unsupported_schema_version", "access_revoked", "invalid_message", "message_too_large",
    "rate_limited", "sequence_gap", "resync_required", "not_live", "heartbeat_timeout")

/** Allowlisted metadata only: no exception text, endpoint, identifiers, frame, ticket or payload. */
internal data class BackendRealtimeDiagnostic(val stage: BackendRealtimeStatus, val code: String,
    val httpStatus: Int? = null, val retryAfterMs: Long? = null) {
    init {
        require(code in RealtimeProtocolCodes || code in RealtimeSubmissionCodes ||
            code in setOf("http_rejected", "transport_or_validation_failed", "presence_publication_stopped", "submission_conflict", "submission_unconfirmed"))
        require(httpStatus == null || httpStatus in 100..599)
        require(retryAfterMs == null || retryAfterMs in 0..60_000)
    }
}
internal class RealtimeProtocolFailure(val code: String, val retryAfterMs: Long?) : IllegalStateException("Realtime protocol rejected")
internal fun readRealtimeProtocolFailure(frame: JsonObject): RealtimeProtocolFailure {
    val required = setOf("type", "protocolVersion", "workspaceId", "connectionEpoch", "code")
    require(frame.keys == required || frame.keys == required + "retryAfterMs")
    require(frame["type"] == JsonPrimitive("protocol_error") && frame["protocolVersion"] == JsonPrimitive(1))
    val code = requireNotNull((frame["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
    require(code in RealtimeProtocolCodes)
    val retry = if ("retryAfterMs" in frame) requireNotNull((frame["retryAfterMs"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
        .also { require(it in 0..60_000) } else null
    return RealtimeProtocolFailure(code, retry)
}
