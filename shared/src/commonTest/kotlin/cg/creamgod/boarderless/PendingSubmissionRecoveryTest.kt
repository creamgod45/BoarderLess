package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission as PersistedWorkspaceSubmission
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import kotlin.test.*

class PendingSubmissionRecoveryTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun repositoriesSharingPreferencesSerializeConcurrentNewSubmissions() = runTest {
        val preferences = SessionPreferences(InMemorySettings())
        val original = session(preferences)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sequences = mutableListOf<Long>()
        val http = HttpClient(MockEngine { request ->
            val wire = Json.decodeFromString<SubmitOperationsRequest>((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            sequences += wire.operations.single().clientSeq
            if (sequences.size == 1) { entered.complete(Unit); release.await() }
            error("Simulated response loss")
        }) { install(ContentNegotiation) { json() } }
        val first = BackendWorkspaceRepository("https://qa.example.invalid", preferences, http)
        val second = BackendWorkspaceRepository("https://qa.example.invalid", preferences, http)
        val operation = CreateObjectsOperation("local", listOf(TextNode(CanvasObjectId("node"),
            transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)), text = "Draft")))
        try {
            val one = async { runCatching { first.submit(original, operation) } }
            entered.await()
            val two = async { runCatching { second.submit(original.copy(workspace = Workspace(WorkspaceId("other"), "Other")), operation) } }
            runCurrent()
            assertEquals(listOf(1L), sequences)
            release.complete(Unit)
            assertTrue(one.await().isFailure)
            assertTrue(two.await().isFailure)
            assertEquals(listOf(1L, 2L), sequences)
        } finally { release.complete(Unit); first.close(); second.close() }
    }
    @Test fun responseLossDoesNotRecycleSequenceInAnotherWorkspace() = runTest {
        val preferences = SessionPreferences(InMemorySettings())
        val original = session(preferences)
        val requests = mutableListOf<SubmitOperationsRequest>()
        fun client() = HttpClient(MockEngine { http ->
            val wire = Json.decodeFromString<SubmitOperationsRequest>((http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            requests += wire
            error("Simulated response loss")
        }) { install(ContentNegotiation) { json() } }
        val first = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client())
        val second = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client())
        val operation = CreateObjectsOperation("local", listOf(TextNode(CanvasObjectId("node"),
            transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)), text = "Draft")))
        try {
            assertFails { first.submit(original, operation) }
            val other = original.copy(workspace = Workspace(WorkspaceId("other"), "Second"))
            assertFails { second.submit(other, operation) }
            assertEquals(listOf(1L, 2L), requests.map { it.operations.single().clientSeq })
            assertEquals(2L, preferences.clientSequence)
            assertNotEquals(requests[0].transactionId, requests[1].transactionId)
            assertEquals(requests[0], preferences.pendingSubmissions.load(PendingSubmissionScope(apiBase,
                original.userId, original.clientId, "workspace"))!!.request)
        } finally { first.close(); second.close() }
    }
    private val apiBase = "https://qa.example.invalid/api/v1"
    private fun session(preferences: SessionPreferences) = WorkspaceSession("actor", preferences.clientId,
        WorkspaceMemberRole.Owner, 8, 19, Workspace(WorkspaceId("workspace"), "Fixture"))
    private fun prepare(preferences: SessionPreferences): PersistedWorkspaceSubmission {
        val scope = PendingSubmissionScope(apiBase, "actor", preferences.clientId, "workspace")
        return PersistedWorkspaceSubmission(scope, "local", SubmitOperationsRequest(clientId = scope.clientId,
            transactionId = "00000000-0000-0000-0000-000000000040", baseVersion = 8,
            operations = listOf(OperationDto("00000000-0000-0000-0000-000000000050", 10, "create_object",
                payload = buildJsonObject { put("objectId", "00000000-0000-0000-0000-000000000060"); put("text", "素材🙂".repeat(3000)) }))))
            .also { preferences.pendingSubmissions.save(it) }
    }
    private fun ack(request: SubmitOperationsRequest, status: String = "duplicate") = AcceptedOperationsDto(status, 9, 20, 20,
        request.operations.map { CommittedWorkspaceOperationDto(20, it.operationId, request.transactionId, "actor",
            request.clientId, it.clientSeq, request.baseVersion, 9, it.kind, it.payload, 1, "2026-10-03T00:00:00Z") })

    @Test fun responseLossAndRepositoryRestartReplayIdenticalWireRequestWithoutAllocatingNewIds() = runTest {
        val settings = InMemorySettings()
        val preferences = SessionPreferences(settings).apply { clientSequence = 90 }
        val pending = prepare(preferences)
        val bodies = mutableListOf<String>()
        var commits = 0
        fun client() = HttpClient(MockEngine { request ->
            assertEquals("actor", request.headers["x-user-id"])
            assertEquals("/api/v1/workspaces/workspace/operations", request.url.encodedPath)
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            bodies += body
            val decoded = Json.decodeFromString<SubmitOperationsRequest>(body)
            assertEquals(pending.request, decoded)
            if (bodies.size == 1) { commits++; error("Simulated response loss after commit") }
            respond(Json.encodeToString(AcceptedOperationsDto.serializer(), ack(decoded)), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val first = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client())
        try { assertFailsWith<IllegalStateException> { first.retryPendingSubmission(session(preferences)) } }
        finally { first.close() }
        assertEquals(pending, preferences.pendingSubmissions.load(pending.scope))
        assertEquals(90L, preferences.clientSequence)
        val reopened = SessionPreferences(settings)
        val second = BackendWorkspaceRepository("https://qa.example.invalid", reopened, client())
        try { assertEquals(SubmitOutcome.Accepted(9, 20), second.retryPendingSubmission(session(reopened))) }
        finally { second.close() }
        assertEquals(1, commits)
        assertEquals(2, bodies.size)
        assertEquals(bodies.first(), bodies.last())
        assertEquals(90L, reopened.clientSequence, "Older duplicate must not regress the global client sequence")
        assertNull(reopened.pendingSubmissions.load(pending.scope))
    }

    @Test fun differentOwnerWorkspaceOriginClientAndViewerCannotSendSavedRequest() = runTest {
        val preferences = SessionPreferences(InMemorySettings())
        val pending = prepare(preferences)
        var calls = 0
        fun client() = HttpClient(MockEngine { calls++; error("Unexpected request") }) {
            install(ContentNegotiation) { json() }
        }
        val repository = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client())
        try {
            val current = session(preferences)
            assertNull(repository.retryPendingSubmission(current.copy(userId = "other")))
            assertNull(repository.retryPendingSubmission(current.copy(workspace = Workspace(WorkspaceId("other"), "Other"))))
            assertFailsWith<IllegalStateException> { repository.retryPendingSubmission(current.copy(clientId = "other")) }
            assertFailsWith<IllegalStateException> { repository.retryPendingSubmission(current.copy(role = WorkspaceMemberRole.Viewer)) }
        } finally { repository.close() }
        val other = BackendWorkspaceRepository("https://other.invalid", preferences, client())
        try { assertNull(other.retryPendingSubmission(session(preferences))) } finally { other.close() }
        assertEquals(0, calls)
        assertEquals(pending, preferences.pendingSubmissions.load(pending.scope))
    }

    @Test fun uncorrelatedHttp200RetainsPendingEvidenceAndDoesNotAdvanceSequence() = runTest {
        val preferences = SessionPreferences(InMemorySettings()).apply { clientSequence = 3 }
        val pending = prepare(preferences)
        val response = ack(pending.request).let { it.copy(operations = it.operations.map { op -> op.copy(actorId = "other") }) }
        val client = HttpClient(MockEngine { respond(Json.encodeToString(AcceptedOperationsDto.serializer(), response),
            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json() }
        }
        val repository = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client)
        try { assertFailsWith<BackendContractException> { repository.retryPendingSubmission(session(preferences)) } }
        finally { repository.close() }
        assertEquals(3L, preferences.clientSequence)
        assertEquals(pending, preferences.pendingSubmissions.load(pending.scope))
    }

    @Test fun normalSubmitPersistsBeforePostAndPublicRecoveryRefreshesAuthoritativeProjection() = runTest {
        val settings = InMemorySettings()
        val preferences = SessionPreferences(settings)
        val original = session(preferences)
        var savedRequest: SubmitOperationsRequest? = null
        var posts = 0
        val bodies = mutableListOf<String>()
        fun client() = HttpClient(MockEngine { http ->
            val result = when {
                http.method == HttpMethod.Post -> {
                    val body = (http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    bodies += body
                    val request = Json.decodeFromString<SubmitOperationsRequest>(body)
                    posts++
                    if (posts == 1) {
                        savedRequest = request
                        val persisted = preferences.pendingSubmissions.load(PendingSubmissionScope(apiBase,
                            original.userId, original.clientId, original.workspace.id.value))
                        assertEquals(request, persisted?.request, "Persist before transport begins")
                        error("Simulated response loss")
                    }
                    assertEquals(savedRequest, request)
                    Json.encodeToString(AcceptedOperationsDto.serializer(), ack(request))
                }
                http.url.encodedPath.endsWith("/operations") -> Json.encodeToString(CatchUpOperationsDto.serializer(),
                    CatchUpOperationsDto(emptyList(), 20, false))
                http.url.encodedPath.endsWith("/state") -> {
                    val objectPayload = checkNotNull(savedRequest).operations.single().payload
                    Json.encodeToString(WorkspaceStateDto.serializer(), WorkspaceStateDto("workspace", 9, 20,
                        listOf(CanvasObjectDto(objectPayload.getValue("objectId").jsonPrimitive.content,
                            objectPayload.getValue("objectType").jsonPrimitive.content, 1, zIndex = 0, locked = false,
                            transform = objectPayload.getValue("transform").jsonObject,
                            properties = objectPayload.getValue("properties").jsonObject))))
                }
                else -> Json.encodeToString(WorkspaceDto.serializer(), WorkspaceDto("workspace", "actor", "Fixture", 9, 20, 1, "owner"))
            }
            respond(result, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val first = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client())
        try {
            assertFailsWith<IllegalStateException> {
                first.submit(original, CreateObjectsOperation("local-create", listOf(TextNode(CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(240f, 120f)), text = "Saved draft"))))
            }
            assertNotNull(first.pendingChange(original))
        } finally { first.close() }
        val reopened = SessionPreferences(settings)
        val second = BackendWorkspaceRepository("https://qa.example.invalid", reopened, client())
        try {
            val pending = assertNotNull(second.pendingChange(original))
            val refreshed = second.retryPendingChange(original, pending.transactionId)
            assertEquals(20L, refreshed.lastServerSeq)
            assertEquals(9L, refreshed.workspaceVersion)
            assertEquals("Saved draft", (refreshed.workspace.objects.values.single() as TextNode).text)
            assertNull(second.pendingChange(refreshed))
        } finally { second.close() }
        assertEquals(2, posts)
        assertEquals(bodies.first(), bodies.last())
        assertEquals(checkNotNull(savedRequest).operations.single().clientSeq, reopened.clientSequence)
    }
}
