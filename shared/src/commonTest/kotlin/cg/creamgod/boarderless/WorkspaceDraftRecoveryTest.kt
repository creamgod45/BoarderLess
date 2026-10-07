package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class WorkspaceDraftRecoveryTest {
    private val baseUrl = "https://qa.invalid"

    private fun opened(preferences: SessionPreferences) =
        WorkspaceSession(
            "actor",
            preferences.clientId,
            WorkspaceMemberRole.Owner,
            8,
            19,
            Workspace(WorkspaceId("workspace"), "Fixture"),
        )

    private val create =
        CreateObjectsOperation(
            "create",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                    text = "Draft",
                ),
            ),
        )
    private val edit = EditTextOperation("edit", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))

    private fun stage(
        repository: BackendWorkspaceRepository,
        session: WorkspaceSession,
    ) {
        repository.retainDraft(session, session.workspace, create)
        val after = (create.applyTo(session.workspace) as OperationResult.Applied).workspace
        repository.retainDraft(session, after, edit)
    }

    private class Server(
        var loseFirstResponse: Boolean = false,
    ) {
        var version = 8L
        var seq = 19L
        var role = "owner"
        var denyReads = false
        val objects = mutableListOf<CanvasObjectDto>()
        val posts = mutableListOf<String>()
        val methods = mutableListOf<HttpMethod>()
        private val acknowledgements = mutableMapOf<String, AcceptedOperationsDto>()

        fun client() =
            HttpClient(
                MockEngine { request ->
                    assertEquals("actor", request.headers["x-user-id"])
                    methods += request.method
                    if (denyReads && request.method == HttpMethod.Get) {
                        return@MockEngine respond(
                            "{}",
                            HttpStatusCode.Forbidden,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    val body =
                        if (request.method == HttpMethod.Post) {
                            val raw = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            posts += raw
                            val wire = Json.decodeFromString<SubmitOperationsRequest>(raw)
                            val old = acknowledgements[wire.transactionId]
                            val ack =
                                if (old != null) {
                                    old.copy(status = "duplicate")
                                } else {
                                    version++
                                    val from = seq + 1
                                    val records =
                                        wire.operations.map { operation ->
                                            val payload = operation.payload
                                            when (operation.kind) {
                                                "create_object" -> {
                                                    objects +=
                                                        CanvasObjectDto(
                                                            payload.getValue("objectId").jsonPrimitive.content,
                                                            payload.getValue("objectType").jsonPrimitive.content,
                                                            1,
                                                            zIndex = 0,
                                                            locked = false,
                                                            transform = payload.getValue("transform").jsonObject,
                                                            properties = payload.getValue("properties").jsonObject,
                                                        )
                                                }

                                                "update_object" -> {
                                                    val index =
                                                        objects.indexOfFirst {
                                                            it.objectId ==
                                                                payload.getValue("objectId").jsonPrimitive.content
                                                        }
                                                    check(index >= 0)
                                                    val current = objects[index]
                                                    objects[index] =
                                                        current.copy(
                                                            objectVersion = current.objectVersion + 1,
                                                            properties =
                                                                JsonObject(current.properties + payload.getValue("properties").jsonObject),
                                                        )
                                                }

                                                else -> {
                                                    error("Unexpected fixture operation")
                                                }
                                            }
                                            CommittedWorkspaceOperationDto(
                                                ++seq,
                                                operation.operationId,
                                                wire.transactionId,
                                                "actor",
                                                wire.clientId,
                                                operation.clientSeq,
                                                wire.baseVersion,
                                                version,
                                                operation.kind,
                                                payload,
                                                1,
                                                "2026-10-03T00:00:00Z",
                                            )
                                        }
                                    AcceptedOperationsDto("accepted", version, from, seq, records).also {
                                        acknowledgements[wire.transactionId] =
                                            it
                                    }
                                }
                            if (loseFirstResponse) {
                                loseFirstResponse = false
                                error("Response lost after commit")
                            }
                            Json.encodeToString(AcceptedOperationsDto.serializer(), ack)
                        } else {
                            when {
                                request.url.encodedPath.endsWith("/state") -> {
                                    Json.encodeToString(
                                        WorkspaceStateDto.serializer(),
                                        WorkspaceStateDto("workspace", version, seq, objects.toList(), emptyList()),
                                    )
                                }

                                request.url.encodedPath.endsWith("/operations") -> {
                                    "{}"
                                }

                                // Legacy response deliberately requires state refresh.
                                else -> {
                                    Json.encodeToString(
                                        WorkspaceDto.serializer(),
                                        WorkspaceDto("workspace", "actor", "Fixture", version, seq, 1, role),
                                    )
                                }
                            }
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    @Test fun lostResponseOrInterruptedWireCleanupRetainsTailAndReusesExactHeadWire() =
        runTest {
            for (lostResponse in listOf(true, false)) {
                val memory = InMemorySettings()
                var failWireRemoval = !lostResponse
                val settings =
                    object : Settings by memory {
                        override fun remove(key: String) {
                            if (failWireRemoval && key.startsWith("wsp1.")) {
                                failWireRemoval = false
                                error("Stop before wire cleanup")
                            }
                            memory.remove(key)
                        }
                    }
                val preferences = SessionPreferences(settings)
                val session = opened(preferences)
                val server = Server(lostResponse)
                val first = BackendWorkspaceRepository(baseUrl, preferences, server.client())
                try {
                    stage(first, session)
                    assertFailsWith<IllegalStateException> { first.submit(session, create) }
                    assertNotNull(first.pendingChange(session))
                    assertEquals(if (lostResponse) 2 else 1, first.pendingDraft(session)?.operationCount)
                } finally {
                    first.close()
                }
                val second = BackendWorkspaceRepository(baseUrl, SessionPreferences(memory), server.client())
                try {
                    val pending = assertNotNull(second.pendingChange(session))
                    val authoritative = second.retryPendingChange(session, pending.transactionId)
                    assertNull(second.pendingChange(authoritative))
                    val draft = assertNotNull(second.pendingDraft(authoritative))
                    assertEquals(1, draft.operationCount)
                    assertFalse(draft.requiresReview)
                    assertEquals(2, server.posts.size) // No automatic tail submission during recovery.
                    assertEquals(server.posts.first(), server.posts.last())
                    val restored = second.restorePendingDraft(authoritative, draft.id)
                    assertEquals(listOf(edit), restored.operations)
                    assertEquals(2, server.posts.size) // Explicit restore validates, UI then schedules FIFO.
                    assertIs<SubmitOutcome.Accepted>(second.submit(restored.session, restored.operations.single()))
                    assertEquals(
                        "Edited",
                        server.objects
                            .single()
                            .properties
                            .getValue("text")
                            .jsonPrimitive.content,
                    )
                    assertEquals(3, server.posts.size)
                    assertNull(second.pendingDraft(restored.session))
                    assertNull(second.pendingChange(restored.session))
                } finally {
                    second.close()
                }
            }
        }

    @Test fun changedRemoteBaselineOrViewerRoleRetainsDraftWithoutPosting() =
        runTest {
            for (changedVersion in listOf(true, false)) {
                val preferences = SessionPreferences(InMemorySettings())
                val session = opened(preferences)
                val server = Server()
                val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
                try {
                    stage(repository, session)
                    val draft = assertNotNull(repository.pendingDraft(session))
                    if (changedVersion) {
                        server.version = 9
                        server.seq = 20
                    } else {
                        server.role = "viewer"
                    }
                    assertFailsWith<IllegalStateException> { repository.restorePendingDraft(session, draft.id) }
                    assertEquals(2, repository.pendingDraft(session)?.operationCount)
                    assertTrue(server.posts.isEmpty())
                    assertNull(repository.pendingDraft(session.copy(userId = "other")))
                } finally {
                    repository.close()
                }
            }
        }

    @Test fun stopWireResendQuarantinesDraftAndExplicitRemovalNeverMutatesServer() =
        runTest {
            val preferences = SessionPreferences(InMemorySettings())
            val session = opened(preferences)
            val server = Server(loseFirstResponse = true)
            val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
            try {
                stage(repository, session)
                assertFailsWith<IllegalStateException> { repository.submit(session, create) }
                val pending = assertNotNull(repository.pendingChange(session))
                val scope = PendingSubmissionScope("$baseUrl/api/v1", "actor", preferences.clientId, "workspace")
                val originalWire = assertNotNull(preferences.pendingSubmissions.load(scope))
                val current = repository.dismissPendingChange(session, pending.transactionId)
                assertEquals(originalWire, preferences.pendingSubmissions.loadStopped(scope, pending.transactionId))
                assertNull(repository.pendingChange(current))
                val draft = assertNotNull(repository.pendingDraft(current))
                assertTrue(draft.requiresReview)
                assertEquals(2, draft.operationCount)
                assertFailsWith<IllegalStateException> { repository.restorePendingDraft(current, draft.id) }
                assertEquals(1, server.posts.size)
                assertFailsWith<IllegalStateException> { repository.dismissPendingDraft(current, "wrong-id") }
                assertNotNull(repository.pendingDraft(current))
                repository.dismissPendingDraft(current, draft.id)
                assertNull(repository.pendingDraft(current))
                assertEquals(originalWire, preferences.pendingSubmissions.loadStopped(scope, pending.transactionId))
                assertEquals(
                    "Draft",
                    server.objects
                        .single()
                        .properties
                        .getValue("text")
                        .jsonPrimitive.content,
                )
                assertEquals(1, server.posts.size)
            } finally {
                repository.close()
            }
        }

    @Test fun failedStopArchiveNeverClearsPendingOrQuarantinesDraft() =
        runTest {
            val memory = InMemorySettings()
            val settings =
                object : Settings by memory {
                    override fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (!key.startsWith("wsa1.")) memory.putString(key, value)
                    }
                }
            val preferences = SessionPreferences(settings)
            val session = opened(preferences)
            val server = Server(loseFirstResponse = true)
            val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
            try {
                stage(repository, session)
                assertFails { repository.submit(session, create) }
                val pending = assertNotNull(repository.pendingChange(session))
                val scope = PendingSubmissionScope("$baseUrl/api/v1", "actor", preferences.clientId, "workspace")
                val wire = assertNotNull(preferences.pendingSubmissions.load(scope))
                val draft = assertNotNull(preferences.draftJournals.load(scope))
                assertFails { repository.dismissPendingChange(session, pending.transactionId) }
                assertEquals(wire, preferences.pendingSubmissions.load(scope))
                assertEquals(draft, preferences.draftJournals.load(scope))
                assertEquals(1, server.posts.size)
            } finally {
                repository.close()
            }
        }

    @Test fun failedQuarantineAfterArchiveRetainsPendingAndAllowsExactStopRetry() =
        runTest {
            val memory = InMemorySettings()
            var dropQuarantine = false
            val settings =
                object : Settings by memory {
                    override fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (!(dropQuarantine && key.startsWith("wdj1."))) memory.putString(key, value)
                    }
                }
            val preferences = SessionPreferences(settings)
            val session = opened(preferences)
            val server = Server(loseFirstResponse = true)
            val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
            try {
                stage(repository, session)
                assertFails { repository.submit(session, create) }
                val pending = assertNotNull(repository.pendingChange(session))
                val scope = PendingSubmissionScope("$baseUrl/api/v1", "actor", preferences.clientId, "workspace")
                val wire = assertNotNull(preferences.pendingSubmissions.load(scope))
                dropQuarantine = true
                assertFails { repository.dismissPendingChange(session, pending.transactionId) }
                assertEquals(wire, preferences.pendingSubmissions.load(scope))
                assertEquals(wire, preferences.pendingSubmissions.loadStopped(scope, pending.transactionId))
                dropQuarantine = false
                repository.dismissPendingChange(session, pending.transactionId)
                assertNull(repository.pendingChange(session))
                assertTrue(assertNotNull(preferences.draftJournals.load(scope)).quarantined)
                assertEquals(wire, preferences.pendingSubmissions.loadStopped(scope, pending.transactionId))
                assertEquals(1, server.posts.size)
            } finally {
                repository.close()
            }
        }

    @Test fun viewerCanReviewQuarantinedDraftWithoutWritingJournalOrSendingOperations() =
        runTest {
            val preferences = SessionPreferences(InMemorySettings())
            val session = opened(preferences)
            val server = Server(loseFirstResponse = true)
            val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
            try {
                stage(repository, session)
                assertFailsWith<IllegalStateException> { repository.submit(session, create) }
                val pending = assertNotNull(repository.pendingChange(session))
                val unconfirmedReview = repository.reviewPendingDraft(session, assertNotNull(repository.pendingDraft(session)).id)
                assertTrue(unconfirmedReview.hasUnconfirmedSubmission)
                repository.dismissPendingChange(session, pending.transactionId)
                server.role = "viewer"
                val scope = PendingSubmissionScope("$baseUrl/api/v1", "actor", preferences.clientId, "workspace")
                val before = preferences.draftJournals.load(scope)
                server.methods.clear()
                val draft = assertNotNull(repository.pendingDraft(session))
                val reviewed = repository.reviewPendingDraft(session, draft.id)
                assertTrue(reviewed.quarantined)
                assertNull(repository.pendingChange(session))
                assertTrue(reviewed.hasUnconfirmedSubmission) // Stopping resend is not a validated acknowledgement.
                assertFalse(reviewed.baselineMatchesCurrent)
                assertEquals(
                    "Edited",
                    (
                        reviewed.proposed.objects.values
                            .single() as TextNode
                    ).text,
                )
                assertEquals(
                    "Draft",
                    (
                        reviewed.current.objects.values
                            .single() as TextNode
                    ).text,
                )
                assertEquals(before, preferences.draftJournals.load(scope))
                assertTrue(server.methods.isNotEmpty() && server.methods.all { it == HttpMethod.Get })
                assertEquals(1, server.posts.size)
                reviewed.toBackupJson()
                assertEquals(before, preferences.draftJournals.load(scope))
                assertEquals(1, server.posts.size)
            } finally {
                repository.close()
            }
        }

    @Test fun reviewRejectsWrongScopeIdAndRevokedReadWithoutPublishingBackup() =
        runTest {
            val preferences = SessionPreferences(InMemorySettings())
            val session = opened(preferences)
            val server = Server()
            val repository = BackendWorkspaceRepository(baseUrl, preferences, server.client())
            try {
                stage(repository, session)
                val draft = assertNotNull(repository.pendingDraft(session))
                assertFailsWith<IllegalStateException> { repository.reviewPendingDraft(session, "wrong") }
                assertFailsWith<IllegalStateException> { repository.reviewPendingDraft(session.copy(clientId = "other"), draft.id) }
                assertFailsWith<IllegalStateException> { repository.reviewPendingDraft(session.copy(userId = "other"), draft.id) }
                assertTrue(server.methods.isEmpty())
                server.denyReads = true
                val error = assertFailsWith<BackendHttpException> { repository.reviewPendingDraft(session, draft.id) }
                assertTrue(error.isWorkspaceAccessLoss)
                assertEquals(draft, repository.pendingDraft(session))
                assertTrue(server.posts.isEmpty())
            } finally {
                repository.close()
            }
        }
}
