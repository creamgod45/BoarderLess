package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*

class DesktopDraftLegacyMigrationTest {
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
                    text = "🙂 legacy",
                ),
            ),
        )

    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-legacy-migration-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun source(memory: InMemorySettings): WorkspaceDraftLegacySnapshot {
        val journal = WorkspaceDraftJournalStore(memory)
        journal.append(scope, session, baseline, operation)
        return WorkspaceDraftLegacySnapshot(scope, journal.load(scope), null)
    }

    @Test fun adoptsActualLegacyFixtureWithoutDeletingKeysAndNeverRewindsNewState() =
        fixture { root ->
            val memory = InMemorySettings()
            val original = source(memory)
            val keys = memory.keys.associateWith { memory.getStringOrNull(it) }
            val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
            val adopted = store.adoptLegacy(original)
            assertEquals(1L, adopted.generation)
            assertEquals(original.journal, adopted.bundle!!.journal)
            assertNotNull(adopted.bundle.legacyImportDigest)
            assertEquals(keys, memory.keys.associateWith { memory.getStringOrNull(it) })
            val advanced = store.update(scope, 1) { it!!.copy(journal = it.journal!!.copy(quarantined = true)) }
            assertEquals(advanced, DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).adoptLegacy(original))
            assertFails { store.adoptLegacy(original.copy(journal = original.journal!!.copy(id = "changed-source"))) }
            assertEquals(advanced, store.read(scope))
        }

    @Test fun publicationInterruptionLeavesNoMarkerOrOneCompleteBundle() =
        fixture { root ->
            val source = source(InMemorySettings())
            val before =
                DesktopDraftScopeBundleStore(
                    DesktopAtomicDraftStore(root) {
                        if (it == AtomicDraftStage.DataForced) error("fixture before publication")
                    },
                )
            assertFails { before.adoptLegacy(source) }
            val normal = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
            assertNull(normal.read(scope))
            val after =
                DesktopDraftScopeBundleStore(
                    DesktopAtomicDraftStore(root) {
                        if (it == AtomicDraftStage.Published) error("fixture after publication")
                    },
                )
            assertFailsWith<AtomicDraftCommitUnknownException> { after.adoptLegacy(source) }
            val reopened = normal.read(scope)!!
            assertEquals(WorkspaceDraftLegacyMigration.prepare(source), reopened.bundle)
            assertEquals(reopened, normal.adoptLegacy(source))
            normal.compareAndSet(scope, reopened.generation, null)
            assertFails { normal.adoptLegacy(source) } // Tombstone must not resurrect stale legacy data.
            assertEquals(StoredDraftScopeBundle(2, null), normal.read(scope))
        }
}
