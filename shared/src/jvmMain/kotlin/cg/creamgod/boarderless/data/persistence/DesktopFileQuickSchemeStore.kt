package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import java.nio.file.Path

/** Device library saved as one private, checksummed atomic record. */
class DesktopFileQuickSchemeStore private constructor(loadFiles: () -> DesktopAtomicDraftStore) : QuickSchemeRepository {
    override val supportsIdentifiedSave = true
    override fun pendingIdentifiedSaves() = synchronized(this) { delegate.pendingIdentifiedSaves() }
    override fun stageIdentifiedSave(intent: QuickSchemeSaveIntent) = synchronized(this) { delegate.stageIdentifiedSave(intent) }
    override fun saveIdentified(requestId: String, payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        synchronized(this) { delegate.saveIdentified(requestId, payload, name, schemaVersion, sourceWorkspaceId) }
    constructor(root: Path, ensureParent: () -> Unit = {}) : this({ ensureParent(); DesktopAtomicDraftStore(root) })
    internal constructor(files: DesktopAtomicDraftStore) : this({ files })
    private val records by lazy {
        val files = loadFiles()
        object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? = files.read(key)?.let {
                DurableLocalRecord(it.generation, requireNotNull(it.payload) { "Local scheme library was removed" })
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) { files.compareAndSet(key, generation, payload) }
            override fun confirmDurable(key: String, generation: Long) { files.confirmDurable(key, generation) }
        }
    }
    private val delegate by lazy { PersistentQuickSchemeStore(records) }
    override val cloudSyncRuntime by lazy { CloudSchemeRuntime { records.backgroundAwaited() } }
    override fun list(): List<QuickScheme> = synchronized(this) { delegate.list() }
    override fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        synchronized(this) { delegate.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override fun rename(id: Int, name: String): QuickScheme? = synchronized(this) { delegate.rename(id, name) }
    override fun delete(id: Int): Boolean = synchronized(this) { delegate.delete(id) }
}
