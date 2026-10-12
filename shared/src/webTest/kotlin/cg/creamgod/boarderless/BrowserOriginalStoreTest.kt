package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.BrowserOriginalStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

class BrowserOriginalStoreTest {
    private val id = "00112233-4455-6677-8899-aabbccddeeff"
    private val token = "11223344-5566-7788-99aa-bbccddeeff00"
    private val otherToken = "22334455-6677-8899-aabb-ccddeeff0011"
    private fun checksum(bytes: ByteArray) = "sha256:" + SHA256().digest(bytes).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
    @Test fun multiChunkOriginalIsPrivateUntilHashPublicationAndReopensExactBytes() = runTest {
        browserOriginalFixtureInstall("allow")
        try {
            val bytes = ByteArray(1024 * 1024 + 123) { (it * 31).toByte() }
            val size = bytes.size.toLong(); val hash = checksum(bytes); val store = BrowserOriginalStore()
            store.begin(id, token, size)
            store.write(id, token, size, 0, bytes.copyOfRange(0, 1024 * 1024))
            assertFails { store.download(id, size, hash) { fail("Partial original must stay private") } }
            assertFails { store.publish(id, token, size, hash) }
            store.write(id, token, size, 1024 * 1024L, bytes.copyOfRange(1024 * 1024, bytes.size))
            assertEquals("true", browserOriginalFixtureValue("sawEarlySuccess"))
            assertEquals("2", browserOriginalFixtureValue("acknowledged"))
            assertFails { store.publish(id, token, size, "sha256:" + "0".repeat(64)) }
            assertFails { store.download(id, size, hash) { fail("Hash mismatch must not publish") } }
            store.publish(id, token, size, hash)
            browserOriginalFixtureReopen()
            val output = ByteArray(bytes.size); var offset = 0
            BrowserOriginalStore().download(id, size, hash) { chunk ->
                assertTrue(chunk.size <= 1024 * 1024)
                chunk.copyInto(output, offset); offset += chunk.size
            }
            assertContentEquals(bytes, output)
            BrowserOriginalStore().publish(id, token, size, hash) // Exact seal retry after unknown ACK.
            assertFails { store.abandon(id, token, size) }
            assertFails { store.resumePrefix(id, token, size) }
            assertFails { store.write(id, token, size, 0, byteArrayOf(1)) }
        } finally { browserOriginalFixtureRestore() }
    }
    @Test fun chunkPutSuccessIsNotCommitAndCancellationRollsBackOffset() = runTest {
        browserOriginalFixtureInstall("hold")
        try {
            val store = BrowserOriginalStore(); val bytes = "中文🙂".encodeToByteArray(); val size = bytes.size.toLong()
            store.begin(id, token, size)
            val put = CompletableDeferred<Unit>(); browserOriginalFixtureWaitForPut { put.complete(Unit) }
            val write = async { store.write(id, token, size, 0, bytes) }
            put.await(); assertFalse(write.isCompleted)
            assertEquals("0", browserOriginalFixtureValue("acknowledged"))
            write.cancelAndJoin(); browserOriginalFixtureReopen()
            store.write(id, token, size, 0, bytes)
            store.publish(id, token, size, checksum(bytes))
            var actual: ByteArray? = null
            store.download(id, size, checksum(bytes)) { actual = it }
            assertContentEquals(bytes, actual)
        } finally { browserOriginalFixtureRestore() }
    }
    @Test fun sealAbortNeverPublishesAndExactReopenRetryCanFinish() = runTest {
        browserOriginalFixtureInstall("abortSeal")
        try {
            val bytes = byteArrayOf(1, 2, 3); val hash = checksum(bytes); val store = BrowserOriginalStore()
            store.begin(id, token, 3); store.write(id, token, 3, 0, bytes)
            assertFails { store.publish(id, token, 3, hash) }
            assertFails { store.download(id, 3, hash) { fail("Aborted publication") } }
            browserOriginalFixtureReopen()
            store.publish(id, token, 3, hash)
            store.download(id, 3, hash) { assertContentEquals(bytes, it) }
        } finally { browserOriginalFixtureRestore() }
    }
    @Test fun storageDenialWriterFenceAndWrongOffsetsNeverReplaceOriginal() = runTest {
        browserOriginalFixtureInstall("denied")
        try { assertFails { BrowserOriginalStore().begin(id, token, 3) } }
        finally { browserOriginalFixtureRestore() }
        browserOriginalFixtureInstall("allow")
        try {
            val store = BrowserOriginalStore(); store.begin(id, token, 3)
            assertFails { store.begin(id, otherToken, 3) }
            assertFails { store.write(id, otherToken, 3, 0, byteArrayOf(1)) }
            assertFails { store.write(id, token, 3, 1, byteArrayOf(1)) }
            supervisorScope {
                val first = async { runCatching { store.write(id, token, 3, 0, byteArrayOf(1, 2, 3)) } }
                val second = async { runCatching { BrowserOriginalStore().write(id, token, 3, 0, byteArrayOf(3, 2, 1)) } }
                assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
            }
            assertFails { store.abandon(id, otherToken, 3) }
            store.abandon(id, token, 3)
            assertFails { store.begin(id, otherToken, 3) }
            browserOriginalFixtureReopen()
            assertFails { BrowserOriginalStore().begin(id, token, 3) }
            assertFails { BrowserOriginalStore().resumePrefix(id, token, 3) }
            assertFails { store.publish(id, token, 3, checksum(byteArrayOf(1, 2, 3))) }
            store.begin(otherToken, token, 3)
        } finally { browserOriginalFixtureRestore() }
    }
}
internal expect fun browserOriginalFixtureInstall(mode: String)
internal expect fun browserOriginalFixtureReopen()
internal expect fun browserOriginalFixtureRestore()
internal expect fun browserOriginalFixtureValue(name: String): String
internal expect fun browserOriginalFixtureWaitForPut(done: () -> Unit)
