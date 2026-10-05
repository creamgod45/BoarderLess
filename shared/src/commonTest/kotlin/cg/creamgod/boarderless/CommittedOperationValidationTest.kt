package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertIs

class CommittedOperationValidationTest {
    private val request = SubmitOperationsRequest(clientId = "client", transactionId = "transaction",
        baseVersion = 8, operations = listOf(
            OperationDto("op-1", 10, "create_object", payload = buildJsonObject { put("objectId", "image") }),
            OperationDto("op-2", 11, "create_relation", payload = buildJsonObject { put("relationId", "link") }),
        ))
    private val accepted = AcceptedOperationsDto("accepted", 9, 20, 21,
        request.operations.mapIndexed { index, op -> CommittedWorkspaceOperationDto(
            20L + index, op.operationId, request.transactionId, "actor", request.clientId, op.clientSeq,
            request.baseVersion, 9, op.kind, op.payload, 1, "2026-10-02T00:00:00Z") })

    @Test
    fun fullDurableRecordSurvivesJsonAndValidatesAcceptedAndDuplicate() {
        val wire = Json.encodeToString(AcceptedOperationsDto.serializer(), accepted)
        val decoded = Json.decodeFromString<AcceptedOperationsDto>(wire)
        assertEquals(accepted, decoded)
        decoded.validateAcknowledgement(request, "actor")
        decoded.copy(status = "duplicate").validateAcknowledgement(request, "actor")
    }

    @Test
    fun atomicBoundaryRejectsMixedRecordsAndAllowsOlderNonConflictingBase() {
        fun valid(records: List<CommittedWorkspaceOperationDto>) =
            isValidCommittedTransaction(records, 20, 21, 9)
        assertTrue(valid(accepted.operations))
        assertTrue(valid(accepted.operations.map { it.copy(baseVersion = 3) }))
        // Existing server accepts non-consecutive clientSeq. REST correlation still matches
        // every submitted sequence, but remote transactions cannot assume our mapper's policy.
        assertTrue(valid(listOf(accepted.operations.first(), accepted.operations.last().copy(clientSeq = 99))))
        val first = accepted.operations.first()
        for (invalid in listOf(first.copy(operationId = " "), first.copy(transactionId = " "),
            first.copy(actorId = " "), first.copy(clientId = " "), first.copy(operationType = " "),
            first.copy(baseVersion = 9), first.copy(baseVersion = -1), first.copy(clientSeq = Long.MAX_VALUE))) {
            kotlin.test.assertFalse(valid(listOf(invalid, accepted.operations.last())))
        }
        val second = accepted.operations.last()
        for (invalid in listOf(second.copy(transactionId = "other"), second.copy(actorId = "other"),
            second.copy(clientId = "other"), second.copy(baseVersion = 7), second.copy(clientSeq = -1))) {
            kotlin.test.assertFalse(valid(listOf(first, invalid)))
        }
    }

    @Test
    fun atomicRecordLimitIsEnforcedWithoutPublishingPartialTransaction() {
        val records = (0 until 201).map { index -> accepted.operations.first().copy(
            operationId = "op-$index", serverSeq = 20L + index, clientSeq = 10L + index) }
        kotlin.test.assertFalse(isValidCommittedTransaction(records, 20, 220, 9))
        assertTrue(isValidCommittedTransaction(records.take(200), 20, 219, 9))
        kotlin.test.assertFalse(isValidCommittedTransaction(emptyList(), 20, 20, 9))
    }

    @Test
    fun activitySummaryOrMissingPayloadCannotBecomeAcknowledgement() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<AcceptedOperationsDto>("""{"status":"accepted","workspaceVersion":9,"toServerSeq":21}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<CommittedWorkspaceOperationDto>("""{"serverSeq":20,"actorId":"actor","clientId":"client","workspaceVersion":9,"operationType":"create_object","committedAt":"today"}""")
        }
    }

    @Test
    fun wrongScopeCorrelationVersionOrSchemaIsRejectedWithoutEchoingPayload() {
        val first = accepted.operations.first()
        for (record in listOf(first.copy(operationId = "different"), first.copy(transactionId = "old"),
            first.copy(actorId = "other"), first.copy(clientId = "other"), first.copy(clientSeq = 99),
            first.copy(baseVersion = 7), first.copy(workspaceVersion = 10), first.copy(operationType = "delete_objects"),
            first.copy(schemaVersion = 2), first.copy(committedAt = ""))) {
            val error = assertFailsWith<BackendContractException> {
                accepted.copy(operations = listOf(record, accepted.operations.last())).validateAcknowledgement(request, "actor")
            }
            assertEquals("Invalid committed transaction acknowledgement", error.message)
        }
    }

    @Test
    fun gapsReorderingPartialDuplicatesAndInvalidRangesFailClosed() {
        for (response in listOf(accepted.copy(status = "unknown"), accepted.copy(workspaceVersion = 0),
            accepted.copy(fromServerSeq = 0), accepted.copy(toServerSeq = 22), accepted.copy(toServerSeq = Long.MAX_VALUE),
            accepted.copy(operations = accepted.operations.reversed()), accepted.copy(operations = accepted.operations.take(1)),
            accepted.copy(operations = listOf(accepted.operations.first(), accepted.operations.first())),
            accepted.copy(operations = listOf(accepted.operations.first(), accepted.operations.last().copy(serverSeq = 22))))) {
            assertFailsWith<BackendContractException> { response.validateAcknowledgement(request, "actor") }
        }
    }

    @Test
    fun malformedOrUnrelatedHttp200KeepsUnconfirmedWireAndDoesNotAcknowledgeReservation() = runTest {
        val responses = listOf("""{"status":"accepted","workspaceVersion":9,"toServerSeq":21}""",
            Json.encodeToString(AcceptedOperationsDto.serializer(), accepted))
        for (response in responses) {
            val preferences = SessionPreferences(InMemorySettings()).apply { clientSequence = 45 }
            var requestCount = 0
            val client = HttpClient(MockEngine {
                requestCount++
                respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            try {
                val session = WorkspaceSession("actor", preferences.clientId, WorkspaceMemberRole.Owner,
                    8, 19, Workspace(WorkspaceId("workspace"), "Fixture"))
                val repository = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client)
                val failure = assertFailsWith<Exception> {
                    repository.submit(session, CreateObjectsOperation("local", listOf(TextNode(CanvasObjectId("node"),
                        transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(240f, 120f)), text = "Draft"))))
                }
                if (response == responses.first()) {
                    assertTrue(failure is JsonConvertException || failure is SerializationException)
                } else {
                    assertIs<BackendContractException>(failure)
                }
                assertEquals(1, requestCount)
                assertEquals(46L, preferences.clientSequence, "Reserved sequence must not be recycled after transport")
                val pending = kotlin.test.assertNotNull(preferences.pendingSubmissions.load(
                    PendingSubmissionScope("https://qa.example.invalid/api/v1", "actor", preferences.clientId, "workspace")))
                assertEquals(46L, pending.request.operations.single().clientSeq)
                assertEquals(8L, pending.request.baseVersion)
                assertEquals(8L, session.workspaceVersion)
                assertEquals(19L, session.lastServerSeq)
            } finally { client.close() }
        }
    }
}
