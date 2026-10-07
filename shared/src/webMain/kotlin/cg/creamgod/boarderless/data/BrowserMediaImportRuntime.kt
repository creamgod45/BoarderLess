package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

/** JS and Wasm share a small DOM bridge; browser File handles never enter canvas payloads. */
fun browserMediaImportRuntime(activity: StateFlow<MediaPlaybackActivity>? = null): MediaImportRuntime {
    val previewPermits = Semaphore(2)
    val previewCache = bitmapPreviewCache()
    return MediaImportRuntime(
        clearPreviewCache = previewCache::clear,
        playbackActivity = activity,
        loadGiphyAnimation = { url ->
            cg.creamgod.boarderless.data.remote.loadGiphyAnimation(url) {
                awaitBrowserImageDecoder()
                BrowserGifAnimation.decode(it)
            }
        },
        loadGiphyStill = { url ->
            cg.creamgod.boarderless.data.remote.loadGiphyStill(url) {
                awaitBrowserImageDecoder()
                decodeBrowserPreview(it)
            }
        },
        selectSource = {
            val selection = pickBrowserFile()
            selection?.let { metadata ->
                val source = BrowserAssetTransferSource(metadata)
                try {
                    source.prepare()
                    source
                } catch (error: Throwable) {
                    source.release()
                    throw error
                }
            }
        },
        loadPreview = { session, assetId ->
            previewPermits.withPermit {
                val gateway = BackendAssetTransferGateway()
                try {
                    BrowserAssetPreviewLoader(gateway, previewCache).load(session, assetId)
                } finally {
                    gateway.close()
                }
            }
        },
        loadGif = { session, assetId ->
            previewPermits.withPermit {
                val gateway = BackendAssetTransferGateway()
                try {
                    loadBrowserGif(gateway, session, assetId)
                } finally {
                    gateway.close()
                }
            }
        },
        loadVideo = { session, assetId ->
            previewPermits.withPermit {
                val gateway = BackendAssetTransferGateway()
                try {
                    loadBrowserVideo(gateway, session, assetId)
                } finally {
                    gateway.close()
                }
            }
        },
        videoSurface = { playback, modifier -> BrowserVideoSurface(playback, modifier) },
    )
}

@Serializable
private data class BrowserSelection(
    val fileId: String,
    val name: String,
    val mediaType: String,
    val byteSize: Long,
)

private suspend fun pickBrowserFile(): BrowserSelection? =
    suspendCancellableCoroutine { pending ->
        val requestId = randomUuid()
        var selectedId: String? = null
        pending.invokeOnCancellation {
            browserMediaCancel(requestId)
            selectedId?.let(::browserMediaRelease)
        }
        browserMediaPick(requestId) { result, error ->
            if (error != null) {
                if (pending.isActive) pending.resumeWithException(IllegalStateException(error))
            } else if (result == null) {
                if (pending.isActive) pending.resume(null)
            } else {
                try {
                    val selection = Json.decodeFromString<BrowserSelection>(result)
                    selectedId = selection.fileId
                    if (pending.isActive) pending.resume(selection) else browserMediaRelease(selection.fileId)
                } catch (failure: Exception) {
                    if (pending.isActive) pending.resumeWithException(failure)
                }
            }
        }
    }

private class BrowserAssetTransferSource(
    private val selection: BrowserSelection,
) : DirectAssetUploadSource {
    private var released = false
    override val displayName = selection.name
    override val mediaType = selection.mediaType
    override val byteSize = selection.byteSize
    override var checksum: String = "sha256:preparing"
        private set
    override val width: Int? = null
    override val height: Int? = null
    override val durationMs: Long? = null

    suspend fun prepare() {
        validateAssetTransferSource(this)
        val digest = SHA256()
        var offset = 0L
        while (offset < byteSize) {
            coroutineContext.ensureActive()
            val bytes = readChunk(offset, minOf(DefaultAssetUploadChunkBytes.toLong(), byteSize - offset).toInt())
            if (bytes.isEmpty()) throw AssetImportException(AssetImportIssue.TruncatedSource, "Selected browser file is truncated")
            digest.update(bytes)
            offset += bytes.size
            yield()
        }
        checksum = "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    override suspend fun readChunk(
        offset: Long,
        maximumBytes: Int,
    ): ByteArray {
        require(offset >= 0)
        require(maximumBytes in 1..DefaultAssetUploadChunkBytes)
        check(!released) { "Selected browser file has been released" }
        if (offset >= byteSize) return byteArrayOf()
        return suspendCancellableCoroutine { pending ->
            browserMediaRead(selection.fileId, offset.toDouble(), maximumBytes) { data, error ->
                if (!pending.isActive) return@browserMediaRead
                if (error != null || data == null) {
                    pending.resumeWithException(IllegalStateException(error ?: "Selected file cannot be read"))
                } else {
                    try {
                        pending.resume(Base64.decode(data))
                    } catch (failure: Exception) {
                        pending.resumeWithException(failure)
                    }
                }
            }
        }
    }

    override suspend fun release() {
        if (!released) {
            released = true
            browserMediaRelease(selection.fileId)
        }
    }

    override suspend fun uploadDirect(
        ticket: AssetUploadTicket,
        onProgress: (Long) -> Unit,
    ) {
        check(!released) { "Selected browser file has been released" }
        coroutineContext.ensureActive()
        suspendCancellableCoroutine<Unit> { pending ->
            pending.invokeOnCancellation { browserMediaAbortUpload(selection.fileId) }
            if (pending.isActive) {
                browserMediaUpload(
                    selection.fileId,
                    ticket.uploadUrl,
                    Json.encodeToString(ticket.requiredHeaders),
                    { loaded -> if (pending.isActive) onProgress(loaded.toLong()) },
                    { error ->
                        if (pending.isActive) {
                            if (error == null) {
                                pending.resume(Unit)
                            } else {
                                pending.resumeWithException(IllegalStateException(error))
                            }
                        }
                    },
                )
            }
        }
    }
}

internal expect fun browserMediaPick(
    requestId: String,
    done: (String?, String?) -> Unit,
)

internal expect fun browserMediaRead(
    fileId: String,
    offset: Double,
    maximum: Int,
    done: (String?, String?) -> Unit,
)

internal expect fun browserMediaRelease(fileId: String)

internal expect fun browserMediaCancel(requestId: String)

internal expect fun browserMediaUpload(
    fileId: String,
    url: String,
    headers: String,
    progress: (Double) -> Unit,
    done: (String?) -> Unit,
)

internal expect fun browserMediaAbortUpload(fileId: String)
