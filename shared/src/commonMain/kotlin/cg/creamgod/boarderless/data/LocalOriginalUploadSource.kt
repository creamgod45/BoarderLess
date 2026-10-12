package cg.creamgod.boarderless.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Read the private original through the platform gateway using a bounded queue and
 * bounded current/producer chunks; never materialize the full image/video in memory.
 * The producer is canceled when PUT fails/cancels or the scoped consumer returns. */
internal suspend fun <T> withLocalOriginalUploadSource(repository: CanvasWorkspaceRepository,
    snapshot: LocalWorkspaceSyncSnapshot, request: PreparedAssetUploadRequest,
    block: suspend (AssetTransferSource) -> T): T = coroutineScope {
    request.requireReview(snapshot)
    val gateway = requireNotNull(repository.localAssetGateway as? AssetDownloadGateway)
    require(gateway.downloadServerScope == null)
    val ticket = gateway.authorize(snapshot.local, request.source.id)
    require(MaterializationSourceDescriptor.fromAsset(ticket.asset) == request.source)
    val chunks = Channel<ByteArray>(1)
    val producer = launch {
        try {
            gateway.download(ticket) { bytes ->
                require(bytes.size in 1..DefaultAssetUploadChunkBytes)
                chunks.send(bytes.copyOf())
            }
            chunks.close()
        } catch (failure: Throwable) { chunks.close(failure); throw failure }
    }
    var position = 0L
    var pending = byteArrayOf()
    var pendingOffset = 0
    val source = object : AssetTransferSource {
        override val displayName = request.source.id
        override val mediaType = request.source.mediaType
        override val byteSize = request.source.byteSize
        override val checksum = request.source.checksum
        override val width = request.source.width
        override val height = request.source.height
        override val durationMs = request.source.durationMs
        override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
            require(offset == position && maximumBytes in 1..DefaultAssetUploadChunkBytes)
            if (pendingOffset == pending.size) {
                val received = chunks.receiveCatching()
                received.exceptionOrNull()?.let { throw it }
                pending = received.getOrNull() ?: return byteArrayOf()
                pendingOffset = 0
            }
            val count = minOf(maximumBytes, pending.size - pendingOffset)
            val result = pending.copyOfRange(pendingOffset, pendingOffset + count)
            pendingOffset += count; position += count
            return result
        }
    }
    try { block(source) } finally { producer.cancel(); chunks.cancel() }
}
