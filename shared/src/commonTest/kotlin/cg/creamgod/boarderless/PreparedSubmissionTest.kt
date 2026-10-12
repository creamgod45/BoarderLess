package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class PreparedSubmissionTest {
    @Test fun prepareSendsNoHttpAndReopenResendsExactSavedWireRequest() = runTest {
        val settings = InMemorySettings()
        val preferences = SessionPreferences(settings)
        val workspace = Workspace(WorkspaceId("board"), "Board")
        val session = WorkspaceSession("user", preferences.clientId, WorkspaceMemberRole.Owner, 0, 0, workspace)
        val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "本機")
        val operation = TransactionOperation("local-transaction", listOf(CreateObjectsOperation("local-create", listOf(node))))
        val sent = mutableListOf<SubmitOperationsRequest>()
        fun client() = HttpClient(MockEngine { request ->
            sent += Json.decodeFromString<SubmitOperationsRequest>((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            error("Response lost")
        }) { install(ContentNegotiation) { json() } }
        val first = BackendWorkspaceRepository("https://example.invalid", preferences, client())
        val prepared = try {
            assertFails { first.prepareSubmission(session, operation) }
            first.retainDraft(session, workspace, operation)
            first.prepareSubmission(session, operation).also {
                assertEquals(it, first.prepareSubmission(session, operation))
                assertEquals(it, first.preparedSubmission(session))
                assertEquals(operation.operationId, it.localOperationId)
                assertTrue(sent.isEmpty())
                assertFails { first.prepareSubmission(session, operation.copy(operations = listOf(CreateObjectsOperation("local-create", listOf(node.copy(text = "Changed")))))) }
                assertFails { first.prepareSubmission(session.copy(role = WorkspaceMemberRole.Viewer), operation) }
            }
        } finally { first.close() }
        val reopened = BackendWorkspaceRepository("https://example.invalid", SessionPreferences(settings), client())
        try {
            assertEquals(prepared, reopened.prepareSubmission(session, operation))
            assertTrue(sent.isEmpty())
            assertFails { reopened.sendPreparedSubmission(session, prepared.copy(requestDigest = "a".repeat(64))) }
            assertTrue(sent.isEmpty())
            assertFails { reopened.sendPreparedSubmission(session, prepared) }
            val request = sent.single()
            assertEquals(prepared.transactionId, request.transactionId)
            assertEquals(prepared.operationIds, request.operations.map { it.operationId })
            assertEquals(prepared.clientSequences, request.operations.map { it.clientSeq })
            assertEquals(prepared, reopened.preparedSubmission(session))
        } finally { reopened.close() }
    }
}
