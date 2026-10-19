package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

internal class BrowserExportImageSink(private val type: String, private val expected: Long) : AssetDownloadSink {
    private val id = randomUuid()
    private val digest = SHA256()
    private var offset = 0L
    private var verified = false
    private var closed = false
    init { browserExportImageBegin(id, type, expected.toDouble()) }
    override suspend fun writeChunk(offset: Long, bytes: ByteArray) {
        check(!closed && !verified && offset == this.offset)
        require(bytes.isNotEmpty() && offset + bytes.size <= expected)
        currentCoroutineContext().ensureActive()
        var position = 0
        while (position < bytes.size) {
            currentCoroutineContext().ensureActive()
            val end = minOf(position + 1024 * 1024, bytes.size)
            val chunk = bytes.copyOfRange(position, end)
            digest.update(chunk)
            browserExportImageAppend(id, Base64.encode(chunk))
            this.offset += chunk.size; position = end
            yield()
        }
    }
    override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference {
        check(!closed && !verified && offset == expected && expectedByteSize == expected)
        currentCoroutineContext().ensureActive()
        val actual = "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        check(actual.equals(expectedChecksum, true)) { "Downloaded export original checksum mismatch" }
        verified = true
        return LocalAssetReference("browser-export:$id", type)
    }
    suspend fun frame(width: Int, height: Int): ImageBitmap {
        check(verified && !closed)
        val encoded = suspendCancellableCoroutine<String> { request ->
            request.invokeOnCancellation { release() }
            try {
                browserExportImageDecode(id, width, height) { result, error ->
                    if (request.isActive) {
                        if (error == null && result != null) request.resume(result)
                        else request.resumeWithException(IllegalStateException("Image export decode failed"))
                    }
                }
            } catch (failure: Throwable) { if (request.isActive) request.resumeWithException(failure) }
        }
        currentCoroutineContext().ensureActive()
        awaitBrowserImageDecoder()
        currentCoroutineContext().ensureActive()
        return decodeBrowserPreview(Base64.decode(encoded)).also {
            check(it.width in 1..width && it.height in 1..height && it.width.toLong() * it.height <= 4_194_304)
            currentCoroutineContext().ensureActive()
        }
    }
    override suspend fun abort() { release() }
    fun release() { if (!closed) { closed = true; browserExportImageRelease(id) } }
}

internal suspend fun loadBrowserExportImage(gateway: AssetDownloadGateway, session: WorkspaceSession,
                                          assetId: String, width: Int, height: Int): ImageBitmap {
    imageExportSize(1, 1, width, height)
    val ticket = gateway.authorize(session, assetId)
    currentCoroutineContext().ensureActive()
    require(ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id && ticket.asset.status == AssetStatus.Ready)
    require(ticket.asset.mediaType in setOf("image/png", "image/jpeg", "image/webp") && ticket.asset.byteSize in 1..MaxWorkspaceAssetBytes)
    val sink = BrowserExportImageSink(ticket.asset.mediaType, ticket.asset.byteSize)
    try {
        val authorized = object : AssetDownloadGateway {
            override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
            override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
        }
        AssetDownloadCoordinator(authorized).download(session, assetId, sink)
        return sink.frame(width, height)
    } finally { sink.release() }
}

internal expect fun browserExportImageBegin(id: String, type: String, expected: Double)
internal expect fun browserExportImageAppend(id: String, base64: String)
internal expect fun browserExportImageDecode(id: String, width: Int, height: Int, done: (String?, String?) -> Unit)
internal expect fun browserExportImageRelease(id: String)
