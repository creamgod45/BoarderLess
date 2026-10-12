package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.history.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalSyncEvidenceExportTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var record: DurableLocalRecord? = null
        override suspend fun read(key: String) = record
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(record?.generation == generation)
            record = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(record?.generation == generation) }
    }
    private class Destination : DraftBackupDestination {
        var content: String? = null; var closes = 0
        var beforeWrite: () -> Unit = {}
        override suspend fun write(json: String, canWrite: () -> Boolean) {
            beforeWrite(); check(canWrite()); content = json
        }
        override fun close() { closes++ }
    }
    @Test fun explicitExportPreservesPrivateUtf8ContentAndDoesNotMutateLocalState() = runTest {
        val records = Records(); val local = AwaitedLocalWorkspaceRepository(records) { "2026-10-10" }
        val owner = local.openOrCreateWorkspace(); val reviewed = local.readSyncSnapshot(owner)
        val destination = Destination(); val before = records.record!!.generation
        val runtime = DraftBackupRuntime { name ->
            assertEquals("boarderless-sync-evidence.json", name); destination
        }
        assertEquals(DraftBackupResult.Saved, exportLocalSyncEvidence(runtime, local, reviewed, { true }))
        val decoded = LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), destination.content!!)
        assertEquals(reviewed.local.workspace, decoded.workspace)
        assertEquals("private-audit-copy-not-submission-or-authorization", decoded.purpose)
        assertEquals(before, records.record!!.generation)
        assertEquals(reviewed, local.readSyncSnapshot(owner)); assertEquals(1, destination.closes)
        val text = reviewed.copy(local = owner.copy(workspace = owner.workspace.copy(title = "私密🙂\n</script>")))
        assertEquals(text.local.workspace, LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), text.toEvidenceJson()).workspace)
        assertFails { text.copy(local = text.local.copy(workspace = text.local.workspace.copy(title = "\uD800"))).toEvidenceJson() }
    }
    @Test fun cancelledPickerChangedSnapshotAndExpiredWriteGuardNeverExport() = runTest {
        val records = Records(); val local = AwaitedLocalWorkspaceRepository(records) { "2026-10-10" }
        val owner = local.openOrCreateWorkspace(); val reviewed = local.readSyncSnapshot(owner)
        assertEquals(DraftBackupResult.Cancelled, exportLocalSyncEvidence(DraftBackupRuntime { null }, local, reviewed, { true }))
        val stale = Destination()
        assertFails { exportLocalSyncEvidence(DraftBackupRuntime {
            local.retainDraftAwaited(owner, owner.workspace,
                LocalWorkspaceContentOperation("rename", owner.workspace, owner.workspace.copy(title = "Changed"))); stale
        }, local, reviewed, { true }) }
        assertNull(stale.content); assertEquals(1, stale.closes)
        val latest = local.readSyncSnapshot(owner); var current = true
        val expired = Destination().apply { beforeWrite = { current = false } }
        assertFails { exportLocalSyncEvidence(DraftBackupRuntime { expired }, local, latest, { current }) }
        assertNull(expired.content); assertEquals(1, expired.closes)
        val cancelled = Destination()
        assertFailsWith<CancellationException> { exportLocalJsonBackup(DraftBackupRuntime { cancelled }, "fixture.json", { true }) {
            throw CancellationException()
        } }
        assertNull(cancelled.content); assertEquals(1, cancelled.closes)
    }
}
