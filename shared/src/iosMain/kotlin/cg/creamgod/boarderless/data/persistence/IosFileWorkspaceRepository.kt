package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import platform.Foundation.NSRecursiveLock
import kotlin.time.Clock

/** Private Library documents; media access is supplied separately by the platform. */
internal fun iosFileWorkspaceRepository(): CanvasWorkspaceRepository =
    IosFileWorkspaceRepository(iosPrivateRecordRoot("local-documents"))

private class IosLocalDocuments(val root: String) {
    val repository = PersistentLocalWorkspaceRepository(
        { IosAtomicLocalRecordStore(root, 4 * 1024 * 1024) },
        object : LocalDocumentLock {
            private val lock = NSRecursiveLock()
            override fun <T> withLock(block: () -> T): T {
                lock.lock()
                try { return block() } finally { lock.unlock() }
            }
        },
        { Clock.System.now().toString() },
    )
}

internal class IosFileWorkspaceRepository private constructor(private val documents: IosLocalDocuments) :
    CanvasWorkspaceRepository by documents.repository {
    constructor(root: String) : this(IosLocalDocuments(root))
    override val localAssetGateway by lazy {
        IosFileAssetGateway("${documents.root}/assets") { session -> refresh(session); Unit }
    }
    override suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset> = localAssetGateway.list(session)
    override suspend fun getAsset(session: WorkspaceSession, assetId: String): WorkspaceAsset = localAssetGateway.recover(session, assetId)
}
