package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class WorkspaceDraftScopeBundleTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val create =
        CreateObjectsOperation(
            "local-head",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                    text = "🙂",
                ),
            ),
        )
    private val journal =
        WorkspaceDraftJournal(
            scope = scope,
            id = "draft",
            baseVersion = 8,
            baseServerSeq = 19,
            baseWorkspace = Workspace(WorkspaceId("workspace"), "Fixture"),
            operations = listOf(create),
            headTransactionId = "tx",
        )
    private val pending =
        PendingWorkspaceSubmission(
            scope,
            "local-head",
            SubmitOperationsRequest(
                clientId = "client",
                transactionId = "tx",
                baseVersion = 8,
                operations = listOf(OperationDto("wire-head", 20, "create_object", payload = buildJsonObject { put("text", "🙂") })),
            ),
        )
    private val bundle = WorkspaceDraftScopeBundle(scope = scope, journal = journal, pending = pending)

    @Test fun exactWireAndJournalRoundTripWithoutRegeneratingIdentity() {
        val decoded = WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(bundle), scope)
        assertEquals(bundle, decoded)
        assertEquals("local-head", decoded.pending!!.localOperationId)
        assertEquals(
            "wire-head",
            decoded.pending.request.operations
                .single()
                .operationId,
        )
        assertEquals("tx", decoded.pending.request.transactionId)
        assertEquals(journal.replay(), decoded.journal!!.replay())
    }

    @Test fun inconsistentHeadReceiptAndBaseVersionAreRejected() {
        for (invalid in listOf(
            bundle.copy(pending = null),
            bundle.copy(pending = pending.copy(localOperationId = "other")),
            bundle.copy(pending = pending.copy(request = pending.request.copy(transactionId = "other"))),
            bundle.copy(pending = pending.copy(request = pending.request.copy(baseVersion = 7))),
            bundle.copy(journal = journal.copy(lastAcknowledgedTransactionId = "tx")),
            bundle.copy(journal = journal.copy(scope = scope.copy(userId = "other"))),
        )) {
            assertFails { WorkspaceDraftScopeBundleCodec.encode(invalid) }
        }
    }

    @Test fun wireOnlyLegacyAndAcknowledgedJournalStayDistinct() {
        val legacy = bundle.copy(journal = null)
        assertEquals(legacy, WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(legacy), scope))
        val acknowledged =
            bundle.copy(
                pending = null,
                journal =
                    journal.copy(
                        operations = emptyList(),
                        headTransactionId = null,
                        lastAcknowledgedTransactionId = "tx",
                    ),
            )
        assertEquals(acknowledged, WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(acknowledged), scope))
        assertFails { WorkspaceDraftScopeBundleCodec.encode(bundle.copy(journal = null, pending = null)) }
        assertNotEquals(
            WorkspaceDraftScopeBundleCodec.scopeHash(scope),
            WorkspaceDraftScopeBundleCodec.scopeHash(scope.copy(clientId = "other")),
        )
    }
}
