package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ClipboardPayload(
    val format: String = "boarderless/selection",
    val version: Int = 6,
    val nodes: List<ClipboardNode>,
    val groups: List<ClipboardGroup> = emptyList(),
    val media: List<ClipboardMedia> = emptyList(),
    val relations: List<ClipboardRelation> = emptyList(),
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val sequenceDiagrams: List<SequenceCanvasDiagram> = emptyList(),
)

@Serializable
internal data class ClipboardNode(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val text: String,
    val colorToken: String,
    val shapeToken: String = NodeShape.RoundedRectangle.token,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
    val vectorPath: VectorPath? = null,
)

@Serializable
internal data class ClipboardGroup(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val title: String,
    val colorToken: String,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
)

@Serializable
internal data class ClipboardMedia(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val assetId: String,
    val mediaKind: String,
    val altText: String = "",
    val thumbnailAssetId: String? = null,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
)

@Serializable
internal data class ClipboardRelation(
    val sourceId: String,
    val targetId: String,
    val direction: String,
    val intent: String? = null,
    val label: String? = null,
    val geometry: cg.creamgod.boarderless.domain.model.RelationGeometry? = null,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val originalId: String? = null,
)

internal val ClipboardJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

internal enum class ClipboardPayloadIssue {
    UnsupportedFormat,
    UnsupportedVersion,
    Empty,
    InvalidObjectId,
    DuplicateObjectId,
    InvalidTransform,
    UnsupportedShape,
    InvalidMedia,
    InvalidParent,
    ParentCycle,
    InvalidRelation,
    InvalidSequence,
    Limit,
}

internal fun validateClipboardPayload(payload: ClipboardPayload): ClipboardPayloadIssue? {
    if (payload.format != "boarderless/selection") return ClipboardPayloadIssue.UnsupportedFormat
    if (payload.version !in 1..7) return ClipboardPayloadIssue.UnsupportedVersion
    if (payload.nodes.isEmpty() && payload.groups.isEmpty() && payload.media.isEmpty()) {
        return ClipboardPayloadIssue.Empty
    }

    if (payload.nodes.size + payload.groups.size + payload.media.size > 1000 || payload.relations.size > 2000 ||
        payload.sequenceDiagrams.size > 64
    ) {
        return ClipboardPayloadIssue.Limit
    }
    if (payload.version < 7 &&
        (payload.sequenceDiagrams.isNotEmpty() || payload.relations.any { it.originalId != null })
    ) {
        return ClipboardPayloadIssue.UnsupportedVersion
    }
    val objectIds =
        payload.groups.map(ClipboardGroup::originalId) +
            payload.nodes.map(ClipboardNode::originalId) +
            payload.media.map(ClipboardMedia::originalId)
    if (objectIds.any(String::isBlank)) return ClipboardPayloadIssue.InvalidObjectId
    if (objectIds.toSet().size != objectIds.size) return ClipboardPayloadIssue.DuplicateObjectId

    fun validTransform(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
    ): Boolean =
        x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && rotation.isFinite() &&
            kotlin.math.abs(x) <= 1_000_000f && kotlin.math.abs(y) <= 1_000_000f &&
            width > 0f && height > 0f && width <= 100_000f && height <= 100_000f

    if (payload.nodes.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        } ||
        payload.groups.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        } ||
        payload.media.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        }
    ) {
        return ClipboardPayloadIssue.InvalidTransform
    }
    if (payload.version < 6 && payload.nodes.any { it.vectorPath != null }) return ClipboardPayloadIssue.UnsupportedVersion
    if (payload.nodes.any { NodeShape.fromToken(it.shapeToken) == null }) {
        return ClipboardPayloadIssue.UnsupportedShape
    }
    if (payload.media.any {
            it.assetId.isBlank() || MediaKind.fromToken(it.mediaKind) == null ||
                it.thumbnailAssetId?.isBlank() == true
        }
    ) {
        return ClipboardPayloadIssue.InvalidMedia
    }

    val groupIds = payload.groups.mapTo(mutableSetOf(), ClipboardGroup::originalId)
    if (payload.nodes.any { it.parentOriginalId != null && it.parentOriginalId !in groupIds } ||
        payload.media.any { it.parentOriginalId != null && it.parentOriginalId !in groupIds } ||
        payload.groups.any {
            it.parentOriginalId != null &&
                (it.parentOriginalId !in groupIds || it.parentOriginalId == it.originalId)
        }
    ) {
        return ClipboardPayloadIssue.InvalidParent
    }
    val parentByGroupId = payload.groups.associate { it.originalId to it.parentOriginalId }
    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()

    fun hasParentCycle(groupId: String): Boolean {
        if (groupId in visited) return false
        if (!visiting.add(groupId)) return true
        val cyclic = parentByGroupId[groupId]?.let(::hasParentCycle) == true
        visiting.remove(groupId)
        visited += groupId
        return cyclic
    }
    if (groupIds.any(::hasParentCycle)) return ClipboardPayloadIssue.ParentCycle

    val nodeIds = payload.nodes.mapTo(mutableSetOf(), ClipboardNode::originalId)
    nodeIds += payload.media.map { it.originalId }
    if (payload.relations.any { relation ->
            relation.sourceId !in nodeIds || relation.targetId !in nodeIds ||
                (relation.sourceId == relation.targetId && (payload.version < 5 || relation.geometry == null)) ||
                (
                    relation.geometry != null &&
                        (
                            payload.version < 5 ||
                                !relation.geometry.allowsEndpoints(CanvasObjectId(relation.sourceId), CanvasObjectId(relation.targetId))
                        )
                ) ||
                runCatching { RelationDirection.valueOf(relation.direction) }.isFailure
        }
    ) {
        return ClipboardPayloadIssue.InvalidRelation
    }
    val relationIds = payload.relations.mapNotNull { it.originalId }
    if (relationIds.any(String::isBlank) || relationIds.distinct().size != relationIds.size) return ClipboardPayloadIssue.InvalidRelation
    if (payload.sequenceDiagrams.isNotEmpty() &&
        runCatching {
            require(
                payload.sequenceDiagrams
                    .map { it.containerId }
                    .distinct()
                    .size == payload.sequenceDiagrams.size,
            )
            require(payload.relations.all { it.originalId != null })
            val snapshot =
                clipboardMaterialization(
                    payload,
                    Vec2.Zero,
                    objectIds.associateWith(::CanvasObjectId),
                    payload.relations.mapIndexed { index, edge -> RelationId(edge.originalId ?: "clipboard_edge_$index") },
                    0,
                )
            require(snapshot.hasValidSequenceBindings())
        }.isFailure
    ) {
        return ClipboardPayloadIssue.InvalidSequence
    }
    return null
}
