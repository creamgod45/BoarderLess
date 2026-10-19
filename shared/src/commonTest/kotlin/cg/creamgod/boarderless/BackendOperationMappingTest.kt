package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.toExpandedDtos
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.GroupFrameAttributes
import cg.creamgod.boarderless.domain.history.GroupFrameAttributesChange
import cg.creamgod.boarderless.domain.history.MediaNodeAttributes
import cg.creamgod.boarderless.domain.history.MediaNodeAttributesChange
import cg.creamgod.boarderless.domain.history.ParentChange
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.history.TextNodeAttributes
import cg.creamgod.boarderless.domain.history.TextNodeAttributesChange
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.TransformChange
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.history.UpdateGroupFrameAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateMediaNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateTextNodeAttributesOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendOperationMappingTest {
    @Test
    fun mediaCreationSerializesStableReferencesWithoutUrlsOrLocalPaths() {
        val operation =
            CreateObjectsOperation(
                operationId = "create-media",
                objects =
                    listOf(
                        MediaNode(
                            id = CanvasObjectId("media-node"),
                            transform = CanvasTransform(Vec2.Zero, CanvasSize(640f, 360f)),
                            assetId = "asset-original",
                            mediaKind = MediaKind.Video,
                            altText = "Product demo",
                            thumbnailAssetId = "asset-poster",
                        ),
                    ),
            )

        val dto = operation.toExpandedDtos(startingSequence = 4).single()
        val properties = dto.payload.getValue("properties").jsonObject

        assertEquals("create_object", dto.kind)
        assertEquals(
            "media",
            dto.payload
                .getValue("objectType")
                .jsonPrimitive.content,
        )
        assertEquals("asset-original", properties.getValue("assetId").jsonPrimitive.content)
        assertEquals("video", properties.getValue("mediaKind").jsonPrimitive.content)
        assertEquals("asset-poster", properties.getValue("thumbnailAssetId").jsonPrimitive.content)
        assertEquals(
            setOf("assetId", "mediaKind", "altText", "thumbnailAssetId"),
            properties.keys,
        )
    }

    @Test
    fun mediaAttributeUpdateUsesPartialObjectContract() {
        val operation =
            UpdateMediaNodeAttributesOperation(
                operationId = "describe-media",
                changes =
                    listOf(
                        MediaNodeAttributesChange(
                            objectId = CanvasObjectId("media-node"),
                            expectedVersion = 3,
                            before = MediaNodeAttributes(2, false, ""),
                            after = MediaNodeAttributes(2, true, "Architecture overview"),
                        ),
                    ),
            )

        val dto = operation.toExpandedDtos(startingSequence = 9).single()
        val properties = dto.payload.getValue("properties").jsonObject

        assertEquals("update_object", dto.kind)
        assertEquals(
            true,
            dto.payload
                .getValue("locked")
                .jsonPrimitive.content
                .toBoolean(),
        )
        assertEquals(setOf("altText"), properties.keys)
        assertEquals("Architecture overview", properties.getValue("altText").jsonPrimitive.content)
    }

    @Test
    fun multiNodeTextEditExpandsIntoOrderedBackendOperations() {
        val firstId = CanvasObjectId("node-1")
        val secondId = CanvasObjectId("node-2")
        val operation =
            EditTextOperation(
                operationId = "batch-edit",
                changes =
                    listOf(
                        TextChange(firstId, expectedVersion = 3, before = "First", after = "First revised"),
                        TextChange(secondId, expectedVersion = 7, before = "Second", after = "Second revised"),
                    ),
            )

        val mapped = operation.toExpandedDtos(startingSequence = 40)

        assertEquals(listOf(41L, 42L), mapped.map { it.clientSeq })
        assertEquals(listOf("update_object", "update_object"), mapped.map { it.kind })
        assertEquals(mapOf(firstId.value to 3L), mapped[0].expectedObjectVersions)
        assertEquals(mapOf(secondId.value to 7L), mapped[1].expectedObjectVersions)
        assertEquals(
            firstId.value,
            mapped[0]
                .payload
                .getValue("objectId")
                .jsonPrimitive.content,
        )
        assertEquals(
            secondId.value,
            mapped[1]
                .payload
                .getValue("objectId")
                .jsonPrimitive.content,
        )
        assertEquals(
            "First revised",
            mapped[0]
                .payload
                .getValue("properties")
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content,
        )
        assertEquals(
            "Second revised",
            mapped[1]
                .payload
                .getValue("properties")
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun partialAttributeUpdatesOnlySendChangedProperties() {
        val textId = CanvasObjectId("text-node")
        val textOperation =
            UpdateTextNodeAttributesOperation(
                operationId = "text-color",
                changes =
                    listOf(
                        TextNodeAttributesChange(
                            objectId = textId,
                            expectedVersion = 4,
                            before = TextNodeAttributes(zIndex = 2, locked = false, colorToken = "blue"),
                            after = TextNodeAttributes(zIndex = 2, locked = false, colorToken = "amber"),
                        ),
                    ),
            )

        val textPayload = textOperation.toExpandedDtos(startingSequence = 8).single().payload
        assertEquals(setOf("objectId", "properties"), textPayload.keys)
        assertEquals(
            setOf("colorToken"),
            textPayload.getValue("properties").jsonObject.keys,
        )
        assertEquals(
            "amber",
            textPayload
                .getValue("properties")
                .jsonObject
                .getValue("colorToken")
                .jsonPrimitive.content,
        )

        val groupId = CanvasObjectId("group-frame")
        val groupOperation =
            UpdateGroupFrameAttributesOperation(
                operationId = "group-title",
                changes =
                    listOf(
                        GroupFrameAttributesChange(
                            objectId = groupId,
                            expectedVersion = 5,
                            before =
                                GroupFrameAttributes(
                                    zIndex = 1,
                                    locked = false,
                                    colorToken = "violet",
                                    title = "Ideas",
                                ),
                            after =
                                GroupFrameAttributes(
                                    zIndex = 1,
                                    locked = false,
                                    colorToken = "violet",
                                    title = "Next ideas",
                                ),
                        ),
                    ),
            )

        val groupPayload = groupOperation.toExpandedDtos(startingSequence = 9).single().payload
        assertEquals(setOf("objectId", "properties"), groupPayload.keys)
        assertEquals(
            setOf("title"),
            groupPayload.getValue("properties").jsonObject.keys,
        )
        assertEquals(
            "Next ideas",
            groupPayload
                .getValue("properties")
                .jsonObject
                .getValue("title")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun shapeUpdateUsesTheExistingPartialPropertiesContract() {
        val operation =
            UpdateTextNodeAttributesOperation(
                operationId = "text-shape",
                changes =
                    listOf(
                        TextNodeAttributesChange(
                            objectId = CanvasObjectId("shape-node"),
                            expectedVersion = 2,
                            before = TextNodeAttributes(0, false, "paper", NodeShape.RoundedRectangle),
                            after = TextNodeAttributes(0, false, "paper", NodeShape.Diamond),
                        ),
                    ),
            )

        val payload = operation.toExpandedDtos(startingSequence = 3).single().payload

        assertEquals(setOf("objectId", "properties"), payload.keys)
        assertEquals(
            setOf("shapeToken"),
            payload.getValue("properties").jsonObject.keys,
        )
        assertEquals(
            "diamond",
            payload
                .getValue("properties")
                .jsonObject
                .getValue("shapeToken")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun moveAndReparentTransactionPreservesSequentialObjectVersions() {
        val nodeId = CanvasObjectId("node")
        val secondNodeId = CanvasObjectId("node-2")
        val groupId = CanvasObjectId("lane")
        val before = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val after = before.copy(position = Vec2(300f, 200f))
        val operation =
            TransactionOperation(
                operationId = "move-reparent",
                operations =
                    listOf(
                        TransformObjectsOperation(
                            operationId = "move",
                            changes =
                                listOf(
                                    TransformChange(nodeId, 4, before, after),
                                    TransformChange(secondNodeId, 7, before, after),
                                ),
                        ),
                        ReparentObjectsOperation(
                            operationId = "reparent",
                            changes =
                                listOf(
                                    ParentChange(nodeId, 5, null, groupId),
                                    ParentChange(secondNodeId, 8, null, groupId),
                                ),
                        ),
                    ),
            )

        val mapped = operation.toExpandedDtos(startingSequence = 20)

        assertEquals(listOf("move_objects", "update_object", "update_object"), mapped.map { it.kind })
        assertEquals(listOf(21L, 22L, 23L), mapped.map { it.clientSeq })
        assertEquals(mapOf(nodeId.value to 4L, secondNodeId.value to 7L), mapped[0].expectedObjectVersions)
        assertEquals(mapOf(nodeId.value to 5L), mapped[1].expectedObjectVersions)
        assertEquals(mapOf(secondNodeId.value to 8L), mapped[2].expectedObjectVersions)
        assertEquals(
            groupId.value,
            mapped[1]
                .payload
                .getValue("parentId")
                .jsonPrimitive.content,
        )
        assertEquals(
            groupId.value,
            mapped[2]
                .payload
                .getValue("parentId")
                .jsonPrimitive.content,
        )
    }
}
