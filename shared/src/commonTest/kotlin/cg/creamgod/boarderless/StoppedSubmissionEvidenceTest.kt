package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.remote.*
import kotlinx.serialization.json.*
import kotlin.test.*

class StoppedSubmissionEvidenceTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val wire =
        SubmitOperationsRequest(
            clientId = "client",
            transactionId = "transaction",
            baseVersion = 8,
            operations =
                listOf(
                    OperationDto("wire", 20, "delete_objects", payload = buildJsonObject { put("objectIds", JsonArray(emptyList())) }),
                ),
        )
    private val submitted = PendingWorkspaceSubmission(scope, "local", wire)
    private val receipt =
        TransactionReceiptDto(
            "transaction",
            "actor",
            "client",
            9,
            20,
            20,
            "2026-10-05T00:00:00Z",
            listOf(ReceiptOperationDto("wire", 20, 20, "delete_objects")),
        )
    private val response =
        SubmissionReceiptResponseDto(
            "workspace",
            21,
            10,
            listOf(receipt),
            listOf(ReceiptLookupDto("transaction", "transaction", "committed", "transaction", 9)),
        )
    private val ack =
        AcceptedOperationsDto(
            "duplicate",
            9,
            20,
            20,
            listOf(
                CommittedWorkspaceOperationDto(
                    20,
                    "wire",
                    "transaction",
                    "actor",
                    "client",
                    20,
                    8,
                    9,
                    "delete_objects",
                    wire.operations.single().payload,
                    1,
                    receipt.committedAt,
                ),
            ),
        )
    private val evidence = StoppedSubmissionEvidence(response, ack, 10, 21)
    private val bundle = WorkspaceDraftScopeBundle(scope = scope, stoppedPending = submitted)

    @Test fun evidenceUpgradesOnlyItsExactStoppedBundleAndRetainsOriginalWire() {
        assertFalse(WorkspaceDraftScopeBundleCodec.encode(bundle).decodeToString().contains("stoppedEvidence"))
        assertFalse(WorkspaceDraftScopeBundleCodec.encode(bundle).decodeToString().contains("retainedStopped"))
        val saved = WorkspaceDraftBundleTransitions.recordStoppedEvidence(bundle, submitted, evidence)
        assertEquals(2, saved.version)
        assertEquals(submitted, saved.stoppedPending)
        assertNull(saved.pending)
        assertEquals(evidence, saved.stoppedEvidence)
        assertFalse(WorkspaceDraftScopeBundleCodec.encode(saved).decodeToString().contains("retainedStopped"))
        assertEquals(saved, WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(saved), scope))
        assertEquals(saved, WorkspaceDraftBundleTransitions.recordStoppedEvidence(saved, submitted, evidence))
        assertFails { WorkspaceDraftBundleTransitions.stage(saved, submitted) }
        assertFails { WorkspaceDraftBundleTransitions.recordStoppedEvidence(bundle, submitted.copy(localOperationId = "other"), evidence) }
        assertFails { WorkspaceDraftScopeBundleCodec.encode(saved.copy(version = 1)) }
        assertFails { WorkspaceDraftScopeBundleCodec.encode(bundle.copy(version = 2)) }
    }

    @Test fun malformedOrUnknownEvidenceCannotBeStoredOrOverrideTerminalOutcome() {
        val fenced =
            evidence.copy(
                receipt =
                    response.copy(
                        receipts = emptyList(),
                        lookups = listOf(ReceiptLookupDto("transaction", "transaction", "fenced")),
                    ),
                acknowledgement = null,
            )
        assertEquals(PendingReceiptStatus.Fenced, fenced.validateAgainst(submitted))
        assertEquals(2, WorkspaceDraftBundleTransitions.recordStoppedEvidence(bundle, submitted, fenced).version)
        val bad =
            listOf(
                evidence.copy(acknowledgement = null),
                evidence.copy(observedWorkspaceVersion = 9),
                evidence.copy(observedServerSeq = 20),
                evidence.copy(acknowledgement = ack.copy(status = "accepted")),
                evidence.copy(acknowledgement = ack.copy(operations = emptyList())),
                fenced.copy(acknowledgement = ack),
                fenced.copy(receipt = fenced.receipt.copy(lookups = listOf(ReceiptLookupDto("transaction", "transaction", "unknown")))),
            )
        bad.forEach { assertFails { WorkspaceDraftBundleTransitions.recordStoppedEvidence(bundle, submitted, it) } }
        val saved = WorkspaceDraftBundleTransitions.recordStoppedEvidence(bundle, submitted, evidence)
        assertFails { WorkspaceDraftBundleTransitions.recordStoppedEvidence(saved, submitted, fenced) }
        val advanced = evidence.copy(observedWorkspaceVersion = 11, observedServerSeq = 22)
        // A repeat cannot replace already saved immutable log values, even an omitted default.
        val altered =
            advanced.copy(
                acknowledgement =
                    ack.copy(
                        operations =
                            ack.operations.map {
                                it.copy(payload = JsonObject(it.payload + ("serverDefault" to JsonPrimitive(1))))
                            },
                    ),
            )
        assertFails { WorkspaceDraftBundleTransitions.recordStoppedEvidence(saved, submitted, altered) }
        val newer = WorkspaceDraftBundleTransitions.recordStoppedEvidence(saved, submitted, advanced)
        assertFails { WorkspaceDraftBundleTransitions.recordStoppedEvidence(newer, submitted, evidence) }
        assertEquals(submitted, newer.stoppedPending)
    }
}
