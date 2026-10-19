package cg.creamgod.boarderless.data.persistence

import platform.Foundation.NSRecursiveLock

internal fun iosFileQuickSchemeStore(): QuickSchemeRepository = IosFileQuickSchemeStore(iosPrivateRecordRoot("quick-schemes"))

internal class IosFileQuickSchemeStore(root: String) : QuickSchemeRepository {
    override val supportsIdentifiedSave = true
    override fun pendingIdentifiedSaves() = locked { delegate.pendingIdentifiedSaves() }
    override fun stageIdentifiedSave(intent: QuickSchemeSaveIntent) = locked { delegate.stageIdentifiedSave(intent) }
    override fun saveIdentified(requestId: String, payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        locked { delegate.saveIdentified(requestId, payload, name, schemaVersion, sourceWorkspaceId) }
    private val lock = NSRecursiveLock()
    private val records = IosAtomicLocalRecordStore(root, 4 * 1024 * 1024)
    private val delegate = PersistentQuickSchemeStore(records)
    override val cloudSyncRuntime = CloudSchemeRuntime { records.backgroundAwaited() }
    private fun <T> locked(block: () -> T): T { lock.lock(); try { return block() } finally { lock.unlock() } }
    override fun list(): List<QuickScheme> = locked { delegate.list() }
    override fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        locked { delegate.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override fun rename(id: Int, name: String): QuickScheme? = locked { delegate.rename(id, name) }
    override fun delete(id: Int): Boolean = locked { delegate.delete(id) }
}
