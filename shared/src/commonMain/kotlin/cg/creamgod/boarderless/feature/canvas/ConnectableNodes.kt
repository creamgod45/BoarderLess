package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode

/** Relations reference real object IDs; media never needs a synthetic text node. */
internal fun Collection<CanvasObject>.connectableNodes(): List<CanvasObject> =
    filter { it is TextNode || it is MediaNode }

internal val CanvasObject.connectionShape: NodeShape
    get() = (this as? TextNode)?.shape ?: NodeShape.Rectangle

internal val CanvasObject.connectionTitle: String
    get() = when (this) {
        is TextNode -> text
        is MediaNode -> altText.ifBlank { mediaKind.name }
        else -> ""
    }
