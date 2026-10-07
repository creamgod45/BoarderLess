package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode

/** Relations reference real object IDs; media never needs a synthetic text node. */
internal fun Collection<CanvasObject>.connectableNodes(): List<CanvasObject> = filter { it is TextNode || it is MediaNode }

internal val CanvasObject.connectionShape: NodeShape
    get() = (this as? TextNode)?.shape ?: NodeShape.Rectangle

internal val CanvasObject.connectionTitle: String
    get() =
        when (this) {
            is TextNode -> text
            is MediaNode -> altText.ifBlank { mediaKind.name }
            else -> ""
        }

/** Custom paths use their contour, rather than the ordinary node shape fallback. */
internal fun CanvasObject.connectionBoundary(
    transform: cg.creamgod.boarderless.domain.model.CanvasTransform,
    toward: cg.creamgod.boarderless.domain.model.Vec2,
): cg.creamgod.boarderless.domain.model.Vec2 =
    (this as? TextNode)?.vectorPath?.let { path ->
        requireNotNull(vectorBoundaryWorldPoint(transform, path, toward)) { "Vector path has no connectable contour" }
    } ?: shapeBoundaryWorldPoint(transform, connectionShape, toward)
