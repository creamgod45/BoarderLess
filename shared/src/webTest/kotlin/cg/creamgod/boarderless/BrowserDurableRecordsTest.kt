package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.BrowserDurableLocalRecordStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.test.*

/** Real strict IndexedDB transactions; persistence permission is an explicit accepted/denied fixture. */
class BrowserDurableRecordsTest {
    private val key = "a".repeat(64)
    @Test fun strictCommitAcknowledgesAfterRequestSuccessAndReopensExactUtf8() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val files = BrowserDurableLocalRecordStore()
            val payload = "{\"text\":\"中文🙂\"}".encodeToByteArray()
            files.compareAndSet(key, null, payload)
            assertEquals("true", browserRecordFixtureValue("sawEarlySuccess"))
            assertEquals("1", browserRecordFixtureValue("committed"))
            assertEquals("1", browserRecordFixtureValue("acknowledged"))
            browserRecordFixtureReopen()
            val saved = assertNotNull(BrowserDurableLocalRecordStore().read(key))
            assertEquals(1L, saved.generation); assertContentEquals(payload, saved.payload)
            files.confirmDurable(key, 1)
            assertFails { files.compareAndSet(key, null, payload) }
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun deniedPersistenceAndAbortedTransactionNeverCreateSuccessfulReceipt() = runTest {
        for (mode in listOf("denied", "abort")) {
            browserRecordFixtureInstall(mode)
            try {
                val files = BrowserDurableLocalRecordStore()
                assertFails { files.compareAndSet(key, null, "{}".encodeToByteArray()) }
                assertEquals("0", browserRecordFixtureValue("acknowledged"))
                browserRecordFixtureReopen()
                assertNull(files.read(key))
            } finally { browserRecordFixtureRestore() }
        }
    }
    @Test fun cancellationAfterPutSuccessAbortsBeforeDurableCommit() = runTest {
        browserRecordFixtureInstall("hold")
        try {
            val put = CompletableDeferred<Unit>()
            browserRecordFixtureWaitForPut { put.complete(Unit) }
            val files = BrowserDurableLocalRecordStore()
            val work = launch { files.compareAndSet(key, null, "{}".encodeToByteArray()) }
            put.await(); assertEquals("0", browserRecordFixtureValue("acknowledged"))
            work.cancelAndJoin()
            browserRecordFixtureReopen()
            assertNull(files.read(key))
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun competingConnectionsAreFencedAndCorruptRowsAreNotReset() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val first = BrowserDurableLocalRecordStore(); val second = BrowserDurableLocalRecordStore()
            first.compareAndSet(key, null, "original".encodeToByteArray())
            supervisorScope {
                val a = async { runCatching { first.compareAndSet(key, 1, "first".encodeToByteArray()) } }
                val b = async { runCatching { second.compareAndSet(key, 1, "second".encodeToByteArray()) } }
                assertEquals(1, listOf(a.await(), b.await()).count { it.isSuccess })
            }
            assertEquals(2L, first.read(key)?.generation)
            suspendCancellableCoroutine<Unit> { pending -> browserRecordFixtureCorrupt(key) { if (pending.isActive) pending.resume(Unit) } }
            assertFails { second.read(key) }
            assertFails { first.compareAndSet(key, null, "reset".encodeToByteArray()) }
            assertFails { second.read(key) }
        } finally { browserRecordFixtureRestore() }
    }
}
internal expect fun browserRecordFixtureInstall(mode: String)
internal expect fun browserRecordFixtureReopen()
internal expect fun browserRecordFixtureRestore()
internal expect fun browserRecordFixtureValue(name: String): String
internal expect fun browserRecordFixtureWaitForPut(done: () -> Unit)
internal expect fun browserRecordFixtureCorrupt(key: String, done: () -> Unit)
