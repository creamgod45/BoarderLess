package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native catalog codec over a private calculation snapshot. A result is published only after
 * the actual generation CAS and durable confirmation, including retries of uncertain publication.
 * Ready still requires the gateway to verify and durably publish the original beforehand.
 */
internal class AwaitedLocalAssetCatalog(private val files: AwaitedDurableLocalRecordStore) {
    private val mutex = Mutex()
    private suspend fun <T> transaction(confirmExisting: Boolean = false, operation: (PersistentLocalAssetCatalog) -> T): T = mutex.withLock {
        val key = PersistentLocalAssetCatalog.Key
        val before = files.read(key)
        currentCoroutineContext().ensureActive()
        var staged: ByteArray? = null
        var confirmation: Long? = null
        val snapshot = object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? {
                check(key == PersistentLocalAssetCatalog.Key)
                return before?.copy(payload = before.payload.copyOf())
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                check(key == PersistentLocalAssetCatalog.Key && generation == before?.generation && staged == null)
                check(generation == null || generation in 1 until 9_007_199_254_740_991L)
                staged = payload.copyOf()
            }
            override fun confirmDurable(key: String, generation: Long) {
                check(key == PersistentLocalAssetCatalog.Key && generation == before?.generation)
                confirmation = generation
            }
        }
        val lock = object : LocalDocumentLock {
            override fun <R> withLock(block: () -> R): R = block()
        }
        val result = operation(PersistentLocalAssetCatalog({ snapshot }, lock))
        currentCoroutineContext().ensureActive()
        val payload = staged
        if (payload != null) {
            files.compareAndSet(key, before?.generation, payload)
            currentCoroutineContext().ensureActive()
            files.confirmDurable(key, (before?.generation ?: 0L) + 1)
        } else {
            val generation = confirmation ?: before?.generation?.takeIf { confirmExisting }
            generation?.let { files.confirmDurable(key, it) }
        }
        currentCoroutineContext().ensureActive()
        result
    }
    suspend fun get(id: String): WorkspaceAsset = transaction { it.get(id) }
    suspend fun ensurePinned(asset: WorkspaceAsset): WorkspaceAsset = transaction { it.ensurePinned(asset) }
    suspend fun list(session: WorkspaceSession): List<WorkspaceAsset> = transaction { it.list(session) }
    suspend fun add(asset: WorkspaceAsset) = transaction(confirmExisting = true) { catalog ->
        val existing = try { catalog.get(asset.id) } catch (_: NoSuchElementException) { null }
        if (existing == null) catalog.add(asset)
        else require(existing == asset && asset.status == AssetStatus.Pending) { "Asset identity changed" }
    }
    suspend fun ready(expected: WorkspaceAsset): WorkspaceAsset = transaction { it.ready(expected) }
    suspend fun abandon(expected: WorkspaceAsset) = transaction { it.abandon(expected) }
}
