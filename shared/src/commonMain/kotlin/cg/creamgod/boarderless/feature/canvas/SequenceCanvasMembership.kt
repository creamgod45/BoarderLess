package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

/** Managed participants/blocks/messages have dedicated scene geometry and editing semantics. */
internal class SequenceCanvasMembership(
    workspace: Workspace,
) {
    val internalIds =
        workspace.sequenceDiagrams.values
            .flatMap { (it.allObjectIds() - it.containerId).toList() }
            .toSet()
    val relationIds =
        workspace.sequenceDiagrams.values
            .flatMap { it.allRelationIds().toList() }
            .toSet()

    fun blocksMutation(
        selection: Set<CanvasObjectId>,
        relation: RelationId?,
    ) = selection.any(internalIds::contains) || relation in relationIds

    fun visibleGroups(workspace: Workspace) =
        workspace.objects.values
            .filterIsInstance<GroupFrame>()
            .filterNot { it.id in internalIds }

    fun visibleNodes(workspace: Workspace) =
        workspace.objects.values
            .filterIsInstance<TextNode>()
            .filterNot { it.id in internalIds }

    fun visibleRelations(workspace: Workspace) = workspace.relations.values.filterNot { it.id in relationIds }
}
