package cg.creamgod.boarderless.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal const val AssetDownloadCleanupTimeoutMillis: Long = 5_000

data class AssetDownloadTicket(
    val asset: WorkspaceAsset,
    val downloadUrl: String,
    val requiredHeaders: Map<String, String> = emptyMap(),
) {
    init {
        require(downloadUrl.isNotBlank()) { "asset download URL must not be blank" }
    }
}

/** Opaque, device-local cache handle. It must never be written to a Workspace operation. */
data class LocalAssetReference(
    val token: String,
    val mediaType: String,
) {
    init {
        require(token.isNotBlank()) { "local asset token must not be blank" }
        require(mediaType.isNotBlank()) { "local asset media type must not be blank" }
    }
}

interface AssetDownloadSink {
    suspend fun writeChunk(offset: Long, bytes: ByteArray)

    /** Atomically exposes the completed cache entry after size/checksum verification. */
    suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference

    suspend fun abort()
}

interface AssetDownloadGateway {
    suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket

    suspend fun download(
        ticket: AssetDownloadTicket,
        onChunk: suspend (bytes: ByteArray) -> Unit,
    )
}

sealed interface AssetDownloadStage {
    data object Authorizing : AssetDownloadStage
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : AssetDownloadStage
    data object Verifying : AssetDownloadStage
    data class Ready(val local: LocalAssetReference) : AssetDownloadStage
}

enum class AssetDownloadIssue {
    BlankAssetId,
    WrongWorkspace,
    NotReady,
    EmptyChunk,
    TooManyBytes,
    Truncated,
}

class AssetDownloadException(
    val issue: AssetDownloadIssue,
    message: String,
) : IllegalStateException(message)

class AssetDownloadCoordinator(
    private val gateway: AssetDownloadGateway,
) {
    suspend fun download(
        session: WorkspaceSession,
        assetId: String,
        sink: AssetDownloadSink,
        onStage: (AssetDownloadStage) -> Unit = {},
    ): LocalAssetReference {
        if (assetId.isBlank()) {
            throw AssetDownloadException(AssetDownloadIssue.BlankAssetId, "Asset id must not be blank")
        }
        val ownerContext = currentCoroutineContext()
        var committed = false
        try {
            currentCoroutineContext().ensureActive()
            onStage(AssetDownloadStage.Authorizing)
            currentCoroutineContext().ensureActive()
            val ticket = gateway.authorize(session, assetId.trim())
            currentCoroutineContext().ensureActive()
            validateTicket(ticket, session, assetId.trim())
            var offset = 0L
            gateway.download(ticket) { bytes ->
                // Check the consumer, not a native transport's potentially NonCancellable callback.
                ownerContext.ensureActive()
                if (bytes.isEmpty()) {
                    throw AssetDownloadException(AssetDownloadIssue.EmptyChunk, "Download returned an empty data chunk")
                }
                val nextOffset = offset + bytes.size
                if (nextOffset > ticket.asset.byteSize) {
                    throw AssetDownloadException(
                        AssetDownloadIssue.TooManyBytes,
                        "Download exceeded declared asset size",
                    )
                }
                sink.writeChunk(offset, bytes)
                ownerContext.ensureActive()
                offset = nextOffset
                onStage(AssetDownloadStage.Downloading(offset, ticket.asset.byteSize))
            }
            currentCoroutineContext().ensureActive()
            if (offset != ticket.asset.byteSize) {
                throw AssetDownloadException(
                    AssetDownloadIssue.Truncated,
                    "Download ended at $offset of ${ticket.asset.byteSize} bytes",
                )
            }
            onStage(AssetDownloadStage.Verifying)
            currentCoroutineContext().ensureActive()
            val local = sink.commit(ticket.asset.byteSize, ticket.asset.checksum)
            committed = true
            // A native commit can finish after cancellation. Keep its verified cache entry,
            // but do not hand it to a cancelled consumer or emit Ready.
            currentCoroutineContext().ensureActive()
            onStage(AssetDownloadStage.Ready(local))
            currentCoroutineContext().ensureActive()
            return local
        } catch (error: Throwable) {
            if (!committed) {
                withContext(NonCancellable) {
                    // Give cancellation-safe cleanup a bounded wait, without replacing the
                    // transfer/verification error or publishing a partially written entry.
                    withTimeoutOrNull(AssetDownloadCleanupTimeoutMillis) {
                        runCatching { sink.abort() }
                    }
                }
            }
            if (error is CancellationException) throw error
            throw error
        }
    }

    private fun validateTicket(
        ticket: AssetDownloadTicket,
        session: WorkspaceSession,
        requestedAssetId: String,
    ) {
        if (ticket.asset.id != requestedAssetId || ticket.asset.workspaceId != session.workspace.id) {
            throw AssetDownloadException(
                AssetDownloadIssue.WrongWorkspace,
                "Authorized asset does not match the current Workspace request",
            )
        }
        if (ticket.asset.status != AssetStatus.Ready) {
            throw AssetDownloadException(
                AssetDownloadIssue.NotReady,
                "Asset ${ticket.asset.id} is not ready for download",
            )
        }
    }
}
