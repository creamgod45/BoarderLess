package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.MediaNode

internal enum class LayerObjectKind { Group, Thought, Media }

internal data class LayerTreeEntry(
    val objectId: CanvasObjectId,
    val depth: Int,
    val title: String,
    val kind: LayerObjectKind,
    val locked: Boolean,
    val zIndex: Long,
)

internal fun buildLayerTree(objects: Collection<CanvasObject>): List<LayerTreeEntry> {
    val objectsById = objects.associateBy(CanvasObject::id)
    val childrenByParent = objects
        .filter { it.parentId?.let(objectsById::containsKey) == true }
        .groupBy(CanvasObject::parentId)
    val roots = objects
        .filter { it.parentId?.let(objectsById::containsKey) != true }
        .sortedWith(layerObjectComparator())
    val visited = mutableSetOf<CanvasObjectId>()
    val entries = mutableListOf<LayerTreeEntry>()

    fun append(canvasObject: CanvasObject, depth: Int) {
        if (!visited.add(canvasObject.id)) return
        entries += canvasObject.toLayerTreeEntry(depth)
        childrenByParent[canvasObject.id]
            .orEmpty()
            .sortedWith(layerObjectComparator())
            .forEach { append(it, depth + 1) }
    }

    roots.forEach { append(it, 0) }
    objects.sortedWith(layerObjectComparator()).forEach { append(it, 0) }
    return entries
}

private fun layerObjectComparator(): Comparator<CanvasObject> =
    compareByDescending<CanvasObject> { it.zIndex }.thenBy { it.id.value }

private fun CanvasObject.toLayerTreeEntry(depth: Int): LayerTreeEntry = when (this) {
    is GroupFrame -> LayerTreeEntry(
        objectId = id,
        depth = depth,
        title = title.ifBlank { Strings.content.untitledGroup() }.take(48),
        kind = LayerObjectKind.Group,
        locked = locked,
        zIndex = zIndex,
    )

    is TextNode -> LayerTreeEntry(
        objectId = id,
        depth = depth,
        title = text.lineSequence().firstOrNull().orEmpty().ifBlank { Strings.content.untitledThought() }.take(48),
        kind = LayerObjectKind.Thought,
        locked = locked,
        zIndex = zIndex,
    )

    is MediaNode -> LayerTreeEntry(
        objectId = id,
        depth = depth,
        title = altText.ifBlank { mediaKind.name }.take(48),
        kind = LayerObjectKind.Media,
        locked = locked,
        zIndex = zIndex,
    )
}
