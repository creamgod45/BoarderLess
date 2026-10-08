package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.model.*

internal data class RelationNodeInsertion(
    val operation: TransactionOperation,
    val first: Relation,
    val second: Relation,
)

/** Shared manual/AI transaction. Original relation is kept whole for exact Undo; new edges
 * reroute through the explicitly placed node. Backward edges follow their actual flow. */
internal fun relationNodeInsertion(
    original: Relation,
    node: TextNode,
    firstId: RelationId = RelationId(randomUuid()),
    secondId: RelationId = RelationId(randomUuid()),
    operationId: String = randomUuid(),
    createId: String = randomUuid(),
    deleteId: String = randomUuid(),
    relationsId: String = randomUuid(),
    firstLabel: String? = original.label,
    firstIntent: String? = original.intent,
): RelationNodeInsertion {
    require(node.id != original.sourceObjectId && node.id != original.targetObjectId)
    require(firstId != secondId && firstId != original.id && secondId != original.id)
    val backward = original.direction == RelationDirection.Backward
    val first = Relation(firstId,
        sourceObjectId = if (backward) original.targetObjectId else original.sourceObjectId,
        targetObjectId = node.id,
        direction = if (backward) RelationDirection.Forward else original.direction,
        label = firstLabel, intent = firstIntent, colorToken = original.colorToken)
    val second = Relation(secondId,
        sourceObjectId = node.id,
        targetObjectId = if (backward) original.sourceObjectId else original.targetObjectId,
        direction = first.direction, colorToken = original.colorToken)
    return RelationNodeInsertion(TransactionOperation(operationId, listOf(
        CreateObjectsOperation(createId, listOf(node)),
        DeleteRelationsOperation(deleteId, listOf(original)),
        CreateRelationsOperation(relationsId, listOf(first, second)),
    )), first, second)
}
