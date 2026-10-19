package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.PendingSubmissionReceipt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Server create/move logs may fill defaults. Supplied values must survive unchanged;
 * omitted object fields may be added, arrays preserve cardinality/order, and JSON numeric
 * 1 and 1.0 are equivalent across JS/Kotlin serialization, never equivalent to string "1".
 */
internal fun JsonElement.preservesSubmittedValues(
    sent: JsonElement,
    depth: Int = 0,
): Boolean =
    depth <= 64 &&
        when (sent) {
            is JsonObject -> {
                this is JsonObject && sent.all { (key, value) -> this[key]?.preservesSubmittedValues(value, depth + 1) == true }
            }

            is JsonArray -> {
                this is JsonArray && size == sent.size &&
                    zip(sent).all { (actual, wanted) -> actual.preservesSubmittedValues(wanted, depth + 1) }
            }

            is JsonPrimitive -> {
                if (this is JsonPrimitive && !isString && !sent.isString &&
                    doubleOrNull?.isFinite() == true && sent.doubleOrNull?.isFinite() == true
                ) {
                    doubleOrNull == sent.doubleOrNull
                } else {
                    this == sent
                }
            }
        }

@Serializable internal data class ReceiptQueryDto(
    val transactionIds: List<String>,
)

@Serializable internal data class SubmissionReceiptResponseDto(
    val workspaceId: String,
    val headServerSeq: Long,
    val headWorkspaceVersion: Long,
    val receipts: List<TransactionReceiptDto>,
    val lookups: List<ReceiptLookupDto>,
)

@Serializable internal data class TransactionReceiptDto(
    val transactionId: String,
    val actorId: String,
    val clientId: String,
    val workspaceVersion: Long,
    val fromServerSeq: Long,
    val toServerSeq: Long,
    val committedAt: String,
    val operations: List<ReceiptOperationDto>,
)

@Serializable internal data class ReceiptOperationDto(
    val operationId: String,
    val clientSeq: Long,
    val serverSeq: Long,
    val kind: String,
)

@Serializable internal data class ReceiptLookupDto(
    val type: String,
    val id: String,
    val status: String,
    val transactionId: String? = null,
    val workspaceVersion: Long? = null,
)

/** Complete committed evidence; summary alone must never enter acknowledgement persistence. */
internal fun validateOriginalCommittedLog(
    receipt: TransactionReceiptDto,
    request: SubmitOperationsRequest,
    actor: String,
    page: FullCatchUpOperationsDto,
): AcceptedOperationsDto {
    if (page.lastServerSeq !in receipt.toServerSeq..9_007_199_254_740_991L || page.operations.size > 200) {
        throw BackendContractException("Original transaction checkpoint mismatch")
    }
    val records = page.operations.filter { it.serverSeq in receipt.fromServerSeq..receipt.toServerSeq }
    val ack = AcceptedOperationsDto("duplicate", receipt.workspaceVersion, receipt.fromServerSeq, receipt.toServerSeq, records)
    ack.validateAcknowledgement(request, actor)
    records.zip(request.operations).forEach { (record, sent) ->
        if (record.committedAt != receipt.committedAt || !record.payload.preservesSubmittedValues(sent.payload)) {
            throw BackendContractException("Original submission payload mismatch")
        }
    }
    return ack
}

/** Match the whole receipt to the exact saved wire, not just one ID or a similar canvas state.
 * Receipts have no payload/baseVersion: even a match must not become a full REST ack.
 */
internal fun SubmissionReceiptResponseDto.validatePendingReceipt(
    workspace: String,
    actor: String,
    request: SubmitOperationsRequest,
): PendingSubmissionReceipt {
    val max = 9_007_199_254_740_991L

    fun invalid(): Nothing = throw BackendContractException("Invalid original submission receipt")
    if (workspaceId != workspace || headServerSeq !in 0..max || headWorkspaceVersion !in 0..max ||
        request.operations.size !in 1..200 || request.operations
            .map { it.operationId }
            .toSet()
            .size != request.operations.size ||
        lookups.size != 1
    ) {
        invalid()
    }
    val lookup = lookups.single()
    if (lookup.type != "transaction" || lookup.id != request.transactionId) invalid()
    val status =
        when (lookup.status) {
            "committed" -> PendingReceiptStatus.Committed
            "fenced" -> PendingReceiptStatus.Fenced
            "unknown" -> PendingReceiptStatus.Unknown
            else -> invalid()
        }
    if (status != PendingReceiptStatus.Committed) {
        if (receipts.isNotEmpty() || lookup.transactionId != null || lookup.workspaceVersion != null) invalid()
        return PendingSubmissionReceipt(status, headServerSeq, headWorkspaceVersion)
    }
    if (receipts.size != 1) invalid()
    val receipt = receipts.single()
    if (receipt.transactionId != request.transactionId || receipt.actorId != actor || receipt.clientId != request.clientId ||
        receipt.workspaceVersion !in 1..headWorkspaceVersion || receipt.workspaceVersion <= request.baseVersion ||
        lookup.transactionId != receipt.transactionId || lookup.workspaceVersion != receipt.workspaceVersion ||
        receipt.fromServerSeq !in 1..max || receipt.toServerSeq !in receipt.fromServerSeq..headServerSeq ||
        receipt.toServerSeq - receipt.fromServerSeq + 1 != request.operations.size.toLong() ||
        receipt.operations.size != request.operations.size || receipt.committedAt.isBlank()
    ) {
        invalid()
    }
    receipt.operations.zip(request.operations).forEachIndexed { index, (committed, submitted) ->
        if (committed.operationId != submitted.operationId || committed.kind != submitted.kind ||
            committed.clientSeq != submitted.clientSeq || committed.clientSeq !in 0..max ||
            committed.serverSeq != receipt.fromServerSeq + index
        ) {
            invalid()
        }
    }
    return PendingSubmissionReceipt(status, headServerSeq, headWorkspaceVersion, receipt.workspaceVersion)
}
