package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository

enum class WorkspaceStorageMode(val token: String) {
    Local("local"), Server("server");
    companion object { fun fromToken(token: String?): WorkspaceStorageMode? = entries.firstOrNull { it.token == token } }
}

/** Platform factories; selecting a mode never migrates documents or imports another mode's data. */
class WorkspaceStorageRuntime(
    val localRepository: () -> CanvasWorkspaceRepository,
    val mediaRuntimeFor: (CanvasWorkspaceRepository) -> MediaImportRuntime,
    val serverRepository: () -> CanvasWorkspaceRepository = { BackendWorkspaceRepository() },
)

/** Reuse platform-owned local gateways. A missing local download capability is an error, never HTTP fallback. */
suspend fun <T> withWorkspaceAssetDownloadGateway(repository: CanvasWorkspaceRepository, block: suspend (AssetDownloadGateway) -> T): T {
    val local = repository.localAssetGateway
    if (local != null) return block(requireNotNull(local as? AssetDownloadGateway))
    check(!repository.isLocalOnly) { "Local downloads require a platform gateway" }
    val remote = BackendAssetTransferGateway()
    try { return block(remote) } finally { remote.close() }
}
