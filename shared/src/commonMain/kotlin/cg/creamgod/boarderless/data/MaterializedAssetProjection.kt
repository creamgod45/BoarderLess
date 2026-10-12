package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*

/** Identity-only projection. It proves neither bytes, authorization nor publication.
 * Keep canonical reviews/receipts unchanged; project only at an explicit local/wire boundary. */
internal class MaterializedAssetProjection(
    session: WorkspaceSession,
    binding: LocalWorkspaceSyncBinding,
    mappings: List<LocalAssetMaterialization>,
) {
    private val bySource: Map<String, LocalAssetMaterialization>
    private val byLocal: Map<String, LocalAssetMaterialization>
    private val workspaceId = session.workspace.id

    init {
        require(mappings.size <= 4096)
        mappings.forEach {
            require(it.binding == binding && it.userId == session.userId && it.clientId == session.clientId && it.workspaceId == workspaceId)
        }
        bySource = mappings.associateBy { it.source.id }
        byLocal = mappings.associateBy { it.localAssetId }
        require(bySource.size == mappings.size && byLocal.size == mappings.size)
        // A source ID cannot simultaneously name a destination of another mapping.
        require(bySource.keys.intersect(byLocal.keys).isEmpty())
    }

    fun toLocal(workspace: Workspace): Workspace = project(workspace, true)
    fun toCanonical(workspace: Workspace): Workspace = project(workspace, false)

    private fun project(workspace: Workspace, toLocal: Boolean): Workspace {
        require(workspace.id == workspaceId)
        val lookup = if (toLocal) bySource else byLocal
        val wrongNamespace = if (toLocal) byLocal else bySource
        fun reference(id: String, kind: MediaKind?): String {
            require(id !in wrongNamespace) { "Mixed asset identity namespace" }
            val mapping = lookup[id] ?: return id // Existing unmapped local/server references stay exact.
            require(kind == null || mediaKindForAssetMediaType(mapping.source.mediaType) == kind)
            return if (toLocal) mapping.localAssetId else mapping.source.id
        }
        return workspace.copy(objects = workspace.objects.mapValues { (_, item) ->
            if (item !is MediaNode) item else item.copy(
                assetId = reference(item.assetId, item.mediaKind),
                thumbnailAssetId = item.thumbnailAssetId?.let { reference(it, MediaKind.Image) },
            )
        })
    }
}
