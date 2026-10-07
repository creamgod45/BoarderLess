package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacyDraftInventoryTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")

    private fun append(
        store: WorkspaceDraftJournalStore,
        target: PendingSubmissionScope,
    ) {
        val baseline = Workspace(WorkspaceId(target.workspaceId), "Unsubmitted")
        val session = WorkspaceSession(target.userId, target.clientId, WorkspaceMemberRole.Editor, 8, 19, baseline)
        store.append(
            target,
            session,
            baseline,
            CreateObjectsOperation(
                "head",
                listOf(
                    TextNode(
                        CanvasObjectId("node"),
                        transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                        text = "🙂 unsent",
                    ),
                ),
            ),
        )
    }

    @Test fun capturesUnsubmittedAndQuarantinedDraftsAcrossNamespacesWithoutWrites() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        val scopes = listOf(scope, scope.copy(workspaceId = "other"), scope.copy(clientId = "foreign"))
        scopes.forEach { append(store, it) }
        store.quarantine(scopes.last())
        val expected = scopes.map { store.load(it)!! }.toSet()
        val before = memory.keys.associateWith(memory::getStringOrNull)
        assertEquals(expected, store.inventoryForMigration(scopes).toSet())
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        assertTrue(WorkspaceDraftJournalStore(InMemorySettings()).inventoryForMigration(emptyList()).isEmpty())
    }

    @Test fun incompleteDuplicateCatalogAndCorruptChunkCannotBeSkipped() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        val other = scope.copy(workspaceId = "other")
        append(store, scope)
        append(store, other)
        val before = memory.keys.associateWith(memory::getStringOrNull)
        for (catalog in listOf(emptyList(), listOf(scope), listOf(scope, other, scope))) {
            assertFailsWith<BackendContractException> { store.inventoryForMigration(catalog) }
        }
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        val chunk = memory.keys.first { it.startsWith("wdc1.") }
        memory.putString(chunk, "broken")
        assertFailsWith<BackendContractException> { store.inventoryForMigration(listOf(scope, other)) }
        assertEquals("broken", memory.getStringOrNull(chunk))
    }

    @Test fun aggregateBoundPrecedesEveryChunkRead() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        val scopes = (1..5).map { scope.copy(workspaceId = "ws-$it") }
        scopes.forEach { append(store, it) }
        memory.keys.filter { it.startsWith("wdj1.") }.forEach { key ->
            val original = Json.parseToJsonElement(memory.getStringOrNull(key)!!).jsonObject
            memory.putString(key, JsonObject(original + ("bytes" to JsonPrimitive(4 * 1024 * 1024))).toString())
        }
        var reads = 0
        val settings =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (key.startsWith("wdc1.")) reads++
                    return memory.getStringOrNull(key)
                }
            }
        assertFailsWith<BackendContractException> { WorkspaceDraftJournalStore(settings).inventoryForMigration(scopes) }
        assertEquals(0, reads)
    }

    @Test fun concurrentDraftPublicationIsDetectedWithoutDeletingEitherDraft() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        append(store, scope)
        val other = scope.copy(workspaceId = "late")
        var changed = false
        var writes = 0
        val settings =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (!changed && key.startsWith("wdc1.")) {
                        changed = true
                        append(store, other)
                    }
                    return memory.getStringOrNull(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    writes++
                    memory.putString(key, value)
                }
            }
        assertFailsWith<BackendContractException> { WorkspaceDraftJournalStore(settings).inventoryForMigration(listOf(scope, other)) }
        assertTrue(changed)
        assertEquals(0, writes)
        assertNotNull(store.load(scope))
        assertNotNull(store.load(other))
    }

    @Test fun sequenceCaptureRequiresUnsubmittedDraftScopesAndRetainsTheirFullIntentions() {
        val memory = InMemorySettings()
        val prefs = SessionPreferences(memory)
        val target = scope.copy(clientId = prefs.clientId)
        prefs.clientSequence = 12
        append(prefs.draftJournals, target)
        val before = memory.keys.associateWith(memory::getStringOrNull)
        assertFailsWith<BackendContractException> {
            prefs.captureLegacySequenceForMigration(target, emptyList(), emptyList(), emptyList())
        }
        val captured = prefs.captureLegacySequenceForMigration(target, listOf(target), emptyList(), emptyList())
        assertEquals(listOf(prefs.draftJournals.load(target)), captured.journals)
        assertTrue(captured.pending.isEmpty() && captured.stopped.isEmpty())
        assertEquals(12L, captured.floor) // Unsubmitted operation IDs are not allocated clientSeq.
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
    }
}
