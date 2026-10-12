package cg.creamgod.boarderless.data.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AwaitedQuickSchemeRepository {
    val cloudSyncRuntime: CloudSchemeRuntime? get() = null
    suspend fun pendingIdentifiedSaves(): List<QuickSchemeSaveIntent> = emptyList()
    suspend fun stageIdentifiedSave(intent: QuickSchemeSaveIntent): Unit = error("Identified scheme save unavailable")
    val supportsIdentifiedSave: Boolean get() = false
    suspend fun saveIdentified(requestId: String, payload: String, name: String? = null, schemaVersion: Int = 1,
        sourceWorkspaceId: String? = null): QuickScheme = error("Identified scheme save unavailable")
    suspend fun list(): List<QuickScheme>
    suspend fun save(payload: String, name: String? = null, schemaVersion: Int = 1, sourceWorkspaceId: String? = null): QuickScheme
    suspend fun rename(id: Int, name: String): QuickScheme?
    suspend fun delete(id: Int): Boolean
}

/** Existing native repositories retain their exact file format and forced publication behavior. */
fun QuickSchemeRepository.awaited(): AwaitedQuickSchemeRepository = object : AwaitedQuickSchemeRepository {
    override val cloudSyncRuntime get() = this@awaited.cloudSyncRuntime
    override suspend fun pendingIdentifiedSaves() = withContext(Dispatchers.Default) { this@awaited.pendingIdentifiedSaves() }
    override suspend fun stageIdentifiedSave(intent: QuickSchemeSaveIntent) = withContext(Dispatchers.Default) { this@awaited.stageIdentifiedSave(intent) }
    override val supportsIdentifiedSave get() = this@awaited.supportsIdentifiedSave
    override suspend fun saveIdentified(requestId: String, payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?) =
        withContext(Dispatchers.Default) { this@awaited.saveIdentified(requestId, payload, name, schemaVersion, sourceWorkspaceId) }
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
    override val cloudSyncRuntime = CloudSchemeRuntime { files }
    override suspend fun pendingIdentifiedSaves() = transaction(false) { it.pendingIdentifiedSaves() }
    override suspend fun stageIdentifiedSave(intent: QuickSchemeSaveIntent) = transaction(true) { it.stageIdentifiedSave(intent) }
    override val supportsIdentifiedSave = true
    private val mutex = Mutex()
    private suspend fun <T> transaction(change: Boolean, operation: (QuickSchemeRepository) -> T): T = mutex.withLock {
        val key = PersistentQuickSchemeStore.Key
        val before = files.read(key)
        var staged: ByteArray? = null
        var confirmed: Long? = null
        val snapshot = object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? {
                check(key == PersistentQuickSchemeStore.Key)
                return before?.copy(payload = before.payload.copyOf())
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                check(change && key == PersistentQuickSchemeStore.Key && generation == before?.generation && staged == null)
                staged = payload.copyOf()
            }
            override fun confirmDurable(key: String, generation: Long) {
                check(key == PersistentQuickSchemeStore.Key && generation == before?.generation && staged == null)
                confirmed = generation
            }
        }
        val result = operation(PersistentQuickSchemeStore(snapshot))
        staged?.let {
            files.compareAndSet(key, before?.generation, it)
            files.confirmDurable(key, (before?.generation ?: 0L) + 1)
        }
        if (staged == null) confirmed?.let { files.confirmDurable(key, it) }
        result
    }
    override suspend fun list(): List<QuickScheme> = transaction(false) { it.list() }
    override suspend fun saveIdentified(requestId: String, payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?) =
        transaction(true) { it.saveIdentified(requestId, payload, name, schemaVersion, sourceWorkspaceId) }
    override suspend fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        transaction(true) { it.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override suspend fun rename(id: Int, name: String): QuickScheme? = transaction(true) { it.rename(id, name) }
    override suspend fun delete(id: Int): Boolean = transaction(true) { it.delete(id) }
}
