package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlin.test.*

class CapturedClientSequenceFloorTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "user", "client", "workspace")
    private fun wire(target: PendingSubmissionScope, sequence: Long) = PendingWorkspaceSubmission(
        target, "local", SubmitOperationsRequest(clientId = target.clientId, transactionId = "tx", baseVersion = 8,
            operations = listOf(OperationDto("wire", sequence, "create_object", payload = buildJsonObject {}))))
    private fun acknowledged(target: PendingSubmissionScope, sequence: Long): WorkspaceDraftScopeBundle {
        val journal = WorkspaceDraftJournal(scope = target, id = "draft", baseVersion = 9, baseServerSeq = 5000,
            baseWorkspace = Workspace(WorkspaceId(target.workspaceId), "Fixture"), operations = emptyList(),
            lastAcknowledgedTransactionId = "tx")
        return WorkspaceDraftScopeBundle(scope = target, journal = journal,
            acknowledged = LocalDraftAcknowledgement(wire(target, sequence), 9, 5000))
    }

    @Test fun includesEveryWireSourceAcrossWorkspacesWithoutConfusingServerSequence() {
        val pendingScope = scope.copy(workspaceId = "pending")
        val ackScope = scope.copy(workspaceId = "ack")
        val stoppedScope = scope.copy(workspaceId = "stopped")
        val bundles = listOf(WorkspaceDraftScopeBundle(scope = pendingScope, pending = wire(pendingScope, 80)),
            acknowledged(ackScope, 95), WorkspaceDraftScopeBundle(scope = stoppedScope, stoppedPending = wire(stoppedScope, 101)))
        assertEquals(101L, CapturedClientSequenceFloor.calculate(scope, 40, listOf(wire(scope, 70)), bundles))
        assertEquals(95L, CapturedClientSequenceFloor.calculate(scope, 40, emptyList(), listOf(acknowledged(ackScope, 95))))
        assertEquals(150L, CapturedClientSequenceFloor.calculate(scope, 150, emptyList(), bundles))
        assertEquals(101, bundles.last().stoppedPending!!.request.operations.single().clientSeq.toInt())
    }

    @Test fun apiUserClientNamespaceIsolatedButWorkspaceIsNot() {
        val foreign = listOf(scope.copy(apiBase = "https://other.invalid/api/v1"),
            scope.copy(userId = "other"), scope.copy(clientId = "other"))
        assertEquals(12L, CapturedClientSequenceFloor.calculate(scope, 12,
            foreign.map { wire(it, 800) }, foreign.map { acknowledged(it, 900) }))
        assertEquals(800L, CapturedClientSequenceFloor.calculate(scope, 12,
            listOf(wire(scope.copy(workspaceId = "another"), 800)), emptyList()))
    }

    @Test fun missingUnsafeOrDuplicateEvidenceRequiresRecoveryInsteadOfZero() {
        for (counter in listOf(null, -1L, 9_007_199_254_740_992L)) {
            assertFails { CapturedClientSequenceFloor.calculate(scope, counter, emptyList(), emptyList()) }
        }
        assertEquals(0L, CapturedClientSequenceFloor.calculate(scope, 0, emptyList(), emptyList()))
        assertEquals(9_007_199_254_740_991L,
            CapturedClientSequenceFloor.calculate(scope, 9_007_199_254_740_991L, emptyList(), emptyList()))
        val entry = wire(scope, 10)
        assertFails { CapturedClientSequenceFloor.calculate(scope, 0, listOf(entry, entry), emptyList()) }
        val bundle = acknowledged(scope, 20)
        assertFails { CapturedClientSequenceFloor.calculate(scope, 0, emptyList(), listOf(bundle, bundle)) }
    }

    @Test fun invalidForeignEvidenceIsNotSkippedAndOversizedCatalogRejected() {
        val foreign = scope.copy(userId = "other")
        assertFails { CapturedClientSequenceFloor.calculate(scope, 10, listOf(wire(foreign, -1)), emptyList()) }
        assertFails { CapturedClientSequenceFloor.calculate(scope, 10, emptyList(),
            listOf(acknowledged(foreign, 100).copy(version = 2))) }
        assertFails { CapturedClientSequenceFloor.calculate(scope, 10,
            (0..256).map { wire(scope.copy(workspaceId = "$it"), 10) }, emptyList()) }
    }

    @Test fun deepOrOversizedCapturedWireIsRejectedWithoutChangingEvidence() {
        val original = wire(scope, 80)
        val payloads = listOf(buildJsonObject { put("text", "x".repeat(1024 * 1024)) },
            buildJsonObject { put("deep", Json.parseToJsonElement("[".repeat(70) + "0" + "]".repeat(70))) })
        for (payload in payloads) {
            val entry = original.copy(request = original.request.copy(
                operations = listOf(original.request.operations.single().copy(payload = payload))))
            assertFails { CapturedClientSequenceFloor.calculate(scope, 10, listOf(entry), emptyList()) }
            assertEquals(payload, entry.request.operations.single().payload)
            assertEquals(80L, entry.request.operations.single().clientSeq)
        }
    }
}
