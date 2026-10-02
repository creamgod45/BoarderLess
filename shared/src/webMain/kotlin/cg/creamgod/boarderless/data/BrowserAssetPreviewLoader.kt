@file:OptIn(org.jetbrains.skiko.InternalSkikoApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skiko.wasm.awaitSkiko
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded transient memory, never IndexedDB/localStorage or a shared canvas payload. */
internal class BrowserAssetDownloadSink(private val mediaType: String) : AssetDownloadSink {
    private val chunks = mutableListOf<ByteArray>()
    private var size = 0L
    private var verified = false
    private var closed = false
    private val digest = SHA256()

    override suspend fun writeChunk(offset: Long, bytes: ByteArray) {
        check(!closed && !verified)
        require(bytes.isNotEmpty() && offset == size)
        require(bytes.size.toLong() <= AssetPreviewPolicy.MaxEncodedBytes - size)
        coroutineContext.ensureActive()
        chunks += bytes.copyOf()
        digest.update(bytes)
        size += bytes.size
        yield()
    }

    override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference {
        check(!closed && !verified && expectedByteSize > 0 && size == expectedByteSize)
        coroutineContext.ensureActive()
        val actual = "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        check(actual.equals(expectedChecksum, ignoreCase = true)) { "Downloaded preview checksum mismatch" }
        verified = true
        return LocalAssetReference("browser:${randomUuid()}", mediaType)
    }

    suspend fun takeVerifiedBytes(): ByteArray {
        check(verified && !closed)
        val result = ByteArray(size.toInt())
        var offset = 0
        try {
            for (chunk in chunks) {
                coroutineContext.ensureActive()
                chunk.copyInto(result, offset)
                offset += chunk.size
                yield()
            }
            return result
        } finally { release() }
    }

    override suspend fun abort() { release() }

    fun release() {
        chunks.clear()
        size = 0
        closed = true
    }
}

class BrowserAssetPreviewLoader(
    private val gateway: AssetDownloadGateway,
    private val cache: AuthorizedAssetPreviewCache<ImageBitmap> = bitmapPreviewCache(),
) {
    suspend fun load(session: WorkspaceSession, assetId: String): ImageBitmap {
        return cache.load(gateway, session, assetId) { ticket -> loadAuthorized(session, assetId, ticket) }
    }

    private suspend fun loadAuthorized(session: WorkspaceSession, assetId: String, ticket: AssetDownloadTicket): ImageBitmap {
        val sink = BrowserAssetDownloadSink(ticket.asset.mediaType)
        try {
            val authorizedGateway = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) =
                    gateway.download(ticket, onChunk)
            }
            AssetDownloadCoordinator(authorizedGateway).download(session, assetId, sink)
            val bytes = sink.takeVerifiedBytes()
            awaitBrowserImageDecoder()
            coroutineContext.ensureActive()
            return decodePreview(bytes).also { coroutineContext.ensureActive() }
        } finally { sink.release() }
    }

    private fun decodePreview(bytes: ByteArray): ImageBitmap {
        val data = Data.makeFromBytes(bytes)
        try {
            val codec = Codec.makeFromData(data)
            try {
                AssetPreviewPolicy.validateDimensions(codec.size.x, codec.size.y)
                val bitmap = codec.readPixels()
                try { return Image.makeFromBitmap(bitmap).toComposeImageBitmap() }
                finally { bitmap.close() }
            } finally { codec.close() }
        } finally { data.close() }
    }
}

internal suspend fun awaitBrowserImageDecoder(): Unit = suspendCancellableCoroutine { pending ->
    awaitSkiko.then(
        onFulfilled = { if (pending.isActive) pending.resume(Unit); null },
        onRejected = { if (pending.isActive) pending.resumeWithException(IllegalStateException("Image decoder is unavailable")); null },
    )
}
