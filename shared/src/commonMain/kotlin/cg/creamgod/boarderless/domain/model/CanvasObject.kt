package cg.creamgod.boarderless.domain.model

data class CanvasObjectId(val value: String) {
    init {
        require(value.isNotBlank()) { "Canvas object id must not be blank" }
    }
}

sealed interface CanvasObject {
    val id: CanvasObjectId
    val version: Long
    val parentId: CanvasObjectId?
    val zIndex: Long
    val locked: Boolean
    val transform: CanvasTransform
}

enum class NodeShape(val token: String) {
    RoundedRectangle("rounded"),
    Rectangle("rectangle"),
    Ellipse("ellipse"),
    Diamond("diamond"),
    Pill("pill"),
    Parallelogram("parallelogram"),
    Hexagon("hexagon"),
    Document("document"),
    Database("database"),
    ;

    companion object {
        fun fromToken(token: String): NodeShape? = entries.firstOrNull { it.token == token }
    }
}

data class TextNode(
    override val id: CanvasObjectId,
    override val version: Long = 1,
    override val parentId: CanvasObjectId? = null,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val transform: CanvasTransform,
    val text: String,
    val colorToken: String = "paper",
    val shape: NodeShape = NodeShape.RoundedRectangle,
) : CanvasObject {
    init {
        require(version >= 1) { "text node version must be positive" }
    }
}

data class GroupFrame(
    override val id: CanvasObjectId,
    override val version: Long = 1,
    override val parentId: CanvasObjectId? = null,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val transform: CanvasTransform,
    val title: String = "Group",
    val colorToken: String = "group",
) : CanvasObject {
    init {
        require(version >= 1) { "group version must be positive" }
    }
}

fun CanvasObject.withTransform(
    transform: CanvasTransform,
    version: Long = this.version + 1,
): CanvasObject = when (this) {
    is TextNode -> copy(transform = transform, version = version)
    is GroupFrame -> copy(transform = transform, version = version)
}

fun CanvasObject.withVersion(version: Long): CanvasObject = when (this) {
    is TextNode -> copy(version = version)
    is GroupFrame -> copy(version = version)
}

fun CanvasObject.withParentId(
    parentId: CanvasObjectId?,
    version: Long = this.version + 1,
): CanvasObject = when (this) {
    is TextNode -> copy(parentId = parentId, version = version)
    is GroupFrame -> copy(parentId = parentId, version = version)
}
