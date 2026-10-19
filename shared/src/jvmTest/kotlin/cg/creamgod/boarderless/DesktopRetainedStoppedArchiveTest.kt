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

class DesktopRetainedStoppedArchiveTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val baseline = Workspace(WorkspaceId("workspace"), "Fixture")
    private val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, baseline)
    private val operation =
        CreateObjectsOperation(
            "head",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                    text = "Unsent",
                ),
            ),
        )

    private fun wire(
        tx: String,
        seq: Long,
    ) = PendingWorkspaceSubmission(
        scope,
        "head",
        SubmitOperationsRequest(
            clientId = "client",
            transactionId = tx,
            baseVersion = 8,
            operations = listOf(OperationDto("op-$tx", seq, "create_object", payload = buildJsonObject {})),
        ),
    )

    private fun evidence(tx: String) =
        StoppedSubmissionEvidence(
            SubmissionReceiptResponseDto(
                "workspace",
                19,
                8,
                emptyList(),
                listOf(ReceiptLookupDto("transaction", tx, "fenced")),
            ),
            null,
            8,
            19,
        )

    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-retained-archive-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun archivesSurviveDraftAppendStageAcknowledgementRemovalAndReopen() =
        fixture { root ->
            val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
            val entries = listOf(wire("old", 20), wire("older", 900))
            val adopted = store.adoptStoppedArchives(scope, null, entries)
            assertEquals(3, adopted.bundle!!.version)
            val adapter = DesktopWorkspaceDraftPersistence(store)
            assertEquals(entries, adapter.stopped(scope))
            adapter.append(scope, session, baseline, operation)
            val pending = wire("new", 901)
            adapter.stage(pending)
            val withPending = store.read(scope)!!
            adapter.recordStoppedEvidence(scope, withPending.generation, entries.first(), evidence("old"))
            assertEquals(pending, adapter.pending(scope))
            assertEquals(evidence("old"), adapter.stoppedEvidence(scope, "old"))
            assertNull(adapter.stoppedEvidence(scope, "older"))
            assertNull(adapter.stoppedEvidence(scope, "new"))
            val journalId = adapter.journal(scope)!!.id
            adapter.acknowledge(pending, 9, 20)
            assertTrue(adapter.remove(scope, journalId))
            val reopened = DesktopWorkspaceDraftPersistence(DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)))
            assertNull(reopened.journal(scope))
            assertNull(reopened.pending(scope))
            assertEquals(entries, reopened.stopped(scope))
            assertEquals(evidence("old"), reopened.stoppedEvidence(scope, "old"))
            assertEquals(
                900L,
                CapturedClientSequenceFloor.calculate(
                    scope,
                    10,
                    emptyList(),
                    listOf(store.read(scope)!!.bundle!!),
                    emptyList(),
                ),
            )
            reopened.append(scope, session, baseline, operation)
            assertEquals(entries, reopened.stopped(scope))
            assertFails { reopened.stage(entries.first()) } // Archived identity cannot be resent.
        }

    @Test fun adoptionRejectsChangedIdentityDuplicateScopeAndStaleGeneration() =
        fixture { root ->
            val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
            val archived = wire("old", 20)
            val saved = store.adoptStoppedArchives(scope, null, listOf(archived))
            val bad =
                listOf(
                    listOf(archived, archived),
                    listOf(wire("old", 21)),
                    listOf(archived.copy(scope = scope.copy(workspaceId = "foreign"))),
                )
            bad.forEach { assertFails { store.adoptStoppedArchives(scope, saved.generation, it) } }
            assertFailsWith<AtomicDraftConflictException> { store.adoptStoppedArchives(scope, null, listOf(wire("later", 22))) }
            assertEquals(saved, store.read(scope))
            assertFails { WorkspaceDraftScopeBundleCodec.encode(saved.bundle!!.copy(version = 1)) }
            assertFails { WorkspaceDraftScopeBundleCodec.encode(saved.bundle!!.copy(retainedStopped = emptyList())) }
            assertFails { WorkspaceDraftScopeBundleCodec.encode(saved.bundle!!.copy(pending = archived)) }
        }

    @Test fun legacyJournalAndArchiveAdoptionWorksInEitherOrderWithoutRewinding() {
        for (archivesFirst in listOf(true, false)) {
            fixture { root ->
                val memory = InMemorySettings()
                val journalStore = WorkspaceDraftJournalStore(memory)
                journalStore.append(scope, session, baseline, operation)
                val source = WorkspaceDraftLegacySnapshot(scope, journalStore.load(scope), null)
                val before = memory.keys.associateWith(memory::getStringOrNull)
                val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
                val entries = listOf(wire("a", 20), wire("b", 30))
                if (archivesFirst) {
                    store.adoptStoppedArchives(scope, null, entries)
                    store.adoptLegacy(source)
                } else {
                    val saved = store.adoptLegacy(source)
                    store.adoptStoppedArchives(scope, saved.generation, entries)
                }
                val saved = store.read(scope)!!
                assertEquals(source.journal, saved.bundle!!.journal)
                assertEquals(entries, saved.bundle.retainedStopped.map { it.submitted })
                assertEquals(saved, store.adoptLegacy(source))
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            }
        }
    }

    @Test fun archivePublicationFaultPreservesOldOrCompleteNewSetAndEvidenceIsPerTransaction() {
        for (stage in AtomicDraftStage.entries) {
            fixture { root ->
                val normal = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
                val original = normal.adoptStoppedArchives(scope, null, listOf(wire("old", 20)))
                val failing = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root) { if (it == stage) error("fault") })
                assertFails { failing.adoptStoppedArchives(scope, original.generation, listOf(wire("later", 50))) }
                val saved = normal.read(scope)!!
                assertEquals(if (stage == AtomicDraftStage.DataForced) 1 else 2, saved.bundle!!.retainedStopped.size)
                val adapter = DesktopWorkspaceDraftPersistence(normal)
                adapter.recordStoppedEvidence(scope, saved.generation, wire("old", 20), evidence("old"))
                val before = normal.read(scope)!!
                assertFails { adapter.recordStoppedEvidence(scope, before.generation, wire("old", 20), evidence("later")) }
                assertEquals(before, normal.read(scope))
                assertEquals(evidence("old"), adapter.stoppedEvidence(scope, "old"))
                assertNull(adapter.stoppedEvidence(scope, "later"))
            }
        }
    }

    @Test fun repositoryHintsAreScopedToEachArchiveAndPerformNoNetworkOrWrites() =
        fixture { root ->
            runTest {
                val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
                val entries = listOf(wire("a", 20), wire("b", 30))
                val saved = store.adoptStoppedArchives(scope, null, entries)
                val adapter = DesktopWorkspaceDraftPersistence(store)
                adapter.recordStoppedEvidence(scope, saved.generation, entries.first(), evidence("a"))
                val memory = InMemorySettings().apply { putString("backend.clientId", scope.clientId) }
                val prefs =
                    SessionPreferences(memory).apply {
                        draftPersistence = adapter
                        clientSequence = 40
                    }
                val before = store.read(scope)
                val settingsBefore = memory.keys.associateWith(memory::getStringOrNull)
                var calls = 0
                val client =
                    HttpClient(
                        MockEngine {
                            calls++
                            error("Read-only archive list must not use HTTP")
                        },
                    )
                try {
                    val repo = BackendWorkspaceRepository("https://qa.invalid", prefs, client)
                    val changes = repo.stoppedChanges(session)
                    assertEquals(listOf("a", "b"), changes.map { it.transactionId })
                    assertEquals(listOf(true, false), changes.map { it.hasSavedEvidence })
                    assertEquals(0, calls)
                    assertEquals(before, store.read(scope))
                    assertEquals(settingsBefore, memory.keys.associateWith(memory::getStringOrNull))
                } finally {
                    client.close()
                }
            }
        }
}
