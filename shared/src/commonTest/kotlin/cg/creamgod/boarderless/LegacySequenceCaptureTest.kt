package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

class LegacySequenceCaptureTest {
    private fun scope(client: String) = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", client, "workspace")

    private fun wire(
        scope: PendingSubmissionScope,
        tx: String,
        seq: Long,
    ) = PendingWorkspaceSubmission(
        scope,
        "local",
        SubmitOperationsRequest(
            clientId = scope.clientId,
            transactionId = tx,
            baseVersion = 8,
            operations = listOf(OperationDto("wire-$tx", seq, "create_object", payload = buildJsonObject {})),
        ),
    )

    @Test fun capturesPendingStoppedAndTypedWiresWithoutChangingCounterOrIdentity() {
        val memory = InMemorySettings()
        val prefs = SessionPreferences(memory)
        val scope = scope(prefs.clientId)
        prefs.clientSequence = 40
        val pending = wire(scope, "pending", 50)
        val stopped = wire(scope, "stopped", 300)
        val foreign = wire(scope.copy(clientId = "foreign"), "foreign", 8000)
        prefs.pendingSubmissions.save(pending)
        // Overlapping exact evidence is legitimate after archive publication before pending removal.
        prefs.pendingSubmissions.archiveStopped(pending)
        prefs.pendingSubmissions.archiveStopped(stopped)
        prefs.pendingSubmissions.archiveStopped(foreign)
        val before = memory.keys.associateWith(memory::getStringOrNull)
        val captured =
            prefs.captureLegacySequenceForMigration(
                scope,
                listOf(scope),
                emptyList(),
                listOf(WorkspaceDraftScopeBundle(scope = scope, stoppedPending = stopped)),
            )
        assertEquals(40L, captured.counter)
        assertEquals(300L, captured.floor)
        assertEquals(listOf(pending), captured.pending)
        assertEquals(setOf(pending, stopped, foreign), captured.stopped.toSet())
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        assertEquals(40L, prefs.clientSequence)
        assertEquals(scope.clientId, prefs.clientId)
    }

    @Test fun missingIdentityCounterWrongClientOrIncompletePendingCatalogNeverInitializesAnything() {
        val memory = InMemorySettings()
        val prefs = SessionPreferences(memory)
        assertFails { prefs.captureLegacySequenceForMigration(scope("missing"), emptyList(), emptyList(), emptyList()) }
        assertTrue(memory.keys.isEmpty())
        val scope = scope(prefs.clientId)
        var before = memory.keys.associateWith(memory::getStringOrNull)
        assertFails { prefs.captureLegacySequenceForMigration(scope, emptyList(), emptyList(), emptyList()) }
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        prefs.clientSequence = 0
        assertEquals(0L, prefs.captureLegacySequenceForMigration(scope, emptyList(), emptyList(), emptyList()).floor)
        prefs.pendingSubmissions.save(wire(scope.copy(workspaceId = "hidden"), "hidden", 90))
        before = memory.keys.associateWith(memory::getStringOrNull)
        assertFails { prefs.captureLegacySequenceForMigration(scope, emptyList(), emptyList(), emptyList()) }
        assertFails { prefs.captureLegacySequenceForMigration(scope.copy(clientId = "other"), emptyList(), emptyList(), emptyList()) }
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        assertEquals(0L, prefs.clientSequence)
    }

    @Test fun observedCounterChangeDuringArchiveReadCannotReturnAnOldFloorOrRollbackWriter() {
        val memory = InMemorySettings()
        var injected = false
        var writes = 0
        var injecting = false
        val settings =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (injecting && !injected && key.startsWith("wsac1.")) {
                        injected = true
                        memory.putLong("backend.clientSequence", 900)
                    }
                    return memory.getStringOrNull(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    if (injecting) writes++
                    memory.putString(key, value)
                }
            }
        val prefs = SessionPreferences(settings)
        val scope = scope(prefs.clientId)
        prefs.clientSequence = 40
        prefs.pendingSubmissions.archiveStopped(wire(scope, "stopped", 100))
        injecting = true
        assertFails { prefs.captureLegacySequenceForMigration(scope, emptyList(), emptyList(), emptyList()) }
        assertTrue(injected)
        assertEquals(0, writes)
        assertEquals(900L, prefs.clientSequence)
    }
}
