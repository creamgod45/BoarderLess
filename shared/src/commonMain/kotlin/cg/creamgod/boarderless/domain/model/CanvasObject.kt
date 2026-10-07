package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CanvasObjectId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Canvas object id must not be blank" }
    }
}

@Serializable
sealed interface CanvasObject {
    val id: CanvasObjectId
    val version: Long
    val parentId: CanvasObjectId?
    val zIndex: Long
    val locked: Boolean
    val transform: CanvasTransform
}

@Serializable
enum class NodeShape(
    val token: String,
) {
    RoundedRectangle("rounded"),
    Rectangle("rectangle"),
    Ellipse("ellipse"),
    Diamond("diamond"),
    Pill("pill"),
    Parallelogram("parallelogram"),
    Hexagon("hexagon"),
    Document("document"),
    Database("database"),
    PlainText("plain-text"),
    Triangle("triangle"),
    Pentagon("pentagon"),
    Octagon("octagon"),
    Trapezoid("trapezoid"),
    Plus("plus"),
    ArrowRight("arrow-right"),
    ArrowLeft("arrow-left"),
    ArrowUp("arrow-up"),
    ArrowDown("arrow-down"),
    TriangleDown("triangle-down"),
    RightTriangle("right-triangle"),
    Chevron("chevron"),
    DoubleArrow("double-arrow"),
    Star("star"),
    ManualInput("manual-input"),
    ;

    companion object {
        fun fromToken(token: String): NodeShape? = entries.firstOrNull { it.token == token }
    }
}

@Serializable
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
    /** Optional structured custom outline; shape is the fallback for ordinary nodes. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val vectorPath: VectorPath? = null,
) : CanvasObject {
    init {
        require(version >= 1) { "text node version must be positive" }
        if (vectorPath != null) require(transform.size.width > 0f && transform.size.height > 0f)
    }
}

@Serializable
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

/** A durable reference to media stored by the Workspace asset service.
 *
 * Download URLs and local file paths deliberately do not belong here: both are ephemeral and
 * device-specific. A MediaNode can therefore survive clipboard, history, sync, and reopening the
 * Workspace without leaking a signed URL into the canvas projection.
 */
@Serializable
enum class MediaKind(
    val token: String,
) {
    Image("image"),
    Gif("gif"),
    Video("video"),
    ;

    companion object {
        fun fromToken(token: String): MediaKind? = entries.firstOrNull { it.token == token }
    }
}

@Serializable
data class MediaNode(
    override val id: CanvasObjectId,
    override val version: Long = 1,
    override val parentId: CanvasObjectId? = null,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val transform: CanvasTransform,
    val assetId: String,
    val mediaKind: MediaKind,
    val altText: String = "",
    val thumbnailAssetId: String? = null,
) : CanvasObject {
    init {
        require(version >= 1) { "media node version must be positive" }
        require(assetId.isNotBlank()) { "media node asset id must not be blank" }
        require(thumbnailAssetId?.isNotBlank() != false) { "thumbnail asset id must not be blank" }
    }
}

fun CanvasObject.withTransform(
    transform: CanvasTransform,
    version: Long = this.version + 1,
): CanvasObject =
    when (this) {
        is TextNode -> copy(transform = transform, version = version)
        is GroupFrame -> copy(transform = transform, version = version)
        is MediaNode -> copy(transform = transform, version = version)
    }

fun CanvasObject.withVersion(version: Long): CanvasObject =
    when (this) {
        is TextNode -> copy(version = version)
        is GroupFrame -> copy(version = version)
        is MediaNode -> copy(version = version)
    }

fun CanvasObject.withParentId(
    parentId: CanvasObjectId?,
    version: Long = this.version + 1,
): CanvasObject =
    when (this) {
        is TextNode -> copy(parentId = parentId, version = version)
        is GroupFrame -> copy(parentId = parentId, version = version)
        is MediaNode -> copy(parentId = parentId, version = version)
    }
