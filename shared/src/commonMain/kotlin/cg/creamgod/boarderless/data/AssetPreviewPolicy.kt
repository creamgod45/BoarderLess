package cg.creamgod.boarderless.data

/** Shared safety rules for static images and GIF/video image thumbnails. */
object AssetPreviewPolicy {
    const val MaxEncodedBytes = 32L * 1024 * 1024
    const val MaxPixels = 12_000_000L

    fun validate(ticket: AssetDownloadTicket, session: WorkspaceSession, assetId: String) {
        require(assetId.isNotBlank() && ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id) {
            "Preview authorization belongs to another asset or workspace"
        }
        require(ticket.asset.status == AssetStatus.Ready) { "Preview asset is not ready" }
        require(ticket.asset.mediaType in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) {
            "Preview must be an image, not a video or executable document"
        }
        require(ticket.asset.byteSize <= MaxEncodedBytes) { "Preview requires a smaller thumbnail" }
    }

    fun validateDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0 && width.toLong() * height <= MaxPixels) {
            "Preview dimensions exceed the safe decode limit"
        }
    }

    /** Power-of-two native sampling keeps a mobile preview from allocating full-size pixels. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        validateDimensions(width, height)
        require(maxEdge > 0)
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > maxEdge ||
            (height.toLong() + sample - 1) / sample > maxEdge) {
            sample *= 2
        }
        return sample
    }
}
