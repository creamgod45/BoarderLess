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
    val serverRepositoryWithSubmission: ((cg.creamgod.boarderless.data.remote.AwaitedSubmissionRuntime) -> CanvasWorkspaceRepository)? = null,
    /** Explicit host launch choice (e.g. Desktop --server), never the old saved preference. */
    val initialMode: WorkspaceStorageMode = WorkspaceStorageMode.Local,
) {
    /** Host-only adoption after real publication/activation and writer drain. Never initialize
     * a runtime here or fall back to a legacy writer. The host retains the activation lease
     * through repository close and logical + platform physical I/O drain. No automatic switch. */
    internal fun createActivatedServerRepository(
        activation: cg.creamgod.boarderless.data.remote.ActivatedAwaitedSubmission,
        scope: cg.creamgod.boarderless.data.remote.PendingSubmissionScope,
    ): CanvasWorkspaceRepository {
        activation.runtime.requireActive(scope)
        val factory = requireNotNull(serverRepositoryWithSubmission) { "Activated server factory unavailable" }
        val candidate = factory(activation.runtime)
        try {
            activation.runtime.requireActive(scope)
            requireNotNull(candidate as? BackendWorkspaceRepository) { "Activated server adapter unavailable" }
                .requireActivatedSubmissionRuntime(activation.runtime, scope)
            return candidate
        } catch (failure: Throwable) {
            candidate.close()
            throw failure
        }
    }
    /** Ordinary startup stays local; only a host's explicit launch can override it. */
    fun createRepository(mode: WorkspaceStorageMode = initialMode): CanvasWorkspaceRepository =
        when (mode) {
            WorkspaceStorageMode.Local -> localRepository()
            WorkspaceStorageMode.Server -> serverRepository()
        }
}

/** Reuse platform-owned local gateways. A missing local download capability is an error, never HTTP fallback. */
suspend fun <T> withWorkspaceAssetDownloadGateway(repository: CanvasWorkspaceRepository, block: suspend (AssetDownloadGateway) -> T): T {
    val local = repository.localAssetGateway
    if (local != null) return block(requireNotNull(local as? AssetDownloadGateway))
    check(!repository.isLocalOnly) { "Local downloads require a platform gateway" }
    val remote = requireNotNull(repository.syncAssetGatewayFactory) { "Configured remote originals unavailable" }.invoke()
    try {
        val download = requireNotNull(remote as? AssetDownloadGateway)
        require(download.downloadServerScope != null && download.downloadServerScope == repository.syncServerScope)
        return block(download)
    } finally { remote.close() }
}
