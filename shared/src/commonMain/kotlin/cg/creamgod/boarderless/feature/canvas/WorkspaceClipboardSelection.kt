package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*

internal fun workspaceClipboardPayload(
    workspace: Workspace,
    selectedIds: Set<CanvasObjectId>,
): ClipboardPayload? {
    val selectedGroupIds =
        selectedIds.filterTo(mutableSetOf()) {
            workspace.objectById(it) is GroupFrame
        }
    val includedIds = selectedIds + descendantObjectIds(workspace, selectedGroupIds)
    if (!workspace.hasValidSequenceBindings()) return null
    val diagrams = workspace.sequenceDiagrams.values.filter { diagram -> diagram.allObjectIds().any(includedIds::contains) }
    if (diagrams.any { !includedIds.containsAll(it.allObjectIds()) }) return null
    val objects = includedIds.mapNotNull(workspace::objectById)
    if (objects.isEmpty()) return null
    val nodes = objects.filterIsInstance<TextNode>()
    val groups = objects.filterIsInstance<GroupFrame>()
    val media = objects.filterIsInstance<MediaNode>()
    val minimumZ = objects.minOf(CanvasObject::zIndex)
    val includedIdSet = objects.mapTo(mutableSetOf()) { it.id }
    val nodeIds = objects.connectableNodes().mapTo(mutableSetOf()) { it.id }
    val relations =
        workspace.relations.values.filter {
            it.sourceObjectId in nodeIds && it.targetObjectId in nodeIds
        }
    return ClipboardPayload(
        version = if (diagrams.isEmpty()) 6 else 7,
        sequenceDiagrams = diagrams.map { it.validatedSnapshot() },
        nodes =
            nodes.map { node ->
                ClipboardNode(
                    originalId = node.id.value,
                    x = node.transform.position.x,
                    y = node.transform.position.y,
                    width = node.transform.size.width,
                    height = node.transform.size.height,
                    rotationDegrees = node.transform.rotationDegrees,
                    text = node.text,
                    colorToken = node.colorToken,
                    shapeToken = node.shape.token,
                    vectorPath = node.vectorPath,
                    parentOriginalId = node.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = node.locked,
                    zOffset = node.zIndex - minimumZ,
                )
            },
        groups =
            groups.map { group ->
                ClipboardGroup(
                    originalId = group.id.value,
                    x = group.transform.position.x,
                    y = group.transform.position.y,
                    width = group.transform.size.width,
                    height = group.transform.size.height,
                    rotationDegrees = group.transform.rotationDegrees,
                    title = group.title,
                    colorToken = group.colorToken,
                    parentOriginalId = group.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = group.locked,
                    zOffset = group.zIndex - minimumZ,
                )
            },
        media =
            media.map { node ->
                ClipboardMedia(
                    originalId = node.id.value,
                    x = node.transform.position.x,
                    y = node.transform.position.y,
                    width = node.transform.size.width,
                    height = node.transform.size.height,
                    rotationDegrees = node.transform.rotationDegrees,
                    assetId = node.assetId,
                    mediaKind = node.mediaKind.token,
                    altText = node.altText,
                    thumbnailAssetId = node.thumbnailAssetId,
                    parentOriginalId = node.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = node.locked,
                    zOffset = node.zIndex - minimumZ,
                )
            },
        relations =
            relations.map { relation ->
                ClipboardRelation(
                    originalId = if (diagrams.isEmpty()) null else relation.id.value,
                    sourceId = relation.sourceObjectId.value,
                    targetId = relation.targetObjectId.value,
                    direction = relation.direction.name,
                    intent = relation.intent,
                    label = relation.label,
                    geometry = relation.geometry,
                )
            },
    )
}
