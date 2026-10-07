package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.abs

/** Converts one complete contour, never dropping later subpaths or excess anchors. Quadratic
 * commands attach their shared control to the preceding anchor; geometry is unchanged.
 */
internal fun VectorPath.toPenEditorDraft(): PenPathDraft? =
    runCatching {
        val anchors = mutableListOf<PenAnchor>()
        var closed = false
        commands.forEachIndexed { index, command ->
            require(!closed)
            when (command) {
                is VectorPathCommand.Move -> {
                    require(index == 0)
                    anchors.add(PenAnchor(command.point))
                }

                is VectorPathCommand.Line -> {
                    anchors.add(PenAnchor(command.point))
                }

                is VectorPathCommand.Quadratic -> {
                    anchors[anchors.lastIndex] = anchors.last().copy(outgoing = command.control)
                    anchors.add(PenAnchor(command.point))
                }

                is VectorPathCommand.Cubic -> {
                    anchors[anchors.lastIndex] = anchors.last().copy(outgoing = command.control1)
                    anchors.add(PenAnchor(command.point, incoming = command.control2))
                }

                VectorPathCommand.Close -> {
                    require(index == commands.lastIndex)
                    closed = true
                    if (anchors.last().point == anchors.first().point) {
                        val closing = anchors.removeAt(anchors.lastIndex)
                        anchors[0] = anchors[0].copy(incoming = closing.incoming)
                    }
                }
            }
            require(anchors.size <= 1025) // Closing duplicate is removed before final limit validation.
        }
        require(anchors.size >= 2)
        PenPathDraft(viewBox, anchors.toList(), closed, style).also { it.toVectorPath() }
    }.getOrNull()

internal fun editablePenNode(
    workspace: Workspace,
    node: TextNode,
): Boolean {
    if (workspace.objects[node.id] != node || node.vectorPath == null) return false
    var current: CanvasObject? = node
    val seen = mutableSetOf<CanvasObjectId>()
    while (current != null) {
        if (current.locked || !seen.add(current.id)) return false
        val parent = current.parentId ?: return true
        current = workspace.objects[parent] as? GroupFrame ?: return false
    }
    return false
}

/** Full canvas CAS plus versioned attributes/transform transaction. Original IDs/edges stay intact. */
internal data class PenCanvasEditing(
    val owner: WorkspaceSession,
    val baseline: Workspace,
    val original: TextNode,
) {
    val draft = requireNotNull(original.vectorPath?.toPenEditorDraft()) { "Path cannot be represented by this editor" }

    init {
        require(baseline.id == owner.workspace.id && editablePenNode(baseline, original))
    }

    fun isCurrent(
        session: WorkspaceSession?,
        workspace: Workspace,
    ): Boolean =
        PenCanvasCreation(
            owner,
            baseline,
            original.transform.position,
        ).isCurrent(session, workspace) && editablePenNode(workspace, original)

    fun operation(
        path: VectorPath,
        operationId: String,
    ): TransactionOperation? {
        if (path == draft.toVectorPath()) return null // Imported implicit closure does not become a fake edit.
        require(path.viewBox == draft.viewBox)
        val normalized = normalizePenPath(path)
        val oldPath = requireNotNull(original.vectorPath)
        val xScale = original.transform.size.width / oldPath.viewBox.width
        val yScale = original.transform.size.height / oldPath.viewBox.height
        val size = CanvasSize(normalized.path.viewBox.width * xScale, normalized.path.viewBox.height * yScale)
        require(size.width in 0.001f..100_000f && size.height in 0.001f..100_000f)
        val oldCenter = original.transform.position + Vec2(original.transform.size.width / 2f, original.transform.size.height / 2f)
        val localDelta =
            normalized.origin + Vec2(normalized.path.viewBox.width / 2f, normalized.path.viewBox.height / 2f) -
                Vec2(oldPath.viewBox.width / 2f, oldPath.viewBox.height / 2f)
        val center = oldCenter + rotateVector(Vec2(localDelta.x * xScale, localDelta.y * yScale), original.transform.rotationDegrees)
        val transform = original.transform.copy(position = center - Vec2(size.width / 2f, size.height / 2f), size = size)
        require(abs(transform.position.x) <= 1_000_000f && abs(transform.position.y) <= 1_000_000f)
        require(original.version in 1..9_007_199_254_740_989L && baseline.version in 0..9_007_199_254_740_990L)
        val before = TextNodeAttributes(original.zIndex, original.locked, original.colorToken, original.shape, original.vectorPath)
        val after = before.copy(vectorPath = normalized.path)
        val operations = mutableListOf<WorkspaceOperation>()
        var version = original.version
        if (transform != original.transform) {
            operations.add(
                TransformObjectsOperation(
                    "$operationId:transform",
                    listOf(TransformChange(original.id, version, original.transform, transform)),
                ),
            )
            version++
        }
        if (before !=
            after
        ) {
            operations.add(
                UpdateTextNodeAttributesOperation(
                    "$operationId:path",
                    listOf(TextNodeAttributesChange(original.id, version, before, after)),
                ),
            )
        }
        if (operations.isEmpty()) return null
        return TransactionOperation(operationId, operations)
    }
}
