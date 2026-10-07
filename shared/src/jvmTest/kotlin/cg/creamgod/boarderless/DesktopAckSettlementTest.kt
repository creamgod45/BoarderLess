package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
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
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class DesktopAckSettlementTest {
    @Test fun actualNativeRepositoryKeepsExactPendingOrSettledTailAcrossEveryAckPublicationFault() {
        val cases = listOf(null to 0) + AtomicDraftStage.entries.flatMap { stage -> (1..2).map { stage to it } }
        for ((fault, writeNumber) in cases) {
            val root = Files.createTempDirectory("boarderless-native-ack")
            try {
                runTest {
                    val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
                    val memory =
                        InMemorySettings().apply {
                            putString("backend.clientId", "client")
                            putLong("backend.clientSequence", 40)
                        }
                    val legacy = SessionPreferences(memory)

                    fun source() = legacy.captureLegacySequenceForMigration(scope, listOf(scope), emptyList(), emptyList())
                    val sourceBefore = source()
                    val normalFiles = DesktopAtomicDraftStore(root)
                    val normalActivation = DesktopStorageActivation(normalFiles) {}
                    normalActivation.activate(scope) { source() }
                    var inject = false
                    var checkpoints = 0
                    val files =
                        DesktopAtomicDraftStore(root) {
                            if (inject && it == fault && ++checkpoints == writeNumber) error("ACK publication boundary")
                        }
                    val prefs = DesktopStorageActivation(files) {}.openPreferences(memory, scope) { source() }
                    val node =
                        TextNode(CanvasObjectId("old"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Before")
                    val baseline = Workspace(WorkspaceId("workspace"), "Fixture", objects = mapOf(node.id to node))
                    val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, baseline)
                    val head = DeleteObjectsOperation("delete", listOf(node), emptyList())
                    val next = node.copy(id = CanvasObjectId("new"), text = "保留草稿🙂")
                    val tail = CreateObjectsOperation("tail", listOf(next))
                    val sent = mutableListOf<SubmitOperationsRequest>()
                    lateinit var entry: PendingWorkspaceSubmission

                    fun client() =
                        HttpClient(
                            MockEngine { http ->
                                val request =
                                    Json.decodeFromString<SubmitOperationsRequest>(
                                        (http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                                    )
                                sent += request
                                entry = assertNotNull(DesktopDraftScopeBundleStore(normalFiles).read(scope)?.bundle?.pending)
                                assertEquals(request, entry.request)
                                assertEquals(41L, request.operations.single().clientSeq)
                                assertEquals(41L, DesktopClientSequenceLedger(normalFiles).read(scope)?.highWater)
                                inject = fault != null
                                val records =
                                    request.operations.map { op ->
                                        CommittedWorkspaceOperationDto(
                                            20,
                                            op.operationId,
                                            request.transactionId,
                                            "actor",
                                            "client",
                                            op.clientSeq,
                                            8,
                                            9,
                                            op.kind,
                                            JsonObject(op.payload + ("cascadedRelationIds" to JsonArray(emptyList()))),
                                            1,
                                            "2026-10-06T00:00:00Z",
                                        )
                                    }
                                respond(
                                    Json.encodeToString(AcceptedOperationsDto("accepted", 9, 20, 20, records)),
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            },
                        ) { install(ContentNegotiation) { json() } }
                    val repository = BackendWorkspaceRepository("https://qa.invalid", prefs, client())
                    try {
                        repository.retainDraft(session, baseline, head)
                        val after = (head.applyTo(baseline) as OperationResult.Applied).workspace
                        repository.retainDraft(session, after, tail)
                        if (fault == null) {
                            assertEquals(SubmitOutcome.Accepted(9, 20), repository.submit(session, head))
                        } else {
                            assertFails { repository.submit(session, head) }
                        }
                    } finally {
                        repository.close()
                    }
                    inject = false
                    assertEquals(1, sent.size) // Storage failure never retries HTTP automatically.
                    val reopened = assertNotNull(DesktopDraftScopeBundleStore(normalFiles).read(scope)).bundle!!
                    val ackPublished = fault == null || (writeNumber == 2 && fault != AtomicDraftStage.DataForced)
                    if (ackPublished) {
                        assertNull(reopened.pending)
                        assertEquals(LocalDraftAcknowledgement(entry, 9, 20), reopened.acknowledged)
                        assertEquals(listOf(tail), reopened.journal!!.operations)
                        assertEquals(9, reopened.journal.baseVersion)
                        assertEquals(20, reopened.journal.baseServerSeq)
                        assertTrue(
                            reopened.journal.baseWorkspace.objects
                                .isEmpty(),
                        )
                    } else {
                        assertEquals(entry, reopened.pending)
                        assertNull(reopened.acknowledged)
                        assertEquals(listOf(head, tail), reopened.journal!!.operations)
                        assertEquals(entry.request.transactionId, reopened.journal.headTransactionId)
                    }
                    val safety = DesktopRecoverySafetyStore(normalFiles)
                    val evidence = safety.deletion(scope, "object:old")
                    if (fault == AtomicDraftStage.DataForced && writeNumber == 1) {
                        assertNull(evidence)
                    } else {
                        assertEquals(2, assertNotNull(evidence).tombstoneVersion)
                        assertEquals(entry.request.transactionId, evidence.transactionId)
                        assertEquals(entry.deletionSnapshotDigests.getValue("object:old"), evidence.snapshotDigest)
                    }
                    val reopenedPrefs = normalActivation.openPreferences(memory, scope) { source() }
                    if (!ackPublished) {
                        // Explicit retry uses exactly the retained wire; no new ID or sequence.
                        val recoveryClient =
                            HttpClient(
                                MockEngine { http ->
                                    val request =
                                        Json.decodeFromString<SubmitOperationsRequest>(
                                            (http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                                        )
                                    sent += request
                                    assertEquals(entry.request, request)
                                    val op = request.operations.single()
                                    val record =
                                        CommittedWorkspaceOperationDto(
                                            20,
                                            op.operationId,
                                            request.transactionId,
                                            "actor",
                                            "client",
                                            op.clientSeq,
                                            8,
                                            9,
                                            op.kind,
                                            JsonObject(op.payload + ("cascadedRelationIds" to JsonArray(emptyList()))),
                                            1,
                                            "2026-10-06T00:00:00Z",
                                        )
                                    respond(
                                        Json.encodeToString(AcceptedOperationsDto("accepted", 9, 20, 20, listOf(record))),
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                },
                            ) { install(ContentNegotiation) { json() } }
                        val recovery = BackendWorkspaceRepository("https://qa.invalid", reopenedPrefs, recoveryClient)
                        try {
                            assertEquals(SubmitOutcome.Accepted(9, 20), recovery.retryPendingSubmission(session))
                        } finally {
                            recovery.close()
                        }
                    }
                    val settled = assertNotNull(DesktopDraftScopeBundleStore(normalFiles).read(scope))
                    assertNull(settled.bundle!!.pending)
                    assertEquals(listOf(tail), settled.bundle.journal!!.operations)
                    assertEquals(
                        setOf(next.id),
                        settled.bundle.journal
                            .replay()
                            .objects.keys,
                    )
                    assertEquals(41L, DesktopClientSequenceLedger(normalFiles).read(scope)?.highWater)
                    assertEquals(sourceBefore, source())
                    assertEquals(setOf("backend.clientId", "backend.clientSequence"), memory.keys)
                    // No new publication for an exact ACK, but an existing generation must still
                    // complete its force barrier. Failure remains explicit and never rewinds it.
                    var confirmations = 0
                    val confirmingAck =
                        DesktopWorkspaceDraftPersistence(
                            DesktopDraftScopeBundleStore(
                                DesktopAtomicDraftStore(root) {
                                    confirmations++
                                    error("Existing record force boundary")
                                },
                            ),
                        )
                    assertFailsWith<AtomicDraftCommitUnknownException> { confirmingAck.acknowledge(entry, 9, 20) }
                    assertFails { confirmingAck.acknowledge(entry, 10, 21) }
                    assertFails { confirmingAck.acknowledge(entry.copy(localOperationId = "changed"), 9, 20) }
                    assertEquals(1, confirmations)
                    DesktopWorkspaceDraftPersistence(DesktopDraftScopeBundleStore(normalFiles)).acknowledge(entry, 9, 20)
                    assertEquals(settled, DesktopDraftScopeBundleStore(normalFiles).read(scope))
                }
            } finally {
                Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }
}
