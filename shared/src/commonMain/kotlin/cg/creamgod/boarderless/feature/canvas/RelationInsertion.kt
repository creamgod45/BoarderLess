package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.abs
import kotlin.math.min

internal data class RelationInsertionPlan(
    val operation: TransactionOperation,
    val node: TextNode,
    val first: Relation,
    val second: Relation,
    val preview: Workspace,
)

/** Identity is allocated once for this dialog. Editing text does not mint new transaction IDs.
 * Uses the existing create/delete/create wire contract; no endpoint update, route metadata,
 * self-loop or guessed sequence semantics. Whole original relation is retained for exact Undo.
 */
internal class RelationInsertionDraft(
    workspace: Workspace,
    relationId: RelationId,
) {
    val baseline = workspace.copy(objects = workspace.objects.toMap(), relations = workspace.relations.toMap())
    val original = baseline.relations.getValue(relationId)
    private val source = baseline.objects.getValue(original.sourceObjectId)
    private val target = baseline.objects.getValue(original.targetObjectId)
    private val nodeId = CanvasObjectId(randomUuid())
    private val firstId = RelationId(randomUuid())
    private val secondId = RelationId(randomUuid())
    private val transactionId = randomUuid()
    private val createId = randomUuid()
    private val deleteId = randomUuid()
    private val relationsId = randomUuid()
    val center: Vec2

    init {
        require(original.sourceObjectId != original.targetObjectId || original.geometry != null)
        require(baseline.version in 0..9_007_199_254_738_990L && original.version in 1..9_007_199_254_738_990L)
        for (endpoint in listOf(source, target)) {
            var current: CanvasObject? = endpoint
            val seen = mutableSetOf<CanvasObjectId>()
            while (current != null) {
                require(seen.add(current.id) && !current.locked)
                current = current.parentId?.let { baseline.objects[it].also { parent -> require(parent is GroupFrame) } }
            }
            val t = endpoint.transform
            require(
                abs(t.position.x) <= 1_000_000 && abs(t.position.y) <= 1_000_000 && t.size.width in 1f..100_000f &&
                    t.size.height in 1f..100_000f,
            )
        }
        require((baseline.objects.values.maxOfOrNull { it.zIndex } ?: 0) < 9_007_199_254_738_990L)
        center = polylineMidpoint(checkNotNull(relationWorldRoute(original, baseline.objects)))
        require(abs(center.x) <= 1_000_000 && abs(center.y) <= 1_000_000)
    }

    fun plan(
        text: String,
        firstLabel: String? = original.label,
        firstIntent: String? = original.intent,
        position: Vec2 =
            center -
                Vec2(130f, 66f),
        size: CanvasSize = CanvasSize(260f, 132f),
    ): RelationInsertionPlan {
        require(text.isNotBlank() && text.encodeToByteArray().size <= 64 * 1024)
        require(firstLabel == null || firstLabel.encodeToByteArray().size <= 64 * 1024)
        require(firstIntent == null || firstIntent.encodeToByteArray().size <= 4096)
        require(abs(position.x) <= 1_000_000 && abs(position.y) <= 1_000_000 && size.width in 1f..100_000f && size.height in 1f..100_000f)
        val backward = original.direction == RelationDirection.Backward
        val start = if (backward) original.targetObjectId else original.sourceObjectId
        val end = if (backward) original.sourceObjectId else original.targetObjectId
        val direction = if (backward) RelationDirection.Forward else original.direction
        val node =
            TextNode(
                nodeId,
                transform = CanvasTransform(position, size),
                text = text,
                parentId = source.parentId.takeIf { it == target.parentId },
                zIndex = (baseline.objects.values.maxOfOrNull { it.zIndex } ?: 0) + 1,
            )
        val first =
            Relation(
                firstId,
                sourceObjectId = start,
                targetObjectId = node.id,
                direction = direction,
                intent = firstIntent,
                label = firstLabel,
                colorToken = original.colorToken,
            )
        val second =
            Relation(
                secondId,
                sourceObjectId = node.id,
                targetObjectId = end,
                direction = direction,
                intent = null,
                label = null,
                colorToken = original.colorToken,
            )
        val operation =
            TransactionOperation(
                transactionId,
                listOf(
                    CreateObjectsOperation(createId, listOf(node)),
                    DeleteRelationsOperation(deleteId, listOf(original)),
                    CreateRelationsOperation(relationsId, listOf(first, second)),
                ),
            )
        val result = operation.applyTo(baseline)
        require(result is OperationResult.Applied)
        return RelationInsertionPlan(operation, node, first, second, result.workspace)
    }

    fun isCurrent(workspace: Workspace) = workspace == baseline
}
