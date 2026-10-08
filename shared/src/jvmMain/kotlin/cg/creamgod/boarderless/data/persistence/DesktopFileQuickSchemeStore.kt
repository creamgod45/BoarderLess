package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import java.nio.file.Path

/** Device library saved as one private, checksummed atomic record. */
class DesktopFileQuickSchemeStore private constructor(loadFiles: () -> DesktopAtomicDraftStore) : QuickSchemeRepository {
    constructor(root: Path, ensureParent: () -> Unit = {}) : this({ ensureParent(); DesktopAtomicDraftStore(root) })
    internal constructor(files: DesktopAtomicDraftStore) : this({ files })
    private val delegate by lazy {
        val files = loadFiles()
        PersistentQuickSchemeStore(object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? = files.read(key)?.let {
                DurableLocalRecord(it.generation, requireNotNull(it.payload) { "Local scheme library was removed" })
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) { files.compareAndSet(key, generation, payload) }
            override fun confirmDurable(key: String, generation: Long) { files.confirmDurable(key, generation) }
        })
    }
    override fun list(): List<QuickScheme> = synchronized(this) { delegate.list() }
    override fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        synchronized(this) { delegate.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override fun rename(id: Int, name: String): QuickScheme? = synchronized(this) { delegate.rename(id, name) }
    override fun delete(id: Int): Boolean = synchronized(this) { delegate.delete(id) }
}
