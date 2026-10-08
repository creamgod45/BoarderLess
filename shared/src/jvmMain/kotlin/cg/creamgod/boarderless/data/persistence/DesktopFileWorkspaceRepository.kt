package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import cg.creamgod.boarderless.domain.model.WorkspaceId
import java.nio.file.Path
import java.time.Instant

private class DesktopLocalDocuments(loadFiles: () -> DesktopAtomicDraftStore) {
    val files by lazy(loadFiles)
    private val lock = object : LocalDocumentLock {
        override fun <T> withLock(block: () -> T): T = synchronized(this) { block() }
    }
    val repository = PersistentLocalWorkspaceRepository({
        object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? = files.read(key)?.let {
                DurableLocalRecord(it.generation, requireNotNull(it.payload) { "Local documents were removed" })
            }
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                files.compareAndSet(key, generation, payload)
            }
            override fun confirmDurable(key: String, generation: Long) { files.confirmDurable(key, generation) }
        }
    }, lock, { Instant.now().toString() })
}

/** Desktop gateways around the shared local-document engine; the on-disk format is unchanged. */
class DesktopFileWorkspaceRepository private constructor(
    private val storage: DesktopLocalDocuments,
    assetRoot: Path? = null,
) : CanvasWorkspaceRepository by storage.repository {
    constructor(rootPath: Path) : this(DesktopLocalDocuments { DesktopAtomicDraftStore(rootPath) }, rootPath.resolve("assets"))
    internal constructor(files: DesktopAtomicDraftStore) : this(DesktopLocalDocuments { files })

    override val quickSchemeRepository by lazy {
        assetRoot?.let { root -> DesktopFileQuickSchemeStore(root.parent.resolve("schemes")) { storage.files; Unit } }
    }
    override val localAssetGateway by lazy {
        assetRoot?.let { root -> DesktopFileAssetGateway(root) { session -> refresh(session); Unit } }
    }
    override suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset> {
        refresh(session)
        return localAssetGateway?.list(session).orEmpty()
    }
    override suspend fun getAsset(session: WorkspaceSession, assetId: String): WorkspaceAsset =
        requireNotNull(localAssetGateway).recover(session, assetId)
}
