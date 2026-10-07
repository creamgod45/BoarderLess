package cg.creamgod.boarderless.data.remote

import androidx.compose.ui.graphics.ImageBitmap
import cg.creamgod.boarderless.data.GifAnimation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Shared in-flight bound, not a reusable content cache. Queued cancellation never starts a download. */
class GiphyPreviewGate(limit: Int = 3) {
    private val permits = Semaphore(limit)
    suspend fun <T> load(block: suspend () -> T): T = permits.withPermit {
        currentCoroutineContext().ensureActive()
        block() // Resource-producing callers must check cancellation before transferring ownership.
    }
}
private val previewGate = GiphyPreviewGate()

/** Display-only bytes; no application-managed media cache, files, proxy or workspace credentials. */
class GiphyMediaClient(private val client: HttpClient = HttpClient {
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = 10_000 }
}) {
    suspend fun downloadStill(url: String): ByteArray {
        require(isGiphyMediaUrl(url)) { "Unsupported provider media URL" }
        currentCoroutineContext().ensureActive()
        try {
            return client.prepareGet(url).execute { response ->
                currentCoroutineContext().ensureActive()
                if (response.status.value != 200) throw GiphyException(GiphyIssue.Unavailable)
                val declaredSize = response.headers["Content-Length"]?.toLongOrNull()
                if (declaredSize != null && declaredSize !in 1..MaxStillBytes.toLong())
                    throw GiphyException(GiphyIssue.InvalidResponse)
                val mime = response.headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase()
                if (mime != null && mime !in setOf("image/gif", "image/png", "image/jpeg", "image/webp"))
                    throw GiphyException(GiphyIssue.InvalidResponse)
                val chunks = mutableListOf<ByteArray>()
                var size = 0
                val buffer = ByteArray(16 * 1024)
                val channel = response.bodyAsChannel()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count > MaxStillBytes - size) throw GiphyException(GiphyIssue.InvalidResponse)
                    if (count > 0) { chunks += buffer.copyOf(count); size += count }
                    yield()
                }
                if (size == 0) throw GiphyException(GiphyIssue.InvalidResponse)
                val encoded = ByteArray(size)
                var offset = 0
                chunks.forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    chunk.copyInto(encoded, offset); offset += chunk.size
                    yield()
                }
                currentCoroutineContext().ensureActive()
                encoded
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failed: GiphyException) { throw failed }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); throw GiphyException(GiphyIssue.Unavailable) }
    }
    fun close() = client.close()
    companion object { const val MaxStillBytes = 2 * 1024 * 1024 }
}

/** Ownership lasts for this preview request only; decoded display resources are owned by the UI. */
suspend fun loadGiphyStill(url: String, decode: suspend (ByteArray) -> ImageBitmap): ImageBitmap = previewGate.load {
    val client = GiphyMediaClient()
    try {
        val bytes = client.downloadStill(url)
        currentCoroutineContext().ensureActive()
        decode(bytes).also { currentCoroutineContext().ensureActive() }
    } finally { client.close() }
}

/** Transfer decoder ownership only after cancellation checks; no intermediate file or reusable cache. */
suspend fun loadGiphyAnimation(
    url: String,
    client: GiphyMediaClient = GiphyMediaClient(),
    decode: suspend (ByteArray) -> GifAnimation,
): GifAnimation {
    try {
        return previewGate.load {
            var opened: GifAnimation? = null
            try {
                val bytes = client.downloadStill(url)
                currentCoroutineContext().ensureActive()
                val animation = decode(bytes).also { opened = it }
                currentCoroutineContext().ensureActive()
                animation
            } catch (failed: Throwable) {
                // Preserve the original failure even if native cleanup fails.
                withContext(NonCancellable) { runCatching { opened?.release() } }
                throw failed
            }
        }
    } finally { client.close() }
}
