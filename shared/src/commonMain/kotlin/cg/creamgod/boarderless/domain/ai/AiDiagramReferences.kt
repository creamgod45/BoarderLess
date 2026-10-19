package cg.creamgod.boarderless.domain.ai

import cg.creamgod.boarderless.domain.model.*

/** Packet-local aliases shared by the scope review and the isolated tool draft. */
internal fun aiDiagramObjectReferences(approved: Set<CanvasObjectId>): Map<String, CanvasObjectId> =
    approved.sortedBy { it.value }.mapIndexed { index, id -> "N${index + 1}" to id }.toMap()

internal fun aiDiagramRelationReferences(workspace: Workspace, approved: Set<CanvasObjectId>): Map<String, RelationId> =
    workspace.relations.values
        .filter { it.sourceObjectId in approved && it.targetObjectId in approved }
        .sortedBy { it.id.value }
        .mapIndexed { index, relation -> "E${index + 1}" to relation.id }.toMap()
