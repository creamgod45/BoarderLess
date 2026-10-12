package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class PreparedSubmissionRecoveryTest {
    @Test fun originalReceiptCanBeVerifiedAfterPendingAckCleanupWithoutRecoveryWrites() = runTest {
        for (mode in listOf("committed", "payload", "unknown", "later")) {
            val memory = InMemorySettings(); val prefs = SessionPreferences(memory)
            val workspace = Workspace(WorkspaceId("board"), "Board")
            val session = WorkspaceSession("user", prefs.clientId, WorkspaceMemberRole.Owner, 0, 0, workspace)
            val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Original")
            val operation = CreateObjectsOperation("local-create", listOf(node))
            var calls = 0
            lateinit var wire: SubmitOperationsRequest
            val client = HttpClient(MockEngine { request ->
                calls++
                val op = wire.operations.single()
                val receipt = TransactionReceiptDto(wire.transactionId, "user", prefs.clientId, 1, 1, 1,
                    "2026-10-10T00:00:00Z", listOf(ReceiptOperationDto(op.operationId, op.clientSeq, 1, op.kind)))
                val head = if (mode == "later") 2L else 1L
                val body = when {
                    request.url.encodedPath.endsWith("/receipts") -> Json.encodeToString(SubmissionReceiptResponseDto(
                        "board", head, head, if (mode == "unknown") emptyList() else listOf(receipt),
                        listOf(ReceiptLookupDto(type = "transaction", id = wire.transactionId, status = if (mode == "unknown") "unknown" else "committed",
                            transactionId = if (mode == "unknown") null else wire.transactionId, workspaceVersion = if (mode == "unknown") null else 1))))
                    request.url.encodedPath.endsWith("/operations") -> Json.encodeToString(FullCatchUpOperationsDto(listOf(
                        CommittedWorkspaceOperationDto(1, op.operationId, wire.transactionId, "user", prefs.clientId,
                            op.clientSeq, 0, 1, op.kind, if (mode == "payload") buildJsonObject { put("changed", true) } else op.payload,
                            1, receipt.committedAt)) + if (mode == "later") listOf(
                                CommittedWorkspaceOperationDto(2, "peer-delete", "peer-transaction", "peer", "peer-client", 1,
                                    1, 2, "delete_objects", buildJsonObject { put("objectIds", JsonArray(listOf(JsonPrimitive(node.id.value)))) },
                                    1, receipt.committedAt)) else emptyList(), head, false))
                    request.url.encodedPath.endsWith("/state") -> Json.encodeToString(WorkspaceStateDto("board", head, head, emptyList()))
                    else -> Json.encodeToString(WorkspaceDto("board", "user", "Board", head, head, 1, "owner"))
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json() } }
            val repository = BackendWorkspaceRepository("https://example.invalid", prefs, client)
            try {
                repository.retainDraft(session, workspace, operation)
                val identity = repository.prepareSubmission(session, operation)
                val scope = PendingSubmissionScope("https://example.invalid/api/v1", "user", prefs.clientId, "board")
                val pending = prefs.draftPersistence.pending(scope)!!
                wire = pending.request
                prefs.draftPersistence.acknowledge(pending, 1, 1)
                assertNull(repository.preparedSubmission(session))
                val before = memory.keys.associateWith(memory::getStringOrNull)
                assertFails { repository.verifyPreparedSubmission(session, identity.copy(requestDigest = "a".repeat(64)), operation) }
                assertEquals(0, calls)
                if (mode == "payload") assertFails { repository.verifyPreparedSubmission(session, identity, operation) }
                else {
                    val verified = repository.verifyPreparedSubmission(session, identity, operation, if (mode == "later") session else null)
                    assertEquals(if (mode == "unknown") PendingReceiptStatus.Unknown else PendingReceiptStatus.Committed, verified.status)
                    assertEquals(identity, verified.prepared)
                    assertEquals(if (mode == "unknown") null else 1L, verified.committedWorkspaceVersion)
                    if (mode == "later") {
                        assertTrue(verified.current.workspace.objects.isEmpty())
                        assertEquals(2L, verified.current.workspaceVersion)
                        assertEquals(node, verified.committedWorkspace!!.objects[node.id])
                        assertEquals(1L, verified.committedWorkspace!!.version)
                    }
                }
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            } finally { repository.close() }
        }
    }
}
