package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.*

/** Native receipt adapters must force the pending identity before completing an asset upload. */
interface SchemeBundleReceipts {
    suspend fun beforeComplete(asset: SchemeBundleAsset, destinationId: String)
    suspend fun ready(asset: SchemeBundleAsset, imported: ImportedMedia)
    suspend fun recovered(asset: SchemeBundleAsset, repository: AssetRepository): ImportedMedia?
}

class SchemeBundleRuntime(
    val chooseSource: (suspend (canRead: () -> Boolean) -> SchemeBundleSource?)? = null,
    val chooseDestination: (suspend (name: String, canWrite: () -> Boolean) -> SchemeBundleDestination?)? = null,
    val receipts: ((WorkspaceSession, String, List<SchemeBundleAsset>) -> SchemeBundleReceipts)? = null,
    val usesBrowserDownload: Boolean = false,
    val acknowledgeIdleDownloads: (suspend () -> Unit)? = null,
) {
    companion object { val Unavailable = SchemeBundleRuntime() }
}
