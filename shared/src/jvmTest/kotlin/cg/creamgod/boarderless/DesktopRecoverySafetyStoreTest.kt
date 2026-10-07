package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*

class DesktopRecoverySafetyStoreTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "A")
    private val b = a.copy(id = CanvasObjectId("b"), text = "B")
    private val relation = Relation(RelationId("r"), sourceObjectId = a.id, targetObjectId = b.id)
    private val workspace =
        Workspace(
            WorkspaceId("workspace"),
            "Fixture",
            objects = listOf(a, b).associateBy { it.id },
            relations =
                mapOf(relation.id to relation),
        )
    private val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, workspace)

    private fun wire() =
        PendingWorkspaceSubmission(
            scope,
            "head",
            SubmitOperationsRequest(
                clientId = "client",
                transactionId = "tx",
                baseVersion = 8,
                operations = listOf(OperationDto("wire", 20, "create_object", payload = buildJsonObject {})),
            ),
        )

    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-native-safety-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun deleteRecord(): Pair<PendingWorkspaceSubmission, AcceptedOperationsDto> {
        val local = DeleteObjectsOperation("delete", listOf(a, b), listOf(relation))
        val operations = local.toExpandedDtos(40)
        val entry =
            PendingWorkspaceSubmission(
                scope,
                local.operationId,
                SubmitOperationsRequest(clientId = "client", transactionId = "deleted", baseVersion = 8, operations = operations),
                local.deletionVersions(),
                local.deletionSnapshotDigests(),
            )
        val sent = operations.single()
        val record =
            CommittedWorkspaceOperationDto(
                20,
                sent.operationId,
                "deleted",
                "actor",
                "client",
                sent.clientSeq,
                8,
                9,
                sent.kind,
                JsonObject(sent.payload + ("cascadedRelationIds" to JsonArray(listOf(JsonPrimitive("r"))))),
                1,
                "2026-10-05",
            )
        return entry to AcceptedOperationsDto("accepted", 9, 20, 20, listOf(record))
    }

    @Test fun explicitAdoptionPreservesSourcesAndNeverRewindsAdvancedNativeState() =
        fixture { root ->
            val memory = InMemorySettings()
            val legacyFence = PendingFenceAttemptStore(memory)
            legacyFence.begin(wire())
            val (entry, ack) = deleteRecord()
            CommittedDeletionStore(memory).record(entry, ack)
            val fences = legacyFence.inventoryForMigration()
            val deletions = CommittedDeletionStore(memory).inventoryForMigration()
            val before = memory.keys.associateWith(memory::getStringOrNull)
            val files = DesktopAtomicDraftStore(root)
            val store = DesktopRecoverySafetyStore(files)
            assertFails { store.fenceState(wire()) } // No missing-record fallback to an empty store.
            assertNull(store.read(scope))
            store.adoptLegacy(scope, fences, deletions)
            store.observeFence(wire(), PendingReceiptStatus.Fenced)
            val advanced = store.read(scope)!!
            assertEquals(advanced, store.adoptLegacy(scope, fences, deletions.reversed()))
            assertFails { store.adoptLegacy(scope, emptyList(), deletions) }
            assertEquals(advanced, DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root)).read(scope))
            assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            val key =
                Files.list(root).use { paths ->
                    paths
                        .filter { it.fileName.toString().endsWith(".record") }
                        .findFirst()
                        .orElseThrow()
                        .fileName
                        .toString()
                        .removeSuffix(".record")
                }
            files.compareAndSet(key, advanced.generation, null)
            assertFails { store.read(scope) }
            assertFails { store.adoptLegacy(scope, fences, deletions) }
            assertNull(files.read(key)!!.payload) // A tombstone cannot resurrect the old snapshot.
        }

    @Test fun facadeFenceStateSurvivesReopenAndNeverCreatesLegacyFallbackKeys() =
        fixture { root ->
            val memory = InMemorySettings()
            val store = DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root))
            store.adoptLegacy(scope, emptyList(), emptyList())
            val facade = PendingFenceAttemptStore(memory, store)
            facade.observe(wire(), PendingReceiptStatus.Fenced)
            assertNull(facade.state(wire())) // Read-only outcomes do not invent a fence attempt.
            facade.begin(wire())
            val unconfirmed = store.read(scope)
            facade.observe(wire(), PendingReceiptStatus.Unknown)
            assertEquals(unconfirmed, store.read(scope))
            facade.observe(wire(), PendingReceiptStatus.Committed)
            val reopened = PendingFenceAttemptStore(memory, DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root)))
            assertEquals(PendingFenceAttemptStore.State.Committed, reopened.state(wire()))
            assertFails { reopened.begin(wire()) }
            assertFails { reopened.observe(wire(), PendingReceiptStatus.Fenced) }
            val original = wire()
            assertFails { reopened.state(original.copy(request = original.request.copy(baseVersion = 9))) }
            assertTrue(memory.keys.isEmpty())
        }

    @Test fun deletionCascadePublishesAllProofsOrNoneAndProductionRestoreGateReadsNativeProof() {
        for (fault in listOf<AtomicDraftStage?>(null) + AtomicDraftStage.entries) {
            fixture { root ->
                var inject = false
                val files = DesktopAtomicDraftStore(root) { if (inject && it == fault) error("storage fault") }
                val store = DesktopRecoverySafetyStore(files)
                store.adoptLegacy(scope, emptyList(), emptyList())
                val memory = InMemorySettings()
                val facade = CommittedDeletionStore(memory, store)
                val (entry, ack) = deleteRecord()
                inject = fault != null
                if (fault == null) facade.record(entry, ack) else assertFails { facade.record(entry, ack) }
                val reopened = DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root))
                assertEquals(
                    if (fault == AtomicDraftStage.DataForced) 0 else 3,
                    reopened
                        .read(scope)!!
                        .bundle.deletions.size,
                )
                val local =
                    WorkspaceHistory(workspace)
                        .execute(DeleteObjectsOperation("delete", listOf(a, b), listOf(relation)))
                        .history
                        .undo(remoteRestoration = true)
                        .appliedOperation!!
                val restore = CommittedDeletionStore(memory, reopened)
                if (fault == AtomicDraftStage.DataForced) {
                    assertFails { restore.requireRestoration(scope, local.toExpandedDtos(70), local) }
                } else {
                    restore.requireRestoration(scope, local.toExpandedDtos(70), local)
                }
                assertTrue(memory.keys.isEmpty())
            }
        }
    }

    @Test fun malformedDuplicateForeignOrRewoundSafetyCannotReplaceGoodState() =
        fixture { root ->
            val store = DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root))
            val deletion = CommittedDeletionEvidence(scope, "object:a", 2, "deleted", 20, "a".repeat(64))
            store.adoptLegacy(scope, emptyList(), listOf(deletion))
            val original = store.read(scope)
            val bad =
                listOf(
                    listOf(deletion, deletion),
                    listOf(deletion.copy(snapshotDigest = "bad")),
                    listOf(deletion.copy(scope = scope.copy(clientId = "foreign"))),
                    listOf(deletion.copy(transactionId = "different")),
                    listOf(deletion.copy(tombstoneVersion = 1)),
                    listOf(deletion.copy(tombstoneVersion = 3, throughServerSeq = 19)),
                )
            bad.forEach {
                assertFails { store.recordDeletions(scope, it) }
                assertEquals(original, store.read(scope))
            }
            store.recordDeletions(scope, listOf(deletion.copy(tombstoneVersion = 4, throughServerSeq = 30, transactionId = "later")))
            val later = store.read(scope)
            assertFails { store.recordDeletions(scope, listOf(deletion)) }
            assertEquals(later, store.read(scope))
        }

    @Test fun nativeFencePublicationFailureStopsActualRepositoryBeforeHttpAndRetainsPending() {
        for (stage in AtomicDraftStage.entries) {
            fixture { root ->
                runTest {
                    var inject = false
                    val store = DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root) { if (inject && it == stage) error("fault") })
                    store.adoptLegacy(scope, emptyList(), emptyList())
                    val memory = InMemorySettings().apply { putString("backend.clientId", "client") }
                    val prefs = SessionPreferences(memory, store).apply { clientSequence = 40 }
                    prefs.pendingSubmissions.save(wire())
                    val settingsBefore = memory.keys.associateWith(memory::getStringOrNull)
                    var requests = 0
                    val client =
                        HttpClient(
                            MockEngine {
                                requests++
                                error("HTTP must not begin after storage failure")
                            },
                        )
                    try {
                        val repo = BackendWorkspaceRepository("https://qa.invalid", prefs, client)
                        inject = true
                        assertFails { repo.fencePendingChange(session, "tx") }
                        assertEquals(0, requests)
                        assertEquals(wire(), prefs.pendingSubmissions.load(scope))
                        assertEquals(settingsBefore, memory.keys.associateWith(memory::getStringOrNull))
                        val reopened = PendingFenceAttemptStore(memory, DesktopRecoverySafetyStore(DesktopAtomicDraftStore(root)))
                        assertEquals(
                            if (stage ==
                                AtomicDraftStage.DataForced
                            ) {
                                null
                            } else {
                                PendingFenceAttemptStore.State.Unconfirmed
                            },
                            reopened.state(wire()),
                        )
                        assertEquals(stage != AtomicDraftStage.DataForced, repo.pendingChange(session)!!.resendBlocked)
                    } finally {
                        client.close()
                    }
                }
            }
        }
    }
}
