package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacyStoppedInventoryTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")

    private fun wire(
        target: PendingSubmissionScope = scope,
        tx: String = "tx",
        seq: Long = 20,
    ) = PendingWorkspaceSubmission(
        target,
        "local",
        SubmitOperationsRequest(
            clientId = target.clientId,
            transactionId = tx,
            baseVersion = 8,
            operations = listOf(OperationDto("wire-$tx", seq, "create_object", payload = buildJsonObject { put("text", "Draft") })),
        ),
    )

    @Test fun discoversEveryModernScopeAndTransactionWithoutWritesOrFilteringForeignEvidence() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val entries =
            listOf(
                wire(),
                wire(tx = "later", seq = 900),
                wire(scope.copy(workspaceId = "other"), "another", 700),
                wire(scope.copy(clientId = "foreign"), "foreign", 8000),
            )
        entries.forEach(store::archiveStopped)
        val before = memory.keys.associateWith(memory::getStringOrNull)
        assertEquals(entries.toSet(), store.stoppedInventoryForMigration(emptyList()).toSet())
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        assertTrue(PendingWorkspaceSubmissionStore(InMemorySettings()).stoppedInventoryForMigration(emptyList()).isEmpty())
    }

    @Test fun legacyWithoutMetadataNeedsCompleteExactCatalogAndCorruptionCannotBeSkipped() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val entries = listOf(wire(), wire(scope.copy(workspaceId = "other"), "another", 80))
        entries.forEach(store::archiveStopped)
        memory.keys.filter { it.startsWith("wsa1.") }.forEach { key ->
            val manifest = Json.parseToJsonElement(memory.getStringOrNull(key)!!).jsonObject
            memory.putString(key, JsonObject(manifest - "archiveScope" - "archiveTransactionId").toString())
        }
        val catalog = entries.map { StoppedSubmissionLocator(it.scope, it.request.transactionId) }
        val before = memory.keys.associateWith(memory::getStringOrNull)
        for (incomplete in listOf(
            emptyList(),
            catalog.take(1),
            catalog + catalog.first(),
            listOf(catalog.first().copy(transactionId = "missing")),
        )) {
            assertFailsWith<BackendContractException> { store.stoppedInventoryForMigration(incomplete) }
        }
        assertEquals(entries.toSet(), store.stoppedInventoryForMigration(catalog).toSet())
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        val corruptKey = memory.keys.first { it.startsWith("wsac1.") }
        memory.putString(corruptKey, "broken")
        assertFailsWith<BackendContractException> { store.stoppedInventoryForMigration(catalog) }
        assertEquals("broken", memory.getStringOrNull(corruptKey))
    }

    @Test fun boundsAllDeclaredBytesBeforeReadingAnyArchiveWire() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        repeat(5) { store.archiveStopped(wire(scope.copy(workspaceId = "ws-$it"), "tx-$it")) }
        memory.keys.filter { it.startsWith("wsa1.") }.forEach { key ->
            val manifest = Json.parseToJsonElement(memory.getStringOrNull(key)!!).jsonObject
            memory.putString(key, JsonObject(manifest + ("byteSize" to JsonPrimitive(1024 * 1024))).toString())
        }
        var reads = 0
        val counted =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (key.startsWith("wsac1.")) reads++
                    return memory.getStringOrNull(key)
                }
            }
        assertFailsWith<BackendContractException> { PendingWorkspaceSubmissionStore(counted).stoppedInventoryForMigration(emptyList()) }
        assertEquals(0, reads)
    }

    @Test fun detectsCatalogPublicationDuringChunkReadWithoutRollingBackAnotherWriter() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val original = wire()
        val other = wire(tx = "late", seq = 500)
        store.archiveStopped(original)
        var changed = false
        var writes = 0
        val settings =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (!changed && key.startsWith("wsac1.")) {
                        changed = true
                        store.archiveStopped(other)
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
        assertFailsWith<BackendContractException> { PendingWorkspaceSubmissionStore(settings).stoppedInventoryForMigration(emptyList()) }
        assertTrue(changed)
        assertEquals(0, writes)
        assertEquals(original, store.loadStopped(scope, original.request.transactionId))
        assertEquals(other, store.loadStopped(scope, other.request.transactionId))
    }
}
