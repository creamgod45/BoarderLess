package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendCatchUpReplayTest {
    private val initial = WorkspaceStateDto("workspace", 0, 0, emptyList())
    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject
    private val finalState = WorkspaceStateDto("workspace", 2, 2, listOf(CanvasObjectDto("node", "text", 2,
        zIndex = 0, locked = false, transform = obj("{}"),
        properties = obj("""{"text":"Updated","extra":"retain"}"""))))
    private val page = FullCatchUpOperationsDto(listOf(
        CommittedWorkspaceOperationDto(1, "create", "reused-tx", "actor", "peer", 10, 0, 1,
            "create_object", obj("""{"objectId":"node","objectType":"text","properties":{"text":"Draft","extra":"retain"}}"""), 1, "today"),
        CommittedWorkspaceOperationDto(2, "update", "reused-tx", "actor", "peer", 99, 0, 2,
            "update_object", obj("""{"objectId":"node","properties":{"text":"Updated"}}"""), 1, "today"),
    ), 2, false)
    private fun preferences() = SessionPreferences(InMemorySettings()).apply {
        userId = "actor"; workspaceId = "workspace"; clientSequence = 40
    }
    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun refreshReplaysCompleteTransactionsThenReusesRawProjectionForMetadataOnlyRefresh() = runTest {
        var stateReads = 0
        var operationReads = 0
        val preferences = preferences()
        val client = client(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/state") -> {
                    stateReads++; respond(Json.encodeToString(initial), headers = headers)
                }
                request.url.encodedPath.endsWith("/operations") -> {
                    operationReads++
                    assertEquals("1000", request.url.parameters["limit"])
                    assertEquals(if (operationReads == 1) "0" else "2", request.url.parameters["afterSeq"])
                    respond(Json.encodeToString(if (operationReads == 1) page else FullCatchUpOperationsDto(emptyList(), 2, false)), headers = headers)
                }
                else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Title-$operationReads",
                    2, 2, 1, if (operationReads == 0) "owner" else "viewer")), headers = headers)
            }
        })
        try {
            val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences, client)
            val opened = repo.openOrCreateWorkspace()
            val caughtUp = repo.refresh(opened)
            assertEquals(finalState.toDomainWorkspace("Title-0"), caughtUp.workspace)
            assertEquals(2L, caughtUp.lastServerSeq)
            assertEquals(1, stateReads)
            val metadataOnly = repo.refresh(caughtUp)
            assertEquals("Title-1", metadataOnly.workspace.title)
            assertEquals(WorkspaceMemberRole.Viewer, metadataOnly.role)
            assertEquals(1, stateReads)
            assertEquals(40L, preferences.clientSequence)
        } finally { client.close() }
    }

    @Test fun partialGapUnknownKindOrActivitySummaryFallsBackToAuthoritativeState() = runTest {
        val responses = listOf(
            Json.encodeToString(FullCatchUpOperationsDto(emptyList(), 0, false)),
            Json.encodeToString(page.copy(hasMore = true)),
            Json.encodeToString(page.copy(operations = page.operations.drop(1))),
            Json.encodeToString(page.copy(operations = listOf(page.operations.first(), page.operations.last().copy(operationType = "future")))),
            """{"operations":[{"serverSeq":1,"actorId":"actor","clientId":"peer","workspaceVersion":1,"operationType":"create_object","committedAt":"today"}],"lastServerSeq":2,"hasMore":false}""",
        )
        for (body in responses) {
            var stateReads = 0
            val client = client(MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/state") -> {
                        stateReads++; respond(Json.encodeToString(if (stateReads == 1) initial else finalState), headers = headers)
                    }
                    request.url.encodedPath.endsWith("/operations") -> respond(body, headers = headers)
                    else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Board", 2, 2, 1, "owner")), headers = headers)
                }
            })
            try {
                val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences(), client)
                val opened = repo.openOrCreateWorkspace()
                val refreshed = repo.refresh(opened)
                assertEquals(2, stateReads)
                assertEquals(finalState.toDomainWorkspace("Board"), refreshed.workspace)
                assertEquals(2L, refreshed.lastServerSeq)
                assertTrue(opened.workspace.objects.isEmpty())
            } finally { client.close() }
        }
    }

    @Test fun optimisticSessionCannotReuseAuthoritativeRawCache() = runTest {
        var stateReads = 0
        val client = client(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/state") -> {
                    stateReads++; respond(Json.encodeToString(if (stateReads == 1) initial else finalState), headers = headers)
                }
                request.url.encodedPath.endsWith("/operations") -> respond(Json.encodeToString(page), headers = headers)
                else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Board", 2, 2, 1, "owner")), headers = headers)
            }
        })
        try {
            val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences(), client)
            val opened = repo.openOrCreateWorkspace()
            val optimistic = opened.copy(workspace = opened.workspace.copy(title = "Unconfirmed draft"))
            assertEquals(finalState.toDomainWorkspace("Board"), repo.refresh(optimistic).workspace)
            assertEquals(2, stateReads)
        } finally { client.close() }
    }

    @Test fun accessDeniedDoesNotBecomeSnapshotFallbackOrCachedSuccess() = runTest {
        var stateReads = 0
        val client = client(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/state") -> {
                    stateReads++; respond(Json.encodeToString(initial), headers = headers)
                }
                request.url.encodedPath.endsWith("/operations") -> respond("{}", HttpStatusCode.Forbidden, headers)
                else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Board", 0, 0, 1, "owner")), headers = headers)
            }
        })
        try {
            val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences(), client)
            val opened = repo.openOrCreateWorkspace()
            assertEquals(HttpStatusCode.Forbidden, assertFailsWith<BackendHttpException> { repo.refresh(opened) }.status)
            assertEquals(1, stateReads)
        } finally { client.close() }
    }

    @Test fun operationDeduplicationUsesActorScopeNotClientOrGlobalId() = runTest {
        for ((actor, peerClient, expectedStateReads) in listOf(
            Triple("other-actor", "peer", 1),
            Triple("actor", "other-client", 2),
            Triple("actor", "peer", 2),
        )) {
            var stateReads = 0
            val second = page.operations.last().copy(actorId = actor, clientId = peerClient,
                operationId = page.operations.first().operationId)
            val response = page.copy(operations = listOf(page.operations.first(), second))
            val client = client(MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/state") -> {
                        stateReads++
                        respond(Json.encodeToString(if (stateReads == 1) initial else finalState), headers = headers)
                    }
                    request.url.encodedPath.endsWith("/operations") -> respond(Json.encodeToString(response), headers = headers)
                    else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Board", 2, 2, 1, "owner")), headers = headers)
                }
            })
            try {
                val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences(), client)
                val opened = repo.openOrCreateWorkspace()
                assertEquals(finalState.toDomainWorkspace("Board"), repo.refresh(opened).workspace)
                assertEquals(expectedStateReads, stateReads)
                assertEquals(0L, opened.lastServerSeq)
                assertTrue(opened.workspace.objects.isEmpty())
            } finally { client.close() }
        }
    }

    @Test fun transactionSplitAcrossPagesIsReplayedOnceAndNextPageDenialDoesNotPublishHead() = runTest {
        for (denyNext in listOf(false, true)) {
            var stateReads = 0
            val cursors = mutableListOf<String?>()
            val records = page.operations.map { it.copy(workspaceVersion = 1) }
            val client = client(MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/state") -> {
                        stateReads++; respond(Json.encodeToString(initial), headers = headers)
                    }
                    request.url.encodedPath.endsWith("/operations") -> {
                        val cursor = request.url.parameters["afterSeq"]
                        cursors.add(cursor)
                        if (cursor == "0") respond(Json.encodeToString(FullCatchUpOperationsDto(records.take(1), 2, true)), headers = headers)
                        else if (denyNext) respond("{}", HttpStatusCode.Forbidden, headers)
                        else respond(Json.encodeToString(FullCatchUpOperationsDto(records.drop(1), 2, false)), headers = headers)
                    }
                    else -> respond(Json.encodeToString(WorkspaceDto("workspace", "actor", "Board", 1, 2, 1, "owner")), headers = headers)
                }
            })
            try {
                val repo = BackendWorkspaceRepository("https://qa.example.invalid", preferences(), client)
                val opened = repo.openOrCreateWorkspace()
                if (denyNext) {
                    assertEquals(HttpStatusCode.Forbidden, assertFailsWith<BackendHttpException> { repo.refresh(opened) }.status)
                    assertEquals(0L, opened.lastServerSeq)
                } else {
                    val refreshed = repo.refresh(opened)
                    assertEquals(finalState.copy(workspaceVersion = 1).toDomainWorkspace("Board"), refreshed.workspace)
                    assertEquals(2L, refreshed.lastServerSeq)
                }
                assertEquals(listOf<String?>("0", "1"), cursors)
                assertEquals(1, stateReads)
                assertTrue(opened.workspace.objects.isEmpty())
            } finally { client.close() }
        }
    }
}
