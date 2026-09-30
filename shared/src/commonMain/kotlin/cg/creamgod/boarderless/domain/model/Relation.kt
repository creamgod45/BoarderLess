package cg.creamgod.boarderless.domain.model

data class RelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Relation id must not be blank" }
    }
}

enum class RelationDirection {
    None,
    Forward,
    Backward,
    Both,
}

data class Relation(
    val id: RelationId,
    val version: Long = 1,
    val sourceObjectId: CanvasObjectId,
    val targetObjectId: CanvasObjectId,
    val direction: RelationDirection = RelationDirection.Forward,
    val intent: String? = null,
    val label: String? = null,
    val colorToken: String = "relation",
) {
    init {
        require(version >= 1) { "relation version must be positive" }
    }
}

fun Relation.withVersion(version: Long): Relation = copy(version = version)
