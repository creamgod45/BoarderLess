package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DesktopNativeServerStorageTest {
    private fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-native-server")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun scope(
        prefs: SessionPreferences,
        board: String = "board",
    ) = PendingSubmissionScope("http://fixture/api/v1", "user", prefs.clientId, board)

    private fun session(
        prefs: SessionPreferences,
        board: String = "board",
    ) = WorkspaceSession("user", prefs.clientId, WorkspaceMemberRole.Editor, 0, 0, Workspace(WorkspaceId(board), "Board"))

    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)), text = "中文🙂")
    private val operation = CreateObjectsOperation("create", listOf(node))

    @Test fun freshNamespacePersistsIdentityMetadataDraftAndSharedScopedSequenceWithoutLegacySettings() =
        temporary { root ->
            val prefs = DesktopNativeServerStorage(root).preferences
            val client = prefs.clientId
            prefs.userId = "user"
            prefs.workspaceId = "board"
            val opened = session(prefs)
            val first = scope(prefs)
            prefs.draftPersistence.append(first, opened, opened.workspace, operation)
            assertEquals(1L..2L, prefs.sequenceAllocator!!.reserve(first, 2))
            val second = scope(prefs, "other")
            assertEquals(3L..3L, prefs.sequenceAllocator!!.reserve(second, 1))
            val reopened = DesktopNativeServerStorage(root).preferences
            assertEquals(client, reopened.clientId)
            assertEquals("user", reopened.userId)
            assertEquals("board", reopened.workspaceId)
            assertEquals(listOf(operation), reopened.draftPersistence.journal(first)!!.operations)
            assertEquals(4L..4L, reopened.sequenceAllocator!!.reserve(first, 1))
            assertTrue(reopened.draftPersistence.supportsAtomicStoppedEvidence)
            assertFails { reopened.clientSequence }
            assertFails { reopened.draftPersistence.journal(first.copy(clientId = "foreign")) }
        }

    @Test fun interruptedFreshEnrollmentReopensSameIdentityAndNeverReservesBeforeReady() {
        for (stage in AtomicDraftStage.entries) {
            for (failureAt in 1..5) {
                temporary { root ->
                    var armed = false
                    var count = 0
                    val files = DesktopAtomicDraftStore(root) { if (armed && it == stage && ++count == failureAt) error("File checkpoint") }
                    val prefs = DesktopNativeServerStorage(files).preferences
                    val client = prefs.clientId
                    val current = scope(prefs)
                    armed = true
                    runCatching { prefs.draftPersistence.journal(current) }
                    armed = false
                    val reopened = DesktopNativeServerStorage(root).preferences
                    assertEquals(client, reopened.clientId)
                    assertNull(reopened.draftPersistence.journal(current))
                    assertEquals(1L..1L, reopened.sequenceAllocator!!.reserve(current, 1))
                    assertEquals(2L..2L, DesktopNativeServerStorage(root).preferences.sequenceAllocator!!.reserve(current, 1))
                }
            }
        }
    }

    @Test fun registeredLedgerDraftSafetyOrControlLossNeverInitializesZeroOrResetsIdentity() {
        for (kind in listOf("ledger", "draft", "safety", "control")) {
            temporary { root ->
                val files = DesktopAtomicDraftStore(root)
                val prefs = DesktopNativeServerStorage(files).preferences
                val current = scope(prefs)
                assertEquals(1L..4L, prefs.sequenceAllocator!!.reserve(current, 4))
                val known =
                    setOf(
                        WorkspaceDraftScopeBundleCodec.scopeHash(current),
                        DesktopRecoverySafetyStore(files).key(current),
                        DesktopClientSequenceLedger(files).key(current),
                    )
                val key =
                    when (kind) {
                        "ledger" -> DesktopClientSequenceLedger(files).key(current)
                        "draft" -> WorkspaceDraftScopeBundleCodec.scopeHash(current)
                        "safety" -> DesktopRecoverySafetyStore(files).key(current)
                        else -> files.recordKeys().single { it !in known }
                    }
                Files.delete(root.resolve("$key.record"))
                val remaining = files.recordKeys().associateWith { Files.readAllBytes(root.resolve("$it.record")) }
                val reopened = DesktopNativeServerStorage(root).preferences
                assertFails { reopened.sequenceAllocator!!.reserve(current, 1) }
                assertEquals(remaining.keys, files.recordKeys())
                remaining.forEach { (id, bytes) -> assertContentEquals(bytes, Files.readAllBytes(root.resolve("$id.record"))) }
            }
        }
    }

    @Test fun realRepositoryLostResponseKeepsExactNativeWireAndExplicitRetryReusesIdentityAndSequence() =
        temporary { root ->
            runTest {
                val prefs = DesktopNativeServerStorage(root).preferences
                val opened = session(prefs)
                val bodies = mutableListOf<String>()
                val first =
                    BackendWorkspaceRepository(
                        "http://fixture",
                        prefs,
                        HttpClient(
                            MockEngine { request ->
                                bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                                throw java.io.IOException("Lost fixture response")
                            },
                        ) { install(ContentNegotiation) { json() } },
                    )
                first.retainDraft(opened, opened.workspace, operation)
                assertFails { first.submit(opened, operation) }
                val saved = assertNotNull(prefs.draftPersistence.pending(scope(prefs)))
                first.close()
                val reopened = DesktopNativeServerStorage(root).preferences
                assertEquals(saved, reopened.draftPersistence.pending(scope(reopened)))
                val retry =
                    BackendWorkspaceRepository(
                        "http://fixture",
                        reopened,
                        HttpClient(
                            MockEngine { request ->
                                bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                                throw java.io.IOException("Lost fixture retry")
                            },
                        ) { install(ContentNegotiation) { json() } },
                    )
                try {
                    assertFails { retry.retryPendingSubmission(opened) }
                    assertEquals(2, bodies.size)
                    assertEquals(Json.parseToJsonElement(bodies[0]), Json.parseToJsonElement(bodies[1]))
                    assertEquals(saved, reopened.draftPersistence.pending(scope(reopened)))
                    assertEquals(
                        saved.request.operations
                            .last()
                            .clientSeq + 1,
                        reopened.sequenceAllocator!!.reserve(scope(reopened), 1).first,
                    )
                } finally {
                    retry.close()
                }
            }
        }

    @Test fun acceptedDeletionPersistsReceiptAndProvenanceInFreshNativeNamespace() =
        temporary { root ->
            runTest {
                val prefs = DesktopNativeServerStorage(root).preferences
                val baseline = session(prefs).workspace.copy(objects = mapOf(node.id to node))
                val opened = session(prefs).copy(workspace = baseline)
                val deletion = DeleteObjectsOperation("delete", listOf(node), emptyList())
                val current = scope(prefs)
                var requests = 0
                lateinit var staged: PendingWorkspaceSubmission
                val client =
                    HttpClient(
                        MockEngine { http ->
                            requests++
                            val request =
                                Json.decodeFromString<SubmitOperationsRequest>(
                                    (http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                                )
                            staged = assertNotNull(prefs.draftPersistence.pending(current))
                            assertEquals(staged.request, request)
                            assertEquals(1L, request.operations.single().clientSeq)
                            val records =
                                request.operations.map { op ->
                                    CommittedWorkspaceOperationDto(
                                        7,
                                        op.operationId,
                                        request.transactionId,
                                        "user",
                                        prefs.clientId,
                                        op.clientSeq,
                                        0,
                                        1,
                                        op.kind,
                                        JsonObject(op.payload + ("cascadedRelationIds" to JsonArray(emptyList()))),
                                        1,
                                        "2026-10-06T00:00:00Z",
                                    )
                                }
                            respond(
                                Json.encodeToString(AcceptedOperationsDto("accepted", 1, 7, 7, records)),
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { install(ContentNegotiation) { json() } }
                val repository = BackendWorkspaceRepository("http://fixture", prefs, client)
                try {
                    repository.retainDraft(opened, baseline, deletion)
                    assertEquals(SubmitOutcome.Accepted(1, 7), repository.submit(opened, deletion))
                } finally {
                    repository.close()
                }
                assertEquals(1, requests)
                val reopened = DesktopNativeServerStorage(root).preferences
                assertEquals(prefs.clientId, reopened.clientId)
                assertNull(reopened.draftPersistence.pending(current))
                val persisted = assertNotNull(DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).read(current)).bundle!!
                assertEquals(LocalDraftAcknowledgement(staged, 1, 7), persisted.acknowledged)
                val evidence = assertNotNull(DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root)).deletion(current, "object:node"))
                assertEquals(staged.request.transactionId, evidence.transactionId)
                assertEquals(staged.deletionSnapshotDigests.getValue("object:node"), evidence.snapshotDigest)
                assertEquals(2, evidence.tombstoneVersion)
                assertEquals(2L..2L, reopened.sequenceAllocator!!.reserve(current, 1))
            }
        }
}
