package cg.creamgod.boarderless.data

/** Local-only preparation with a durable caller-owned identity. Caller must save the source
 * mapping/ID before invoking this; preparation alone never proves original bytes are Ready. */
interface PinnedLocalAssetGateway : AssetTransferGateway {
    suspend fun preparePinned(session: WorkspaceSession, pending: WorkspaceAsset): AssetUploadTicket
}

internal fun validatePinnedLocalAsset(session: WorkspaceSession, pending: WorkspaceAsset) {
    require(session.canEditContent && pending.ownerId == session.userId && pending.workspaceId == session.workspace.id)
    require(pending.status == AssetStatus.Pending && pending.thumbnailAssetId == null && pending.rejectionReason == null)
    require(pending.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    require(pending.mediaType == pending.mediaType.trim().lowercase())
    require(pending.checksum.matches(Regex("sha256:[0-9a-fA-F]{64}")))
    validateAssetTransferSource(object : AssetTransferSource {
        override val displayName = "local-materialization"
        override val mediaType = pending.mediaType
        override val byteSize = pending.byteSize
        override val checksum = pending.checksum
        override val width = pending.width
        override val height = pending.height
        override val durationMs = pending.durationMs
        override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = error("Metadata validation must not read bytes")
    })
}
