package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*

class DesktopDraftRepositoryIntegrationTest {
    private val baseline = Workspace(WorkspaceId("workspace"), "Fixture")
    private val head = CreateObjectsOperation("head", listOf(TextNode(CanvasObjectId("node"),
        transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)), text = "Draft")))
    private val tail = EditTextOperation("tail", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))
    private fun session(preferences: SessionPreferences) = WorkspaceSession("actor", preferences.clientId,
        WorkspaceMemberRole.Editor, 8, 19, baseline)
    private fun scope(session: WorkspaceSession) = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", session.clientId, "workspace")
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-repository-atomic-test-").toRealPath()
        try { action(root) }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private fun ack(request: SubmitOperationsRequest) = AcceptedOperationsDto("accepted", 9, 20, 20,
        request.operations.map { CommittedWorkspaceOperationDto(20, it.operationId, request.transactionId, "actor",
            request.clientId, it.clientSeq, request.baseVersion, 9, it.kind, it.payload, 1, "2026-10-05T00:00:00Z") })

    @Test fun actualRepositoryPersistsBeforeTransportAndAckKeepsQueuedTail() = fixture { root -> runTest {
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        val persistence = DesktopWorkspaceDraftPersistence(store)
        val preferences = SessionPreferences(InMemorySettings()).apply { draftPersistence = persistence }
        val session = session(preferences)
        val ledger = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
        ledger.initialize(scope(session), 40)
        preferences.sequenceAllocator = ledger
        var posts = 0
        val repository = BackendWorkspaceRepository("https://qa.invalid", preferences, client { http ->
            posts++
            val request = Json.decodeFromString<SubmitOperationsRequest>((http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            val persisted = store.read(scope(session))!!.bundle!!
            assertEquals(request, persisted.pending!!.request)
            assertEquals(41L, request.operations.single().clientSeq)
            assertEquals(41L, ledger.read(scope(session))!!.highWater)
            assertEquals(request.transactionId, persisted.journal!!.headTransactionId)
            val after = (head.applyTo(baseline) as OperationResult.Applied).workspace
            persistence.append(scope(session), session, after, tail)
            respond(Json.encodeToString(AcceptedOperationsDto.serializer(), ack(request)), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            repository.retainDraft(session, baseline, head)
            assertEquals(SubmitOutcome.Accepted(9, 20), repository.submit(session, head))
            assertNull(repository.pendingChange(session))
            assertEquals(1, repository.pendingDraft(session)!!.operationCount)
        } finally { repository.close() }
        assertEquals(1, posts)
        val reopened = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).read(scope(session))!!.bundle!!
        assertNull(reopened.pending)
        assertEquals(listOf(tail), reopened.journal!!.operations)
        assertEquals(9L, reopened.journal.baseVersion)
        assertNotNull(reopened.acknowledged)
    } }

    @Test fun failedStagePreventsHttpAndPreservesOriginalDraft() = fixture { root -> runTest {
        var fail = false
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root) {
            if (fail && it == AtomicDraftStage.DataForced) error("fixture disk failure")
        })
        val preferences = SessionPreferences(InMemorySettings()).apply { draftPersistence = DesktopWorkspaceDraftPersistence(store) }
        val session = session(preferences)
        var posts = 0
        val repository = BackendWorkspaceRepository("https://qa.invalid", preferences, client { posts++; error("Unexpected HTTP") })
        try {
            repository.retainDraft(session, baseline, head)
            val original = store.read(scope(session))
            fail = true
            assertFails { repository.submit(session, head) }
            assertEquals(original, store.read(scope(session)))
            assertEquals(0, posts)
        } finally { repository.close() }
    } }

    @Test fun unknownPublicationDoesNotPostAndExplicitRetryUsesExactSavedRequest() = fixture { root -> runTest {
        var fail = false
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root) {
            if (fail && it == AtomicDraftStage.Published) error("fixture unknown publication")
        })
        val preferences = SessionPreferences(InMemorySettings()).apply { draftPersistence = DesktopWorkspaceDraftPersistence(store) }
        val session = session(preferences)
        var posts = 0
        val repository = BackendWorkspaceRepository("https://qa.invalid", preferences, client { posts++; error("Unexpected HTTP") })
        try {
            repository.retainDraft(session, baseline, head)
            fail = true
            assertFailsWith<AtomicDraftCommitUnknownException> { repository.submit(session, head) }
            assertEquals(0, posts)
        } finally { repository.close() }
        val saved = store.read(scope(session))!!.bundle!!.pending!!
        val normal = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        preferences.draftPersistence = DesktopWorkspaceDraftPersistence(normal)
        val retry = BackendWorkspaceRepository("https://qa.invalid", preferences, client { http ->
            posts++
            val request = Json.decodeFromString<SubmitOperationsRequest>((http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertEquals(saved.request, request)
            respond(Json.encodeToString(AcceptedOperationsDto.serializer(), ack(request)), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try { assertEquals(SubmitOutcome.Accepted(9, 20), retry.retryPendingSubmission(session)) }
        finally { retry.close() }
        assertEquals(1, posts)
        assertNull(normal.read(scope(session))!!.bundle!!.pending)
    } }

    @Test fun legacyWireOnlyStopsBeforeTransportWithoutInventingBaseline() = fixture { root -> runTest {
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        val preferences = SessionPreferences(InMemorySettings()).apply { draftPersistence = DesktopWorkspaceDraftPersistence(store) }
        val session = session(preferences)
        val wire = PendingWorkspaceSubmission(scope(session), "head", SubmitOperationsRequest(clientId = session.clientId,
            transactionId = "legacy-tx", baseVersion = 8,
            operations = listOf(OperationDto("legacy-wire", 1, "create_object", payload = buildJsonObject {}))))
        val original = store.compareAndSet(scope(session), null, WorkspaceDraftScopeBundle(scope = scope(session), pending = wire))
        var posts = 0
        val repository = BackendWorkspaceRepository("https://qa.invalid", preferences, client { posts++; error("Unexpected HTTP") })
        try { assertFails { repository.retryPendingSubmission(session) } }
        finally { repository.close() }
        assertEquals(0, posts)
        assertEquals(original, store.read(scope(session)))
    } }
}
