package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded LRU; authorization is never cached. Values contain no signed URLs or file handles. */
class AuthorizedAssetPreviewCache<T : Any>(
    private val maximumBytes: Long = 64L * 1024 * 1024,
    private val maximumEntries: Int = 64,
    private val weight: (T) -> Long,
) {
    private data class Key(val user: String, val workspace: String, val asset: WorkspaceAsset)
    private data class Entry<T>(val value: T, val bytes: Long)
    private val guard = Mutex()
    private val stripes = List(16) { Mutex() }
    private val entries = LinkedHashMap<Key, Entry<T>>()
    private var bytes = 0L
    private var generation = 0L

    init { require(maximumBytes > 0 && maximumEntries > 0) }

    suspend fun load(
        gateway: AssetDownloadGateway,
        session: WorkspaceSession,
        assetId: String,
        decode: suspend (AssetDownloadTicket) -> T,
    ): T {
        val startedGeneration = guard.withLock { generation }
        val ticket = gateway.authorize(session, assetId)
        AssetPreviewPolicy.validate(ticket, session, assetId)
        currentCoroutineContext().ensureActive()
        val key = Key(session.userId, session.workspace.id.value, ticket.asset)
        return stripes[(key.hashCode() and Int.MAX_VALUE) % stripes.size].withLock {
            val hit = guard.withLock {
                entries.remove(key)?.also { entries[key] = it }?.value
            }
            if (hit != null) return@withLock hit
            val value = decode(ticket)
            currentCoroutineContext().ensureActive()
            val size = weight(value)
            require(size > 0) { "Preview cache weight must be positive" }
            if (size <= maximumBytes) guard.withLock {
                if (generation == startedGeneration) {
                    while (entries.isNotEmpty() && (bytes > maximumBytes - size || entries.size >= maximumEntries)) {
                        val oldest = entries.keys.first()
                        bytes -= entries.remove(oldest)!!.bytes
                    }
                    entries[key] = Entry(value, size)
                    bytes += size
                }
            }
            value
        }
    }

    suspend fun clear() = guard.withLock {
        entries.clear()
        bytes = 0
        generation++ // A load started before clearing must not repopulate the cache.
    }
}

fun bitmapPreviewCache() = AuthorizedAssetPreviewCache<ImageBitmap>(weight = {
    it.width.toLong() * it.height * 4
})
