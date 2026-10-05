package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.Serializable

/** Stable asset references only; availability and workspace ACL must be validated separately. */
@Serializable
data class MediaReference(val assetId: String, val mediaKind: MediaKind, val thumbnailAssetId: String? = null) {
    init {
        require(assetId.isNotBlank())
        require(thumbnailAssetId == null || thumbnailAssetId.isNotBlank())
    }
}

fun MediaNode.mediaReference() = MediaReference(assetId, mediaKind, thumbnailAssetId)

@Serializable
data class MediaReferenceChange(val objectId: CanvasObjectId, val expectedVersion: Long,
    val before: MediaReference, val after: MediaReference) {
    fun inverse() = copy(expectedVersion = expectedVersion + 1, before = after, after = before)
}

@Serializable
data class UpdateMediaReferenceOperation(override val operationId: String,
    val changes: List<MediaReferenceChange>) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty())
        require(changes.map { it.objectId }.distinct().size == changes.size)
        require(changes.all { it.expectedVersion in 1 until 9_007_199_254_740_991L })
    }
    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current !is MediaNode) return OperationResult.Rejected(OperationError.UnsupportedObject(change.objectId))
            if (current.version != change.expectedVersion)
                return OperationResult.Rejected(OperationError.VersionConflict(change.objectId, change.expectedVersion, current.version))
            if (current.locked || current.mediaReference() != change.before)
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            replacements[current.id] = current.copy(version = current.version + 1,
                assetId = change.after.assetId, mediaKind = change.after.mediaKind,
                thumbnailAssetId = change.after.thumbnailAssetId)
        }
        return OperationResult.Applied(workspace.copy(version = workspace.version + 1,
            objects = workspace.objects + replacements))
    }
    override fun inverse(): WorkspaceOperation = UpdateMediaReferenceOperation("$operationId:inverse",
        changes.map(MediaReferenceChange::inverse))
}
