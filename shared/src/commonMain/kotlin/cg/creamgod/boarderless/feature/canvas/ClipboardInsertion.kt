package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*

internal data class ClipboardInsertion(
    val operation: WorkspaceOperation,
    val selectedIds: Set<CanvasObjectId>,
)

/** One frozen operation remaps the whole graph and publishes bindings atomically. */
internal fun prepareClipboardInsertion(
    payload: ClipboardPayload,
    workspace: Workspace,
    targetWorldPosition: Vec2? = null,
    defaultOffset: Vec2 = Vec2(32f, 32f),
    newId: () -> String = ::randomUuid,
): ClipboardInsertion {
    require(validateClipboardPayload(payload) == null)
    val originals = payload.groups.map { it.originalId } + payload.nodes.map { it.originalId } + payload.media.map { it.originalId }
    val idMap = originals.associateWith { CanvasObjectId(newId()) }
    require(idMap.values.distinct().size == idMap.size && idMap.values.none { it in workspace.objects })
    val relationIds = payload.relations.map { RelationId(newId()) }
    require(relationIds.distinct().size == relationIds.size && relationIds.none { it in workspace.relations })
    val positions =
        payload.groups.map { Vec2(it.x, it.y) } + payload.nodes.map { Vec2(it.x, it.y) } + payload.media.map { Vec2(it.x, it.y) }
    val origin = Vec2(positions.minOf(Vec2::x), positions.minOf(Vec2::y))
    val offset = targetWorldPosition?.let { it - origin } ?: defaultOffset
    val materialized =
        clipboardMaterialization(
            payload,
            offset,
            idMap,
            relationIds,
            workspace.objects.values.maxOfOrNull { it.zIndex } ?: 0L,
        )
    require(materialized.hasValidSequenceBindings())
    val operations = mutableListOf<WorkspaceOperation>(CreateObjectsOperation(newId(), materialized.objects.values.toList()))
    if (materialized.relations.isNotEmpty()) operations += CreateRelationsOperation(newId(), materialized.relations.values.toList())
    if (materialized.sequenceDiagrams.isNotEmpty()) {
        operations +=
            UpdateSequenceDiagramsOperation(
                newId(),
                workspace.sequenceDiagramsVersion,
                workspace.sequenceDiagrams,
                workspace.sequenceDiagrams + materialized.sequenceDiagrams,
            )
    }
    val op = if (operations.size == 1) operations.single() else TransactionOperation(newId(), operations)
    require(op.applyTo(workspace) is OperationResult.Applied)
    // Select sequence roots rather than their protected participant/block placeholders.
    val managedLeaves =
        materialized.sequenceDiagrams.values
            .flatMap { it.allObjectIds() - it.containerId }
            .toSet()
    return ClipboardInsertion(op, materialized.objects.keys - managedLeaves)
}

internal fun clipboardMaterialization(
    payload: ClipboardPayload,
    offset: Vec2,
    idMap: Map<String, CanvasObjectId>,
    relationIds: List<RelationId>,
    top: Long,
): Workspace {
    val nodeShapes =
        payload.nodes.associate { copied ->
            copied.originalId to checkNotNull(NodeShape.fromToken(copied.shapeToken))
        }
    val zOrder =
        (
            payload.groups.map { it.originalId to it.zOffset } +
                payload.nodes.map { it.originalId to it.zOffset } +
                payload.media.map { it.originalId to it.zOffset }
        ).sortedBy { (_, zOffset) -> zOffset }
            .mapIndexed { index, (id, _) -> id to (top + index + 1L) }
            .toMap()
    val groups =
        orderClipboardGroups(payload.groups).map { copied ->
            GroupFrame(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform =
                    CanvasTransform(
                        position = Vec2(copied.x + offset.x, copied.y + offset.y),
                        size = CanvasSize(copied.width, copied.height),
                        rotationDegrees = copied.rotationDegrees,
                    ),
                title = copied.title,
                colorToken = copied.colorToken,
            )
        }
    val nodes =
        payload.nodes.map { copied ->
            TextNode(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform =
                    CanvasTransform(
                        position = Vec2(copied.x + offset.x, copied.y + offset.y),
                        size = CanvasSize(copied.width, copied.height),
                        rotationDegrees = copied.rotationDegrees,
                    ),
                text = copied.text,
                colorToken = copied.colorToken,
                shape = nodeShapes.getValue(copied.originalId),
                vectorPath = copied.vectorPath,
            )
        }
    val media =
        payload.media.map { copied ->
            MediaNode(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform =
                    CanvasTransform(
                        position = Vec2(copied.x + offset.x, copied.y + offset.y),
                        size = CanvasSize(copied.width, copied.height),
                        rotationDegrees = copied.rotationDegrees,
                    ),
                assetId = copied.assetId,
                mediaKind = checkNotNull(MediaKind.fromToken(copied.mediaKind)),
                altText = copied.altText,
                thumbnailAssetId = copied.thumbnailAssetId,
            )
        }
    val relations =
        payload.relations.mapIndexed { index, copied ->
            val sourceId = idMap.getValue(copied.sourceId)
            val targetId = idMap.getValue(copied.targetId)
            Relation(
                id = relationIds[index],
                sourceObjectId = sourceId,
                targetObjectId = targetId,
                direction = RelationDirection.valueOf(copied.direction),
                intent = copied.intent,
                label = copied.label,
                geometry = copied.geometry?.translated(offset),
            )
        }
    val edgeMap =
        payload.relations
            .mapIndexedNotNull {
                index,
                edge,
                ->
                edge.originalId?.let { RelationId(it) to relationIds[index] }
            }.toMap()
    val diagrams =
        payload.sequenceDiagrams.map { diagram ->
            diagram.copy(
                containerId = idMap.getValue(diagram.containerId.value),
                participants = diagram.participants.map { it.copy(objectId = idMap.getValue(it.objectId.value)) },
                blocks = diagram.blocks.map { it.copy(objectId = idMap.getValue(it.objectId.value)) },
                messages = diagram.messages.map { it.copy(relationId = edgeMap.getValue(it.relationId)) },
            )
        }
    return Workspace(
        WorkspaceId("clipboard_materialization"),
        "Clipboard",
        objects = (groups + nodes + media).associateBy { it.id },
        relations = relations.associateBy { it.id },
        sequenceDiagrams = diagrams.associateBy { it.containerId },
    )
}

internal fun orderClipboardGroups(groups: List<ClipboardGroup>): List<ClipboardGroup> {
    val byId = groups.associateBy(ClipboardGroup::originalId)

    fun depth(
        group: ClipboardGroup,
        visited: Set<String> = emptySet(),
    ): Int {
        if (group.originalId in visited) return 0
        val parent = group.parentOriginalId?.let(byId::get) ?: return 0
        return 1 + depth(parent, visited + group.originalId)
    }
    return groups.sortedWith(compareBy<ClipboardGroup>({ depth(it) }, ClipboardGroup::zOffset))
}
