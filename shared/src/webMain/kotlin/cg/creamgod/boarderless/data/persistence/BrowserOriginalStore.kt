package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.DefaultAssetUploadChunkBytes
import cg.creamgod.boarderless.data.MaxWorkspaceAssetBytes
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

/** Bounded chunks remain private until their complete original is re-read, hashed and sealed.
 * Every write acknowledges transaction completion; cancellation does not expose a partial original.
 */
internal enum class BrowserOriginalState { Missing, Pending, CompletionRequested, Published }

internal class BrowserOriginalStore {
    private suspend fun operation(action: String, id: String, token: String?, size: Long, offset: Long = 0, data: String? = null): String? {
        validateLocalAssetId(id); token?.let(::validateLocalAssetId)
        require(size in 1..MaxWorkspaceAssetBytes && offset in 0..size)
        currentCoroutineContext().ensureActive()
        val requestId = randomUuid()
        return suspendCancellableCoroutine { pending ->
            pending.invokeOnCancellation { browserCancelOriginalOperation(requestId) }
            if (!pending.isActive) return@suspendCancellableCoroutine
            try {
                browserOriginalOperation(requestId, action, id, token, offset.toString(), size.toString(), data) { value, error ->
                    if (pending.isActive) {
                        if (error != null) pending.resumeWithException(IllegalStateException("Browser original storage failed"))
                        else pending.resume(value)
                    }
                }
            } catch (_: Exception) {
                browserCancelOriginalOperation(requestId)
                if (pending.isActive) pending.resumeWithException(IllegalStateException("Browser original storage unavailable"))
            }
        }
    }
    suspend fun state(id: String, size: Long, checksum: String): BrowserOriginalState {
        validateChecksum(checksum)
        return when (operation("status", id, null, size, data = checksum.lowercase())) {
            "ready" -> BrowserOriginalState.Published
            "recoverable" -> BrowserOriginalState.CompletionRequested
            "pending" -> BrowserOriginalState.Pending
            "missing" -> BrowserOriginalState.Missing
            else -> error("Invalid original status")
        }
    }
    suspend fun begin(id: String, token: String, size: Long) { operation("begin", id, token, size) }
    suspend fun resumePrefix(id: String, token: String, size: Long): Long =
        requireNotNull(operation("resume", id, token, size)).toLong().also { require(it in 0..size) }
    suspend fun verifyPrefixChunk(id: String, token: String, size: Long, offset: Long, expected: ByteArray) {
        require(read(id, token, size, offset, null).contentEquals(expected)) { "Original prefix differs" }
    }
    suspend fun write(id: String, token: String, size: Long, offset: Long, bytes: ByteArray) {
        require(bytes.size in 1..DefaultAssetUploadChunkBytes && bytes.size <= size - offset)
        operation("write", id, token, size, offset, Base64.encode(bytes))
    }
    private suspend fun read(id: String, token: String?, size: Long, offset: Long, checksum: String?): ByteArray {
        val raw = checkNotNull(operation("read", id, token, size, offset, checksum))
        require(raw.length <= 4 * ((DefaultAssetUploadChunkBytes + 2) / 3))
        return Base64.decode(raw).also { require(it.size in 1..DefaultAssetUploadChunkBytes && it.size <= size - offset) }
    }
    suspend fun verifyPending(id: String, token: String, size: Long, checksum: String) {
        validateChecksum(checksum)
        val digest = SHA256(); var offset = 0L
        while (offset < size) {
            currentCoroutineContext().ensureActive()
            val bytes = read(id, token, size, offset, null)
            digest.update(bytes); offset += bytes.size
        }
        check(hash(digest) == checksum.lowercase()) { "Original checksum mismatch" }
    }
    suspend fun requestCompletion(id: String, token: String, size: Long, checksum: String) {
        validateChecksum(checksum)
        operation("requestSeal", id, token, size, data = checksum.lowercase())
    }
    suspend fun publish(id: String, token: String, size: Long, checksum: String) {
        verifyPending(id, token, size, checksum)
        requestCompletion(id, token, size, checksum)
        operation("seal", id, token, size, data = checksum.lowercase())
    }
    suspend fun download(id: String, size: Long, checksum: String, onChunk: suspend (ByteArray) -> Unit) {
        validateChecksum(checksum)
        val digest = SHA256(); var offset = 0L
        while (offset < size) {
            currentCoroutineContext().ensureActive()
            val bytes = read(id, null, size, offset, checksum.lowercase())
            digest.update(bytes); onChunk(bytes); offset += bytes.size
        }
        check(hash(digest) == checksum.lowercase()) { "Original checksum mismatch" }
    }
    suspend fun abandon(id: String, token: String, size: Long) { operation("remove", id, token, size) }
    private fun validateChecksum(value: String) { require(value.matches(Regex("sha256:[0-9a-fA-F]{64}"))) }
    private fun hash(digest: SHA256) = "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
internal expect fun browserOriginalOperation(requestId: String, action: String, id: String, token: String?, offset: String, size: String, data: String?, done: (String?, String?) -> Unit)
internal expect fun browserCancelOriginalOperation(requestId: String)
