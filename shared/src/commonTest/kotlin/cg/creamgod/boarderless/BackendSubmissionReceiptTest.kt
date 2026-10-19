package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendSubmissionReceiptTest {
    @Test fun defaultSettingsCannotSaveAtomicEvidenceOrDispatchAnUnadvertisedSave() =
        runTest {
            val memory = InMemorySettings()
            val prefs = SessionPreferences(memory)
            val session =
                WorkspaceSession(
                    "actor",
                    prefs.clientId,
                    WorkspaceMemberRole.Owner,
                    4,
                    10,
                    Workspace(WorkspaceId("workspace"), "QA"),
                )
            val before = memory.keys.associateWith(memory::getStringOrNull)
            var calls = 0
            val repository =
                BackendWorkspaceRepository(
                    "http://fixture",
                    prefs,
                    HttpClient(
                        MockEngine {
                            calls++
                            error("No HTTP")
                        },
                    ),
                )
            try {
                assertFalse(repository.supportsAtomicStoppedEvidence)
                assertFails { repository.saveStoppedSubmissionEvidence(session, "transaction") }
                assertEquals(0, calls)
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            } finally {
                repository.close()
            }
        }

    @Test fun normalizedPayloadKeepsSuppliedValuesButMayFillDefaults() {
        val sent = Json.parseToJsonElement("""{"transform":{"x":1.0},"items":[{"id":"n","size":2}]}""")
        val normalized = Json.parseToJsonElement("""{"transform":{"x":1,"y":0},"items":[{"id":"n","size":2.0,"extra":true}]}""")
        assertTrue(normalized.preservesSubmittedValues(sent))
        for (bad in listOf(
            """{"transform":{"x":"1"},"items":[{"id":"n","size":2}]}""",
            """{"transform":{"x":2},"items":[{"id":"n","size":2}]}""",
            """{"transform":{"x":1},"items":[]}""",
        )) {
            assertFalse(Json.parseToJsonElement(bad).preservesSubmittedValues(sent))
        }
    }

    private val request =
        SubmitOperationsRequest(
            clientId = "client",
            transactionId = "transaction",
            baseVersion = 4,
            operations =
                listOf(
                    OperationDto("op1", 8, "delete_relations", payload = buildJsonObject { put("relationIds", JsonArray(emptyList())) }),
                    OperationDto("op2", 9, "delete_objects", payload = buildJsonObject { put("objectIds", JsonArray(emptyList())) }),
                ),
        )
    private val receipt =
        TransactionReceiptDto(
            "transaction",
            "actor",
            "client",
            6,
            12,
            13,
            "2026-10-05T00:00:00Z",
            request.operations.mapIndexed {
                i,
                op,
                ->
                ReceiptOperationDto(op.operationId, op.clientSeq, 12L + i, op.kind)
            },
        )
    private val response =
        SubmissionReceiptResponseDto(
            "workspace",
            15,
            7,
            listOf(receipt),
            listOf(ReceiptLookupDto("transaction", "transaction", "committed", "transaction", 6)),
        )

    @Test fun stoppedReceiptRemainsQueryableAfterRecreationWithoutAnyRecoveryWrites() =
        runTest {
            for (mode in listOf("committed", "fenced", "unknown", "actor", "scope", "operation", "denied", "malformed")) {
                val memory = InMemorySettings()
                val initial = SessionPreferences(memory)
                val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", initial.clientId, "workspace")
                val archived = PendingWorkspaceSubmission(scope, "local", request.copy(clientId = initial.clientId))
                initial.draftPersistence.stage(archived)
                initial.draftPersistence.stop(scope, "transaction")
                val prefs = SessionPreferences(memory)
                // A different active wire must neither hide nor replace the stopped original.
                val active = archived.copy(request = archived.request.copy(transactionId = "active"))
                prefs.draftPersistence.stage(active)
                val session =
                    WorkspaceSession(
                        "actor",
                        prefs.clientId,
                        WorkspaceMemberRole.Viewer,
                        4,
                        10,
                        Workspace(WorkspaceId("workspace"), "QA"),
                    )
                val before = memory.keys.associateWith(memory::getStringOrNull)
                var calls = 0
                val client =
                    HttpClient(
                        MockEngine { req ->
                            calls++
                            assertEquals(HttpMethod.Post, req.method)
                            assertEquals("/api/v1/workspaces/workspace/operations/receipts", req.url.encodedPath)
                            assertEquals("actor", req.headers["x-user-id"])
                            assertEquals(
                                ReceiptQueryDto(listOf("transaction")),
                                Json.decodeFromString<ReceiptQueryDto>((req.body as TextContent).text),
                            )
                            val result =
                                if (mode == "fenced" || mode == "unknown") {
                                    response.copy(
                                        receipts = emptyList(),
                                        lookups = listOf(ReceiptLookupDto("transaction", "transaction", mode)),
                                    )
                                } else {
                                    response.copy(
                                        workspaceId = if (mode == "scope") "other" else "workspace",
                                        receipts =
                                            listOf(
                                                receipt.copy(
                                                    clientId = prefs.clientId,
                                                    actorId = if (mode == "actor") "other" else "actor",
                                                    operations =
                                                        if (mode ==
                                                            "operation"
                                                        ) {
                                                            receipt.operations.drop(1)
                                                        } else {
                                                            receipt.operations
                                                        },
                                                ),
                                            ),
                                    )
                                }
                            respond(
                                if (mode == "malformed") "{" else Json.encodeToString(result),
                                if (mode == "denied") HttpStatusCode.Forbidden else HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
                val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
                try {
                    assertEquals(listOf(StoppedWorkspaceChange("transaction", 2)), repository.stoppedChanges(session))
                    assertFails { repository.inspectStoppedReceipt(session, "active") }
                    assertFails { repository.inspectStoppedReceipt(session.copy(clientId = "other"), "transaction") }
                    assertFails { repository.inspectStoppedReceipt(session.copy(userId = "other"), "transaction") }
                    assertEquals(0, calls)
                    if (mode in listOf("committed", "fenced", "unknown")) {
                        assertEquals(
                            when (mode) {
                                "committed" -> PendingReceiptStatus.Committed
                                "fenced" -> PendingReceiptStatus.Fenced
                                else -> PendingReceiptStatus.Unknown
                            },
                            repository.inspectStoppedReceipt(session, "transaction").status,
                        )
                    } else {
                        assertFails { repository.inspectStoppedReceipt(session, "transaction") }
                    }
                    assertEquals(1, calls)
                    assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
                    assertEquals(active, prefs.draftPersistence.pending(scope))
                    assertEquals(archived, prefs.pendingSubmissions.loadStopped(scope, "transaction"))
                } finally {
                    repository.close()
                }
            }
        }

    @Test fun stoppedReceiptCancellationRetainsArchiveWithoutPublishingAnOutcome() =
        runTest {
            val memory = InMemorySettings()
            val prefs = SessionPreferences(memory)
            val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
            val archived = PendingWorkspaceSubmission(scope, "local", request.copy(clientId = prefs.clientId))
            prefs.pendingSubmissions.archiveStopped(archived)
            val session =
                WorkspaceSession(
                    "actor",
                    prefs.clientId,
                    WorkspaceMemberRole.Owner,
                    4,
                    10,
                    Workspace(WorkspaceId("workspace"), "QA"),
                )
            val before = memory.keys.associateWith(memory::getStringOrNull)
            val entered = CompletableDeferred<Unit>()
            var returned = false
            val client =
                HttpClient(
                    MockEngine {
                        entered.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    },
                ) { install(ContentNegotiation) { json() } }
            val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
            try {
                val job =
                    launch {
                        repository.inspectStoppedReceipt(session, "transaction")
                        returned = true
                    }
                entered.await()
                job.cancelAndJoin()
                assertFalse(returned)
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
                assertEquals(archived, prefs.pendingSubmissions.loadStopped(scope, "transaction"))
            } finally {
                repository.close()
            }
        }

    @Test fun explicitFenceKeepsWireAndAcceptsCommitWinningTheRace() =
        runTest {
            for (mode in listOf("fenced", "committed", "unknown", "scope", "denied", "malformed", "cancel")) {
                val settings = InMemorySettings()
                val prefs = SessionPreferences(settings)
                val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
                val pending = PendingWorkspaceSubmission(scope, "local", request.copy(clientId = prefs.clientId))
                prefs.draftPersistence.stage(pending)
                val session =
                    WorkspaceSession(
                        "actor",
                        prefs.clientId,
                        WorkspaceMemberRole.Owner,
                        4,
                        10,
                        Workspace(WorkspaceId("workspace"), "QA"),
                    )
                var calls = 0
                val entered = CompletableDeferred<Unit>()
                val client =
                    HttpClient(
                        MockEngine { req ->
                            calls++
                            assertEquals(PendingFenceAttemptStore.State.Unconfirmed, prefs.fenceAttempts.state(pending))
                            assertEquals(HttpMethod.Post, req.method)
                            assertEquals("/api/v1/workspaces/workspace/operations/fences", req.url.encodedPath)
                            assertEquals("actor", req.headers["x-user-id"])
                            assertEquals(
                                ReceiptQueryDto(listOf("transaction")),
                                Json.decodeFromString<ReceiptQueryDto>((req.body as TextContent).text),
                            )
                            if (mode == "cancel") {
                                entered.complete(Unit)
                                kotlinx.coroutines.awaitCancellation()
                            }
                            val result =
                                if (mode == "committed") {
                                    response.copy(receipts = listOf(receipt.copy(clientId = prefs.clientId)))
                                } else {
                                    response.copy(
                                        workspaceId = if (mode == "scope") "other" else "workspace",
                                        receipts = emptyList(),
                                        lookups =
                                            listOf(
                                                ReceiptLookupDto(
                                                    "transaction",
                                                    "transaction",
                                                    if (mode ==
                                                        "unknown"
                                                    ) {
                                                        "unknown"
                                                    } else {
                                                        "fenced"
                                                    },
                                                ),
                                            ),
                                    )
                                }
                            respond(
                                if (mode == "malformed") "{" else Json.encodeToString(result),
                                if (mode == "denied") HttpStatusCode.Forbidden else HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
                val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
                assertFails { repository.fencePendingChange(session.copy(role = WorkspaceMemberRole.Viewer), "transaction") }
                assertFails { repository.fencePendingChange(session, "other") }
                assertEquals(0, calls)
                if (mode == "fenced" || mode == "committed") {
                    assertEquals(
                        if (mode == "fenced") PendingReceiptStatus.Fenced else PendingReceiptStatus.Committed,
                        repository.fencePendingChange(session, "transaction").status,
                    )
                } else if (mode == "cancel") {
                    val job = launch { repository.fencePendingChange(session, "transaction") }
                    entered.await()
                    job.cancelAndJoin()
                } else {
                    assertFails { repository.fencePendingChange(session, "transaction") }
                }
                assertEquals(1, calls)
                assertEquals(pending, prefs.draftPersistence.pending(scope))
                assertEquals(0L, prefs.clientSequence)
                repository.close()
                val reopened = SessionPreferences(settings)
                assertEquals(
                    if (mode == "fenced") {
                        PendingFenceAttemptStore.State.Fenced
                    } else if (mode == "committed") {
                        PendingFenceAttemptStore.State.Committed
                    } else {
                        PendingFenceAttemptStore.State.Unconfirmed
                    },
                    reopened.fenceAttempts.state(pending),
                )
                var resendCalls = 0
                val retryClient =
                    HttpClient(
                        MockEngine {
                            resendCalls++
                            error("Must not resend")
                        },
                    ) {
                        install(ContentNegotiation) { json() }
                    }
                val retry = BackendWorkspaceRepository("http://fixture", reopened, retryClient)
                assertEquals(true, retry.pendingChange(session)?.resendBlocked)
                assertFails { retry.retryPendingSubmission(session) }
                assertEquals(0, resendCalls)
                assertEquals(pending, reopened.draftPersistence.pending(scope))
                retry.close()
            }
        }

    @Test fun fenceBarrierSaveFailureStopsHttpAndCorruptOrChangedWireFailsClosed() =
        runTest {
            val backing = InMemorySettings()
            val settings =
                object : com.russhwolf.settings.Settings by backing {
                    override fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (!key.startsWith("fence1.")) backing.putString(key, value)
                    }
                }
            val prefs = SessionPreferences(settings)
            val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
            val pending = PendingWorkspaceSubmission(scope, "local", request.copy(clientId = prefs.clientId))
            prefs.draftPersistence.stage(pending)
            val session =
                WorkspaceSession(
                    "actor",
                    prefs.clientId,
                    WorkspaceMemberRole.Owner,
                    4,
                    10,
                    Workspace(WorkspaceId("workspace"), "QA"),
                )
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        error("Save failure must precede HTTP")
                    },
                ) {
                    install(ContentNegotiation) { json() }
                }
            val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
            assertFails { repository.fencePendingChange(session, "transaction") }
            assertEquals(0, calls)
            assertEquals(pending, prefs.draftPersistence.pending(scope))
            repository.close()
            val store = PendingFenceAttemptStore(backing)
            store.begin(pending)
            store.observe(pending, PendingReceiptStatus.Unknown)
            assertEquals(PendingFenceAttemptStore.State.Unconfirmed, store.state(pending))
            assertFails { store.state(pending.copy(request = pending.request.copy(baseVersion = 3))) }
            assertNull(store.state(pending.copy(scope = scope.copy(userId = "other"))))
            store.observe(pending, PendingReceiptStatus.Fenced)
            assertFails { store.begin(pending) }
            assertFails { store.observe(pending, PendingReceiptStatus.Committed) }
            backing.putString(backing.keys.single { it.startsWith("fence1.") }, "{corrupt}")
            assertFails { store.state(pending) }
        }

    @Test fun exactWholeTransactionAndUnknownOrFencedHaveDistinctSemantics() {
        assertEquals(PendingReceiptStatus.Committed, response.validatePendingReceipt("workspace", "actor", request).status)
        for (status in listOf("unknown", "fenced")) {
            val resolved =
                response
                    .copy(receipts = emptyList(), lookups = listOf(ReceiptLookupDto("transaction", "transaction", status)))
                    .validatePendingReceipt("workspace", "actor", request)
            assertEquals(if (status == "unknown") PendingReceiptStatus.Unknown else PendingReceiptStatus.Fenced, resolved.status)
            assertNull(resolved.committedWorkspaceVersion)
        }
    }

    @Test fun wrongScopeIdentityOrderBoundaryAndLookupClaimsAreRejected() {
        val badReceipts =
            listOf(
                receipt.copy(actorId = "other"),
                receipt.copy(clientId = "other"),
                receipt.copy(transactionId = "other"),
                receipt.copy(workspaceVersion = 4),
                receipt.copy(toServerSeq = 16),
                receipt.copy(operations = receipt.operations.reversed()),
                receipt.copy(operations = receipt.operations.take(1)),
                receipt.copy(operations = listOf(receipt.operations[0], receipt.operations[0])),
                receipt.copy(operations = receipt.operations.map { it.copy(clientSeq = 80) }),
                receipt.copy(operations = receipt.operations.map { it.copy(kind = "create_object") }),
                receipt.copy(committedAt = ""),
            )
        for (r in badReceipts) {
            assertFailsWith<BackendContractException> {
                response.copy(receipts = listOf(r)).validatePendingReceipt("workspace", "actor", request)
            }
        }
        val badResponses =
            listOf(
                response.copy(workspaceId = "other"),
                response.copy(headServerSeq = -1),
                response.copy(headWorkspaceVersion = 5),
                response.copy(receipts = listOf(receipt, receipt)),
                response.copy(lookups = emptyList()),
                response.copy(lookups = response.lookups + response.lookups),
                response.copy(lookups = listOf(response.lookups.single().copy(type = "operation"))),
                response.copy(lookups = listOf(response.lookups.single().copy(status = "unknown"))),
                response.copy(lookups = listOf(response.lookups.single().copy(status = "future"))),
                response.copy(lookups = listOf(response.lookups.single().copy(workspaceVersion = 5))),
            )
        for (r in badResponses) assertFailsWith<BackendContractException> { r.validatePendingReceipt("workspace", "actor", request) }
    }

    @Test fun explicitSettlementNeedsFullMatchingRecordsAndFreshAclWithoutResending() =
        runTest {
            for (mode in listOf("valid", "unknown", "missing", "payload", "base", "denied")) {
                val prefs = SessionPreferences(InMemorySettings())
                val wire = request.copy(clientId = prefs.clientId)
                val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
                val pending = PendingWorkspaceSubmission(scope, "local", wire)
                prefs.draftPersistence.stage(pending)
                val workspace = Workspace(WorkspaceId("workspace"), "QA")
                val session = WorkspaceSession("actor", prefs.clientId, WorkspaceMemberRole.Owner, 4, 10, workspace)
                val calls = mutableListOf<String>()
                val client =
                    HttpClient(
                        MockEngine { req ->
                            calls += "${req.method.value} ${req.url.encodedPath}"
                            val body =
                                when {
                                    req.url.encodedPath.endsWith("/receipts") -> {
                                        Json.encodeToString(
                                            if (mode == "unknown") {
                                                response.copy(
                                                    receipts = emptyList(),
                                                    lookups = listOf(ReceiptLookupDto("transaction", "transaction", "unknown")),
                                                )
                                            } else {
                                                response.copy(receipts = listOf(receipt.copy(clientId = prefs.clientId)))
                                            },
                                        )
                                    }

                                    req.url.encodedPath.endsWith("/operations") -> {
                                        assertEquals("11", req.url.parameters["afterSeq"])
                                        assertEquals("200", req.url.parameters["limit"])
                                        var records =
                                            wire.operations.mapIndexed { index, op ->
                                                CommittedWorkspaceOperationDto(
                                                    12L + index,
                                                    op.operationId,
                                                    wire.transactionId,
                                                    "actor",
                                                    prefs.clientId,
                                                    op.clientSeq,
                                                    if (mode == "base") 3 else wire.baseVersion,
                                                    6,
                                                    op.kind,
                                                    if (mode == "payload") buildJsonObject { put("changed", true) } else op.payload,
                                                    1,
                                                    receipt.committedAt,
                                                )
                                            }
                                        if (mode == "missing") records = records.take(1)
                                        Json.encodeToString(FullCatchUpOperationsDto(records, 15, false))
                                    }

                                    req.url.encodedPath.endsWith("/state") -> {
                                        Json.encodeToString(WorkspaceStateDto("workspace", 7, 15, emptyList()))
                                    }

                                    else -> {
                                        Json.encodeToString(WorkspaceDto("workspace", "actor", "QA", 7, 15, 1, "owner"))
                                    }
                                }
                            respond(
                                body,
                                if (mode == "denied" && !req.url.encodedPath.endsWith("/receipts") &&
                                    !req.url.encodedPath.endsWith("/operations")
                                ) {
                                    HttpStatusCode.Forbidden
                                } else {
                                    HttpStatusCode.OK
                                },
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
                val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
                if (mode == "valid") {
                    val settled = repository.confirmPendingReceipt(session, "transaction")
                    assertEquals(7L, settled.workspaceVersion)
                    assertEquals(15L, settled.lastServerSeq)
                    assertNull(repository.pendingChange(session))
                    assertEquals(9L, prefs.clientSequence)
                } else {
                    assertFails { repository.confirmPendingReceipt(session, "transaction") }
                    assertEquals(pending, prefs.draftPersistence.pending(scope))
                }
                assertEquals(1, calls.count { it.startsWith("POST ") })
                assertFalse(calls.any { it.endsWith("/fences") })
                repository.close()
            }
        }

    @Test fun stoppedVerificationNeedsFullCommitOrFenceAndFreshStateWithoutSettlingRecovery() =
        runTest {
            for (mode in listOf(
                "valid",
                "fenced",
                "unknown",
                "missing",
                "payload",
                "base",
                "time",
                "duplicate",
                "head",
                "oversized",
                "denied",
                "stateScope",
                "stateStale",
            )) {
                val memory = InMemorySettings()
                val prefs = SessionPreferences(memory)
                val wire = request.copy(clientId = prefs.clientId)
                val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
                val archived = PendingWorkspaceSubmission(scope, "local", wire)
                prefs.pendingSubmissions.archiveStopped(archived)
                val active = archived.copy(request = wire.copy(transactionId = "active"))
                prefs.draftPersistence.stage(active)
                val session =
                    WorkspaceSession(
                        "actor",
                        prefs.clientId,
                        WorkspaceMemberRole.Owner,
                        4,
                        10,
                        Workspace(WorkspaceId("workspace"), "QA"),
                    )
                val before = memory.keys.associateWith(memory::getStringOrNull)
                val calls = mutableListOf<String>()
                val client =
                    HttpClient(
                        MockEngine { req ->
                            calls += "${req.method.value} ${req.url.encodedPath}"
                            val seq = if (mode == "stateStale") 14L else 15L
                            val body =
                                when {
                                    req.url.encodedPath.endsWith("/receipts") -> {
                                        assertEquals(
                                            ReceiptQueryDto(listOf("transaction")),
                                            Json.decodeFromString<ReceiptQueryDto>((req.body as TextContent).text),
                                        )
                                        Json.encodeToString(
                                            if (mode == "fenced" || mode == "unknown") {
                                                response.copy(
                                                    receipts = emptyList(),
                                                    lookups = listOf(ReceiptLookupDto("transaction", "transaction", mode)),
                                                )
                                            } else {
                                                response.copy(receipts = listOf(receipt.copy(clientId = prefs.clientId)))
                                            },
                                        )
                                    }

                                    req.url.encodedPath.endsWith("/operations") -> {
                                        assertEquals(HttpMethod.Get, req.method)
                                        assertEquals("11", req.url.parameters["afterSeq"])
                                        assertEquals("200", req.url.parameters["limit"])
                                        var records =
                                            wire.operations.mapIndexed { index, op ->
                                                CommittedWorkspaceOperationDto(
                                                    12L + index,
                                                    op.operationId,
                                                    wire.transactionId,
                                                    "actor",
                                                    prefs.clientId,
                                                    op.clientSeq,
                                                    if (mode == "base") 3 else wire.baseVersion,
                                                    6,
                                                    op.kind,
                                                    if (mode == "payload") buildJsonObject { put("changed", true) } else op.payload,
                                                    1,
                                                    if (mode == "time") "wrong" else receipt.committedAt,
                                                )
                                            }
                                        if (mode == "missing") records = records.take(1)
                                        if (mode == "duplicate") records = records + records.first()
                                        if (mode == "oversized") records = List(201) { records.first() }
                                        Json.encodeToString(FullCatchUpOperationsDto(records, if (mode == "head") 12 else 15, false))
                                    }

                                    req.url.encodedPath.endsWith("/state") -> {
                                        Json.encodeToString(
                                            WorkspaceStateDto(if (mode == "stateScope") "other" else "workspace", 7, seq, emptyList()),
                                        )
                                    }

                                    else -> {
                                        Json.encodeToString(WorkspaceDto("workspace", "actor", "Latest", 7, seq, 1, "viewer"))
                                    }
                                }
                            respond(
                                body,
                                if (mode == "denied" && req.method == HttpMethod.Get) HttpStatusCode.Forbidden else HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
                val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
                try {
                    assertFails { repository.verifyStoppedSubmission(session, "active") }
                    assertFails { repository.verifyStoppedSubmission(session.copy(clientId = "other"), "transaction") }
                    assertEquals(0, calls.size)
                    if (mode == "valid" || mode == "fenced") {
                        val verified = repository.verifyStoppedSubmission(session, "transaction")
                        assertEquals("transaction", verified.transactionId)
                        assertEquals(if (mode == "valid") PendingReceiptStatus.Committed else PendingReceiptStatus.Fenced, verified.status)
                        assertEquals(7L, verified.current.workspaceVersion)
                        assertEquals(15L, verified.current.lastServerSeq)
                        assertEquals(WorkspaceMemberRole.Viewer, verified.current.role)
                        assertEquals(if (mode == "valid") 6L else null, verified.committedWorkspaceVersion)
                        assertEquals(if (mode == "valid") 13L else null, verified.committedServerSeq)
                    } else {
                        assertFails { repository.verifyStoppedSubmission(session, "transaction") }
                    }
                    assertEquals(1, calls.count { it.startsWith("POST ") })
                    assertFalse(calls.any { it.endsWith("/fences") })
                    if (mode == "fenced" || mode == "unknown") assertFalse(calls.any { it.endsWith("/operations") })
                    if (mode == "unknown") assertEquals(1, calls.size)
                    assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
                    assertEquals(archived, prefs.pendingSubmissions.loadStopped(scope, "transaction"))
                    assertEquals(active, prefs.draftPersistence.pending(scope))
                } finally {
                    repository.close()
                }
            }
        }

    @Test fun stoppedVerificationCancelledDuringFreshStateReturnsNoVerifiedOutcome() =
        runTest {
            val memory = InMemorySettings()
            val prefs = SessionPreferences(memory)
            val wire = request.copy(clientId = prefs.clientId)
            val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
            val archived = PendingWorkspaceSubmission(scope, "local", wire)
            prefs.pendingSubmissions.archiveStopped(archived)
            val session =
                WorkspaceSession(
                    "actor",
                    prefs.clientId,
                    WorkspaceMemberRole.Owner,
                    4,
                    10,
                    Workspace(WorkspaceId("workspace"), "QA"),
                )
            val before = memory.keys.associateWith(memory::getStringOrNull)
            val entered = CompletableDeferred<Unit>()
            var returned = false
            val client =
                HttpClient(
                    MockEngine { req ->
                        val body =
                            when {
                                req.url.encodedPath.endsWith("/receipts") -> {
                                    Json.encodeToString(response.copy(receipts = listOf(receipt.copy(clientId = prefs.clientId))))
                                }

                                req.url.encodedPath.endsWith("/operations") -> {
                                    Json.encodeToString(
                                        FullCatchUpOperationsDto(
                                            wire.operations.mapIndexed { index, op ->
                                                CommittedWorkspaceOperationDto(
                                                    12L + index,
                                                    op.operationId,
                                                    wire.transactionId,
                                                    "actor",
                                                    prefs.clientId,
                                                    op.clientSeq,
                                                    wire.baseVersion,
                                                    6,
                                                    op.kind,
                                                    op.payload,
                                                    1,
                                                    receipt.committedAt,
                                                )
                                            },
                                            15,
                                            false,
                                        ),
                                    )
                                }

                                req.url.encodedPath.endsWith("/state") -> {
                                    entered.complete(Unit)
                                    kotlinx.coroutines.awaitCancellation()
                                }

                                else -> {
                                    Json.encodeToString(WorkspaceDto("workspace", "actor", "QA", 7, 15, 1, "owner"))
                                }
                            }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
            try {
                val job =
                    launch {
                        repository.verifyStoppedSubmission(session, "transaction")
                        returned = true
                    }
                entered.await()
                job.cancelAndJoin()
                assertFalse(returned)
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            } finally {
                repository.close()
            }
        }

    @Test fun repositoryLookupUsesKnownRouteAndExactScopeWithoutChangingRecovery() =
        runTest {
            val prefs = SessionPreferences(InMemorySettings())
            val wire = request.copy(clientId = prefs.clientId)
            val scope = PendingSubmissionScope("http://fixture/api/v1", "actor", prefs.clientId, "workspace")
            val pending = PendingWorkspaceSubmission(scope, "local", wire)
            prefs.draftPersistence.stage(pending)
            val workspace = Workspace(WorkspaceId("workspace"), "QA")
            val session = WorkspaceSession("actor", prefs.clientId, WorkspaceMemberRole.Owner, 4, 10, workspace)
            var calls = 0
            var mode = "committed"
            val client =
                HttpClient(
                    MockEngine { req ->
                        calls++
                        assertEquals("/api/v1/workspaces/workspace/operations/receipts", req.url.encodedPath)
                        assertEquals(HttpMethod.Post, req.method)
                        assertEquals("actor", req.headers["x-user-id"])
                        assertEquals(
                            ReceiptQueryDto(listOf("transaction")),
                            Json.decodeFromString<ReceiptQueryDto>((req.body as TextContent).text),
                        )
                        val body =
                            when (mode) {
                                "unknown", "fenced" -> {
                                    response.copy(
                                        receipts = emptyList(),
                                        lookups = listOf(ReceiptLookupDto("transaction", "transaction", mode)),
                                    )
                                }

                                "wrong-scope" -> {
                                    response.copy(workspaceId = "other")
                                }

                                else -> {
                                    response.copy(receipts = listOf(receipt.copy(clientId = prefs.clientId)))
                                }
                            }
                        respond(
                            if (mode == "malformed") "{" else Json.encodeToString(body),
                            if (mode == "denied") HttpStatusCode.Forbidden else HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
            assertEquals(PendingReceiptStatus.Committed, repository.inspectPendingReceipt(session, "transaction").status)
            assertEquals(pending, prefs.draftPersistence.pending(scope))
            assertEquals(1, calls)
            assertFails { repository.inspectPendingReceipt(session, "wrong") }
            assertFails { repository.inspectPendingReceipt(session.copy(userId = "other"), "transaction") }
            assertEquals(1, calls)
            for (next in listOf("unknown", "fenced", "wrong-scope", "malformed", "denied")) {
                mode = next
                if (next == "unknown" || next == "fenced") {
                    assertEquals(
                        if (next == "unknown") PendingReceiptStatus.Unknown else PendingReceiptStatus.Fenced,
                        repository.inspectPendingReceipt(session, "transaction").status,
                    )
                } else {
                    assertFails { repository.inspectPendingReceipt(session, "transaction") }
                }
                assertEquals(pending, prefs.draftPersistence.pending(scope))
                assertEquals("transaction", repository.pendingChange(session)?.transactionId)
            }
            assertEquals(6, calls) // Only receipt POSTs; no submit/fence/reload endpoint was touched.
            repository.close()
        }
}
