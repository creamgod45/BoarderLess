package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable

/** Absolute local-unit control points; a null handle is a straight/corner endpoint. */
@Serializable
data class PenAnchor(
    val point: Vec2,
    val incoming: Vec2? = null,
    val outgoing: Vec2? = null,
) {
    init {
        listOfNotNull(point, incoming, outgoing).forEach(::requireVectorCoordinate)
    }

    fun translated(delta: Vec2) = copy(point = point + delta, incoming = incoming?.plus(delta), outgoing = outgoing?.plus(delta))
}

/** One editable pen contour. Serialization is local authoring data, not a Workspace operation. */
@Serializable
data class PenPathDraft(
    val viewBox: CanvasSize,
    val anchors: List<PenAnchor> = emptyList(),
    val closed: Boolean = false,
    val style: VectorPathStyle = VectorPathStyle(fillColorToken = null),
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1)
        require(viewBox.width in 0.001f..100_000f && viewBox.height in 0.001f..100_000f)
        require(anchors.size <= 1024)
        require(!closed || anchors.size >= 2) // Two curved anchors can form a valid lens/oval contour.
    }

    fun append(anchor: PenAnchor): PenPathDraft {
        require(!closed)
        return copy(anchors = anchors + anchor)
    }

    fun replace(
        index: Int,
        anchor: PenAnchor,
    ): PenPathDraft {
        require(index in anchors.indices)
        return copy(anchors = anchors.mapIndexed { i, value -> if (i == index) anchor else value })
    }

    fun remove(index: Int): PenPathDraft {
        require(index in anchors.indices)
        val remaining = anchors.filterIndexed { i, _ -> i != index }
        return copy(anchors = remaining, closed = closed && remaining.size >= 2)
    }

    fun toVectorPath(): VectorPath {
        require(anchors.size >= 2)

        fun segment(
            from: PenAnchor,
            to: PenAnchor,
        ): VectorPathCommand =
            when {
                from.outgoing != null && to.incoming != null -> VectorPathCommand.Cubic(from.outgoing, to.incoming, to.point)
                from.outgoing != null -> VectorPathCommand.Quadratic(from.outgoing, to.point)
                to.incoming != null -> VectorPathCommand.Quadratic(to.incoming, to.point)
                else -> VectorPathCommand.Line(to.point)
            }
        val commands =
            buildList {
                add(VectorPathCommand.Move(anchors.first().point))
                anchors.zipWithNext().forEach { (from, to) -> add(segment(from, to)) }
                if (closed) {
                    add(segment(anchors.last(), anchors.first()))
                    add(VectorPathCommand.Close)
                }
            }
        return VectorPath(viewBox, commands, style)
    }
}

/** Local editor undo/redo, bounded independently of Workspace history; no server submission. */
data class PenPathHistory(
    val draft: PenPathDraft,
    val undoStack: List<PenPathDraft> = emptyList(),
    val redoStack: List<PenPathDraft> = emptyList(),
) {
    init {
        require(undoStack.size <= 100 && redoStack.size <= 100)
    }

    fun edit(after: PenPathDraft): PenPathHistory =
        if (after == draft) {
            this
        } else {
            copy(draft = after, undoStack = (undoStack + draft).takeLast(100), redoStack = emptyList())
        }

    fun undo(): PenPathHistory =
        if (undoStack.isEmpty()) {
            this
        } else {
            copy(
                draft = undoStack.last(),
                undoStack = undoStack.dropLast(1),
                redoStack = (redoStack + draft).takeLast(100),
            )
        }

    fun redo(): PenPathHistory =
        if (redoStack.isEmpty()) {
            this
        } else {
            copy(
                draft = redoStack.last(),
                undoStack = (undoStack + draft).takeLast(100),
                redoStack = redoStack.dropLast(1),
            )
        }
}
