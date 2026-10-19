package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacyPendingInventoryTest {
    private val first = PendingSubmissionScope("https://qa.invalid/api/v1", "user", "client", "first")
    private val second = first.copy(workspaceId = "second")

    private fun entry(
        scope: PendingSubmissionScope,
        sequence: Long,
    ) = PendingWorkspaceSubmission(
        scope,
        "local-${scope.workspaceId}",
        SubmitOperationsRequest(
            clientId = scope.clientId,
            transactionId = "tx-${scope.workspaceId}",
            baseVersion = 0,
            operations =
                listOf(
                    OperationDto(
                        "wire-${scope.workspaceId}",
                        sequence,
                        "create_object",
                        payload = buildJsonObject { put("text", "素材🙂") },
                    ),
                ),
        ),
    )

    private fun snapshot(memory: InMemorySettings) = memory.keys.associateWith { memory.getStringOrNull(it) }

    @Test fun capturesEveryPublishedWorkspaceWithoutChangingWireOrPreferences() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val entries = listOf(entry(first, 10), entry(second, 91))
        entries.forEach(store::save)
        memory.putString("unrelated", "keep")
        memory.putString("wsc1.orphan", "keep orphan")
        val before = snapshot(memory)
        assertEquals(entries.toSet(), store.inventoryForMigration(listOf(second, first)).toSet())
        assertEquals(before, snapshot(memory))
        assertTrue(PendingWorkspaceSubmissionStore(InMemorySettings()).inventoryForMigration(emptyList()).isEmpty())
    }

    @Test fun incompleteOrDuplicateCatalogNeverSilentlyLowersTheFloor() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        store.save(entry(first, 10))
        store.save(entry(second, 91))
        val before = snapshot(memory)
        for (catalog in listOf(emptyList(), listOf(first), listOf(first, second, first))) {
            assertFailsWith<BackendContractException> { store.inventoryForMigration(catalog) }
        }
        memory.putString("wsp1.unknown", "unknown version")
        val withUnknown = snapshot(memory)
        assertFailsWith<BackendContractException> { store.inventoryForMigration(listOf(first, second)) }
        assertEquals(withUnknown, snapshot(memory))
        memory.remove("wsp1.unknown") // Only the explicit synthetic test record.
        assertEquals(before, snapshot(memory))
    }

    @Test fun corruptManifestOrMissingChunkStopsCaptureAndPreservesEvidence() {
        for (damageManifest in listOf(true, false)) {
            val memory = InMemorySettings()
            val store = PendingWorkspaceSubmissionStore(memory)
            store.save(entry(first, 10))
            if (damageManifest) {
                memory.putString(memory.keys.single { it.startsWith("wsp1.") }, "invalid")
            } else {
                memory.remove(memory.keys.single { it.startsWith("wsc1.") })
            }
            val before = snapshot(memory)
            assertFailsWith<BackendContractException> { store.inventoryForMigration(listOf(first)) }
            assertEquals(before, snapshot(memory))
        }
    }

    @Test fun aggregateLimitRejectsBeforeAnyChunkRead() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val scopes = (1..5).map { first.copy(workspaceId = "workspace-$it") }
        scopes.forEach { store.save(entry(it, 10)) }
        memory.keys.filter { it.startsWith("wsp1.") }.forEach { key ->
            val manifest = Json.parseToJsonElement(memory.getStringOrNull(key)!!).jsonObject
            memory.putString(key, JsonObject(manifest + ("byteSize" to JsonPrimitive(1024 * 1024))).toString())
        }
        val before = snapshot(memory)
        var chunks = 0
        val counted =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (key.startsWith("wsc1.")) chunks++
                    return memory.getStringOrNull(key)
                }
            }
        assertFailsWith<BackendContractException> { PendingWorkspaceSubmissionStore(counted).inventoryForMigration(scopes) }
        assertEquals(0, chunks)
        assertEquals(before, snapshot(memory))
    }
}
