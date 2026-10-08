package cg.creamgod.boarderless.data.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AwaitedQuickSchemeRepository {
    suspend fun list(): List<QuickScheme>
    suspend fun save(payload: String, name: String? = null, schemaVersion: Int = 1, sourceWorkspaceId: String? = null): QuickScheme
    suspend fun rename(id: Int, name: String): QuickScheme?
    suspend fun delete(id: Int): Boolean
}

/** Existing native repositories retain their exact file format and forced publication behavior. */
fun QuickSchemeRepository.awaited(): AwaitedQuickSchemeRepository = object : AwaitedQuickSchemeRepository {
    override suspend fun list() = withContext(Dispatchers.Default) { this@awaited.list() }
    override suspend fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?) =
        withContext(Dispatchers.Default) { this@awaited.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override suspend fun rename(id: Int, name: String) = withContext(Dispatchers.Default) { this@awaited.rename(id, name) }
    override suspend fun delete(id: Int) = withContext(Dispatchers.Default) { this@awaited.delete(id) }
}

/** Reuse the native library codec/validation over one loaded transaction snapshot.
 * The snapshot is working data only; no result escapes before the actual durable CAS and ACK.
 */
internal class AwaitedPersistentQuickSchemeStore(private val files: AwaitedDurableLocalRecordStore) : AwaitedQuickSchemeRepository {
    private val mutex = Mutex()
    private suspend fun <T> transaction(change: Boolean, operation: (QuickSchemeRepository) -> T): T = mutex.withLock {
        val key = PersistentQuickSchemeStore.Key
        val before = files.read(key)
        var staged: ByteArray? = null
        val snapshot = object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? {
                check(key == PersistentQuickSchemeStore.Key)
                return before?.copy(payload = before.payload.copyOf())
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                check(change && key == PersistentQuickSchemeStore.Key && generation == before?.generation && staged == null)
                staged = payload.copyOf()
            }
            override fun confirmDurable(key: String, generation: Long) = error("Snapshot is not durable storage")
        }
        val result = operation(PersistentQuickSchemeStore(snapshot))
        staged?.let {
            files.compareAndSet(key, before?.generation, it)
            files.confirmDurable(key, (before?.generation ?: 0L) + 1)
        }
        result
    }
    override suspend fun list(): List<QuickScheme> = transaction(false) { it.list() }
    override suspend fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        transaction(true) { it.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override suspend fun rename(id: Int, name: String): QuickScheme? = transaction(true) { it.rename(id, name) }
    override suspend fun delete(id: Int): Boolean = transaction(true) { it.delete(id) }
}
