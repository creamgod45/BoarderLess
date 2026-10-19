package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.data.persistence.DurableLocalRecord
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

private const val USER = "10000000-0000-4000-8000-000000000001"
private const val WORKSPACE = "30000000-0000-4000-8000-000000000001"

/** The real repository on top of fresh awaited storage: nothing here touches legacy Settings drafts. */
class FreshAwaitedRepositoryTest {
    private class Records : AwaitedDurableLocalRecordStore {
        val values = mutableMapOf<String, DurableLocalRecord>()
        override suspend fun read(key: String) = values[key]?.let { it.copy(payload = it.payload.copyOf()) }
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(values[key]?.generation == generation)
            values[key] = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(values[key]?.generation == generation) }
    }

    private val baseUrl = "https://qa.invalid"
    private val clientId = "20000000-0000-4000-8000-000000000001"
    private fun preferences() = SessionPreferences(InMemorySettings().also {
        it.putString("backend.userId", USER); it.putString("backend.clientId", clientId); it.putString("backend.workspaceId", WORKSPACE)
    })
    private fun session(preferences: SessionPreferences) =
        WorkspaceSession(USER, preferences.clientId, WorkspaceMemberRole.Owner, 8, 19, Workspace(WorkspaceId(WORKSPACE), "Fixture"))
    private val create = CreateObjectsOperation("create", listOf(TextNode(CanvasObjectId("node"),
        transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)), text = "Draft")))
    private val edit = EditTextOperation("edit", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))

    private fun repository(files: Records, prefs: SessionPreferences, server: Server) = BackendWorkspaceRepository(
        baseUrl = baseUrl, preferences = prefs, client = server.client(),
        awaitedSubmissionRuntime = FreshAwaitedServerStorage(files, "$baseUrl/api/v1") {}.runtime)

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
                    assertEquals(USER, request.headers["x-user-id"])
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
                                                USER,
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
                                        WorkspaceStateDto(WORKSPACE, version, seq, objects.toList(), emptyList()),
                                    )
                                }

                                request.url.encodedPath.endsWith("/operations") -> {
                                    "{}"
                                }

                                // Legacy response deliberately requires state refresh.
                                else -> {
                                    Json.encodeToString(
                                        WorkspaceDto.serializer(),
                                        WorkspaceDto(WORKSPACE, USER, "Fixture", version, seq, 1, role),
                                    )
                                }
                            }
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    @Test fun draftsSurviveAReopenedRepositoryAndAnAcknowledgedHeadLeavesTheTail() = runTest {
        val files = Records(); val prefs = preferences(); val session = session(prefs)
        val server = Server(loseFirstResponse = false)
        val first = repository(files, prefs, server)
        try {
            first.retainDraftAwaited(session, session.workspace, create)
            val after = (create.applyTo(session.workspace) as OperationResult.Applied).workspace
            first.retainDraftAwaited(session, after, edit)
            assertEquals(2, first.pendingDraft(session)?.operationCount)
            assertIs<SubmitOutcome.Accepted>(first.submit(session, create))
        } finally { first.close() }
        assertEquals(1, server.posts.size)
        val reopened = repository(files, prefs, Server())
        try {
            // The acknowledged head is gone; the saved tail is still there after a "restart".
            assertNull(reopened.pendingChange(session))
            assertEquals(1, reopened.pendingDraft(session)?.operationCount)
        } finally { reopened.close() }
    }

    @Test fun aLostResponseKeepsTheExactWireAcrossARestartWithoutNewSequences() = runTest {
        val files = Records(); val prefs = preferences(); val session = session(prefs)
        val server = Server(loseFirstResponse = true)
        val first = repository(files, prefs, server)
        try {
            first.retainDraftAwaited(session, session.workspace, create)
            assertFails { first.submit(session, create) }
            assertNotNull(first.pendingChange(session))
        } finally { first.close() }
        val second = repository(files, prefs, server)
        try {
            val pending = assertNotNull(second.pendingChange(session))
            second.retryPendingChange(session, pending.transactionId)
            assertNull(second.pendingChange(session))
            assertEquals(2, server.posts.size)
            assertEquals(server.posts.first(), server.posts.last()) // Same transaction and sequence numbers.
        } finally { second.close() }
    }

    @Test fun anIdentityOutsideTheServerOrMalformedIsRejectedBeforeAnyWrite() = runTest {
        val files = Records(); val prefs = preferences()
        val repo = repository(files, prefs, Server())
        try {
            val bad = session(prefs).copy(userId = "actor")
            assertFails { repo.retainDraftAwaited(bad, bad.workspace, create) }
            assertTrue(files.values.isEmpty())
        } finally { repo.close() }
    }
}
