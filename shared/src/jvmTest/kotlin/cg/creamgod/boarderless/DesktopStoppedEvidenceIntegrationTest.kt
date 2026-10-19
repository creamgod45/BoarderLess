package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*

class DesktopStoppedEvidenceIntegrationTest {
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-stopped-evidence-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test fun actualRepositorySavesCompleteEvidenceAndDraftTogetherOrReportsUnknownPublication() {
        for ((fault, fenced) in listOf(
            null to false,
            null to true,
            AtomicDraftStage.DataForced to false,
            AtomicDraftStage.Published to false,
            AtomicDraftStage.DirectoryForced to false,
        )) {
            fixture { root ->
                runTest {
                    var inject = false
                    val files = DesktopAtomicDraftStore(root) { if (inject && it == fault) error("Injected storage boundary") }
                    val store = DesktopDraftScopeBundleStore(files)
                    val prefs = SessionPreferences(InMemorySettings()).apply { draftPersistence = DesktopWorkspaceDraftPersistence(store) }
                    val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", prefs.clientId, "workspace")
                    val baseline = Workspace(WorkspaceId("workspace"), "Fixture")
                    val session = WorkspaceSession("actor", prefs.clientId, WorkspaceMemberRole.Editor, 8, 19, baseline)
                    val head =
                        CreateObjectsOperation(
                            "head",
                            listOf(
                                TextNode(
                                    CanvasObjectId("node"),
                                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                                    text = "Draft",
                                ),
                            ),
                        )
                    val tail = EditTextOperation("tail", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))
                    lateinit var wire: SubmitOperationsRequest
                    var race = false
                    val calls = mutableListOf<String>()
                    val client =
                        HttpClient(
                            MockEngine { req ->
                                calls += "${req.method.value} ${req.url.encodedPath}"
                                if (req.method == HttpMethod.Post && req.url.encodedPath.endsWith("/operations")) {
                                    wire = Json.decodeFromString((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                    error("Lost original response")
                                }
                                val body =
                                    when {
                                        req.url.encodedPath.endsWith("/receipts") -> {
                                            val receipt =
                                                TransactionReceiptDto(
                                                    wire.transactionId,
                                                    "actor",
                                                    prefs.clientId,
                                                    9,
                                                    20,
                                                    19 + wire.operations.size.toLong(),
                                                    "2026-10-05T00:00:00Z",
                                                    wire.operations.mapIndexed { i, op ->
                                                        ReceiptOperationDto(op.operationId, op.clientSeq, 20L + i, op.kind)
                                                    },
                                                )
                                            Json.encodeToString(
                                                if (fenced) {
                                                    SubmissionReceiptResponseDto(
                                                        "workspace",
                                                        21,
                                                        10,
                                                        emptyList(),
                                                        listOf(ReceiptLookupDto("transaction", wire.transactionId, "fenced")),
                                                    )
                                                } else {
                                                    SubmissionReceiptResponseDto(
                                                        "workspace",
                                                        21,
                                                        10,
                                                        listOf(receipt),
                                                        listOf(
                                                            ReceiptLookupDto(
                                                                "transaction",
                                                                wire.transactionId,
                                                                "committed",
                                                                wire.transactionId,
                                                                9,
                                                            ),
                                                        ),
                                                    )
                                                },
                                            )
                                        }

                                        req.url.encodedPath.endsWith("/operations") -> {
                                            Json.encodeToString(
                                                FullCatchUpOperationsDto(
                                                    wire.operations.mapIndexed { i, op ->
                                                        CommittedWorkspaceOperationDto(
                                                            20L + i,
                                                            op.operationId,
                                                            wire.transactionId,
                                                            "actor",
                                                            prefs.clientId,
                                                            op.clientSeq,
                                                            wire.baseVersion,
                                                            9,
                                                            op.kind,
                                                            op.payload,
                                                            1,
                                                            "2026-10-05T00:00:00Z",
                                                        )
                                                    },
                                                    21,
                                                    false,
                                                ),
                                            )
                                        }

                                        req.url.encodedPath.endsWith("/state") -> {
                                            if (race) {
                                                val before = store.read(scope)!!
                                                store.update(scope, before.generation) { requireNotNull(it) }
                                            }
                                            Json.encodeToString(WorkspaceStateDto("workspace", 10, 21, emptyList()))
                                        }

                                        else -> {
                                            Json.encodeToString(WorkspaceDto("workspace", "actor", "Latest", 10, 21, 1, "viewer"))
                                        }
                                    }
                                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
                    val repository = BackendWorkspaceRepository("https://qa.invalid", prefs, client)
                    try {
                        assertTrue(repository.supportsAtomicStoppedEvidence)
                        repository.retainDraft(session, baseline, head)
                        val after = (head.applyTo(baseline) as OperationResult.Applied).workspace
                        repository.retainDraft(session, after, tail)
                        assertFails { repository.submit(session, head) }
                        prefs.draftPersistence.stop(scope, wire.transactionId)
                        val stopped = store.read(scope)!!
                        assertFalse(repository.stoppedChanges(session).single().hasSavedEvidence)
                        val settingsBefore = prefs.clientSequence
                        calls.clear()
                        inject = fault != null
                        if (fault == null) {
                            val result = repository.saveStoppedSubmissionEvidence(session, wire.transactionId)
                            assertEquals(if (fenced) PendingReceiptStatus.Fenced else PendingReceiptStatus.Committed, result.status)
                            assertEquals(WorkspaceMemberRole.Viewer, result.current.role)
                        } else {
                            assertFails { repository.saveStoppedSubmissionEvidence(session, wire.transactionId) }
                        }
                        inject = false
                        val reopened = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).read(scope)!!
                        assertEquals(stopped.bundle!!.journal, reopened.bundle!!.journal)
                        assertEquals(stopped.bundle.stoppedPending, reopened.bundle.stoppedPending)
                        assertNull(reopened.bundle.pending)
                        assertTrue(reopened.bundle.journal!!.quarantined)
                        assertEquals(listOf(head, tail), reopened.bundle.journal.operations)
                        assertEquals(settingsBefore, prefs.clientSequence)
                        assertEquals(1, calls.count { it.startsWith("POST ") })
                        assertTrue(calls.single { it.startsWith("POST ") }.endsWith("/receipts"))
                        if (fenced) assertFalse(calls.any { it.endsWith("/operations") })
                        if (fault == AtomicDraftStage.DataForced) {
                            assertEquals(stopped, reopened)
                        } else {
                            assertEquals(stopped.generation + 1, reopened.generation)
                            assertEquals(2, reopened.bundle.version)
                            assertEquals(wire, reopened.bundle.stoppedPending!!.request)
                            assertNotNull(reopened.bundle.stoppedEvidence).validateAgainst(reopened.bundle.stoppedPending)
                        }
                        assertEquals(fault != AtomicDraftStage.DataForced, repository.stoppedChanges(session).single().hasSavedEvidence)
                        // Network-time generation drift cannot be overwritten, even if the bytes match.
                        val beforeRace = store.read(scope)!!
                        race = true
                        assertFailsWith<AtomicDraftConflictException> {
                            repository.saveStoppedSubmissionEvidence(
                                session,
                                wire.transactionId,
                            )
                        }
                        assertEquals(beforeRace.bundle, store.read(scope)!!.bundle)
                        assertEquals(beforeRace.generation + 1, store.read(scope)!!.generation)
                    } finally {
                        repository.close()
                    }
                }
            }
        }
    }
}
