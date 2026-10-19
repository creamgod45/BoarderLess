package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.data.persistence.DurableLocalRecord
import cg.creamgod.boarderless.data.remote.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FreshAwaitedServerStorageTest {
    private class Records : AwaitedDurableLocalRecordStore {
        val values = mutableMapOf<String, DurableLocalRecord>()
        var afterWrite: suspend (Int) -> Unit = {}
        var writes = 0
        override suspend fun read(key: String) = values[key]?.let { it.copy(payload = it.payload.copyOf()) }
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(values[key]?.generation == generation)
            values[key] = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
            afterWrite(++writes)
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(values[key]?.generation == generation) }
    }

    private val apiBase = "https://example.test/api/v1"
    private val user = "10000000-0000-4000-8000-000000000001"
    private val client = "20000000-0000-4000-8000-000000000001"
    private fun scope(workspace: Int, userId: String = user, clientId: String = client, base: String = apiBase) =
        PendingSubmissionScope(base, userId, clientId, "30000000-0000-4000-8000-00000000000$workspace")

    private var active = true
    private fun storage(files: Records) = FreshAwaitedServerStorage(files, apiBase) { check(active) { "writer inactive" } }

    @Test fun firstUseEnrollsAndAReopenedStorageVerifiesTheSameRecords() = runTest {
        val files = Records()
        val first = storage(files).runtime
        assertEquals(1L..3L, first.sequences.reserve(scope(1), 3))
        assertNull(first.drafts.read(scope(1))?.bundle)
        val reopened = storage(files).runtime
        assertEquals(4L..4L, reopened.sequences.reserve(scope(1), 1))
    }

    @Test fun secondWorkspaceOfTheSameIdentitySharesTheLedger() = runTest {
        val runtime = storage(Records()).runtime
        assertEquals(1L..2L, runtime.sequences.reserve(scope(1), 2))
        assertEquals(3L..3L, runtime.sequences.reserve(scope(2), 1))
    }

    @Test fun anotherIdentityGetsItsOwnLedgerStartingAtZero() = runTest {
        val runtime = storage(Records()).runtime
        runtime.sequences.reserve(scope(1), 5)
        val other = scope(1, clientId = "20000000-0000-4000-8000-000000000002")
        assertEquals(1L..1L, runtime.sequences.reserve(other, 1))
    }

    @Test fun anOrphanLedgerWithoutAControlEntryIsNeverAdoptedOrReset() = runTest {
        val files = Records()
        // A ledger left behind by an unknown writer: enrollment must refuse, not reinitialize it.
        AwaitedAtomicClientSequenceAllocator(files) {}.initialize(scope(1), 40)
        val runtime = storage(files).runtime
        assertFails { runtime.sequences.reserve(scope(1), 1) }
        assertEquals(40L, AwaitedAtomicClientSequenceAllocator(files) {}.read(scope(1))?.highWater)
    }

    @Test fun unregisteredDraftOrSafetyRecordsBlockEnrollment() = runTest {
        val files = Records()
        AwaitedAtomicRecoverySafetyPersistence(files, {}).adoptLegacy(scope(1), emptyList(), emptyList())
        assertFails { storage(files).runtime.sequences.reserve(scope(1), 1) }
        assertNull(AwaitedAtomicClientSequenceAllocator(files) {}.read(scope(1)))
    }

    @Test fun aLostAcknowledgementResumesTheSamePreparingEntry() = runTest {
        val files = Records()
        files.afterWrite = { if (it == 1) error("lost after Preparing") }
        assertFails { storage(files).runtime.sequences.reserve(scope(1), 1) }
        files.afterWrite = {}
        val runtime = storage(files).runtime
        assertEquals(1L..1L, runtime.sequences.reserve(scope(1), 1))
    }

    @Test fun anotherWorkspaceCannotOvertakeAPreparingEntry() = runTest {
        val files = Records()
        files.afterWrite = { if (it == 3) error("interrupted mid-enrollment") }
        assertFails { storage(files).runtime.sequences.reserve(scope(1), 1) }
        files.afterWrite = {}
        assertFails { storage(files).runtime.sequences.reserve(scope(2), 1) }
        assertEquals(1L..1L, storage(files).runtime.sequences.reserve(scope(1), 1))
        assertEquals(2L..2L, storage(files).runtime.sequences.reserve(scope(2), 1))
    }

    @Test fun aReadyEntryWithAMissingArtifactIsAHardFailureNotAnEmptyReset() = runTest {
        val files = Records()
        storage(files).runtime.sequences.reserve(scope(1), 2)
        // Remove every record except the control: some artifact of the Ready scope is now missing.
        val control = files.values.entries.first { (_, record) -> record.payload.decodeToString().contains("\"entries\"") }
        files.values.keys.toList().filter { it != control.key }.forEach { files.values.remove(it) }
        assertFails { storage(files).runtime.sequences.reserve(scope(1), 1) }
        assertNull(AwaitedAtomicClientSequenceAllocator(files) {}.read(scope(1)))
    }

    @Test fun foreignServerInactiveWriterAndMalformedIdentitiesAreRejected() = runTest {
        val files = Records()
        val runtime = storage(files).runtime
        assertFails { runtime.sequences.reserve(scope(1, base = "https://other.test/api/v1"), 1) }
        assertFails { runtime.sequences.reserve(scope(1, userId = "not-a-uuid"), 1) }
        runtime.sequences.reserve(scope(1), 1)
        active = false
        try { assertFails { runtime.sequences.reserve(scope(1), 1) } } finally { active = true }
        assertEquals(2L..2L, runtime.sequences.reserve(scope(1), 1))
    }

    @Test fun concurrentFirstCallsEnrollOnce() = runTest {
        val files = Records()
        val runtime = storage(files).runtime
        val ranges = (1..5).map { async { runtime.sequences.reserve(scope(1), 1) } }.awaitAll()
        assertEquals((1L..5L).toList(), ranges.map { it.first }.sorted())
        val controls = files.values.values.count { it.payload.decodeToString().contains("\"entries\"") }
        assertEquals(1, controls)
    }

    @Test fun theFreshRuntimeHasNoLegacyIdentityOrActivationTarget() {
        val runtime = storage(Records()).runtime
        assertTrue(runtime.isFresh)
        assertNull(runtime.activationTarget)
    }
}
