package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.BackendContractException
import cg.creamgod.boarderless.data.remote.CanvasObjectDto
import cg.creamgod.boarderless.data.remote.RelationDto
import cg.creamgod.boarderless.data.remote.WorkspaceStateDto
import cg.creamgod.boarderless.data.remote.toDomainWorkspace
import cg.creamgod.boarderless.data.remote.toExpandedDtos
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.Vec2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.long
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.MediaNode
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BackendProjectionTest {
    @Test fun groupedMediaCreatePayloadsRoundTripThroughSerializedProjection() {
        val parent = GroupFrame(CanvasObjectId("group"), zIndex = 2,
            transform = CanvasTransform(Vec2(50f, 70f), CanvasSize(700f, 600f), 45f), title = "Assets")
        val media = MediaKind.entries.mapIndexed { index, kind ->
            MediaNode(CanvasObjectId("node-${kind.token}"), parentId = parent.id,
                zIndex = 10L + index, locked = index % 2 == 0,
                transform = CanvasTransform(Vec2(120f + index, -30f), CanvasSize(320f, 180f), 37f + index),
                assetId = "asset-${kind.token}", mediaKind = kind, altText = "素材 ${kind.token}",
                thumbnailAssetId = if (kind == MediaKind.Image) null else "poster-${kind.token}")
        }
        val operations = CreateObjectsOperation("media-roundtrip", listOf(parent) + media).toExpandedDtos(40)
        assertEquals(listOf(41L, 42L, 43L, 44L), operations.map { it.clientSeq })
        val projection = operations.map { dto ->
            val payload = dto.payload
            CanvasObjectDto(payload.getValue("objectId").jsonPrimitive.content,
                payload.getValue("objectType").jsonPrimitive.content, objectVersion = 1,
                parentId = payload["parentId"]?.jsonPrimitive?.content,
                zIndex = payload.getValue("zIndex").jsonPrimitive.long,
                locked = payload.getValue("locked").jsonPrimitive.boolean,
                transform = payload.getValue("transform").jsonObject,
                properties = payload.getValue("properties").jsonObject)
        }
        // A state response need not preserve create order; parent resolution uses the full map.
        val wire = Json.encodeToString(state(projection.reversed()).copy(workspaceVersion = 4, throughServerSeq = 44))
        val reopened = Json.decodeFromString<WorkspaceStateDto>(wire).toDomainWorkspace("Roundtrip")
        assertEquals(parent, reopened.objects[parent.id])
        media.forEach { assertEquals(it, reopened.objects[it.id]) }
        assertEquals(4L, reopened.version)
        assertEquals(4, reopened.objects.size)
    }

    @Test fun blankMediaReferencesBecomeSafeContractErrorsNotRawConstructorFailures() {
        for ((asset, thumbnail) in listOf("" to null, " " to null, "asset" to "", "asset" to " ")) {
            val media = textObject(firstId, "ignored").copy(objectType = "media", properties = buildJsonObject {
                put("assetId", asset); put("mediaKind", "video")
                put("altText", "https://private.invalid/?token=fixture-secret")
                thumbnail?.let { put("thumbnailAssetId", it) }
            })
            val failure = assertFailsWith<BackendContractException> {
                state(listOf(media)).toDomainWorkspace("Malformed media")
            }
            if (asset.isBlank()) {
                assertEquals("Object $firstId requires non-blank property 'assetId'", failure.message)
            } else {
                assertEquals("Object $firstId has invalid properties", failure.message)
                assertTrue(failure.cause is IllegalArgumentException)
            }
            assertFalse(failure.message.orEmpty().contains("fixture-secret"))
        }
    }
    private val firstId = "00000000-0000-0000-0000-000000000001"
    private val secondId = "00000000-0000-0000-0000-000000000002"

    @Test
    fun validProjectionPreservesObjectsAndRelationSemantics() {
        val state = WorkspaceStateDto(
            workspaceId = "00000000-0000-0000-0000-000000000010",
            workspaceVersion = 4,
            throughServerSeq = 8,
            objects = listOf(textObject(firstId, "First"), textObject(secondId, "Second")),
            relations = listOf(relation(direction = "both")),
        )

        val workspace = state.toDomainWorkspace("Projection")

        assertEquals(2, workspace.objects.size)
        assertEquals("First", assertIs<TextNode>(workspace.objects.values.first { it.id.value == firstId }).text)
        assertEquals(RelationDirection.Both, workspace.relations.values.single().direction)
        assertEquals(4L, workspace.version)
    }

    @Test
    fun mediaProjectionKeepsOnlyDurableAssetReferences() {
        val media = textObject(firstId, "ignored").copy(
            objectType = "media",
            properties = buildJsonObject {
                put("assetId", "asset-original")
                put("mediaKind", "video")
                put("altText", "Launch demo")
                put("thumbnailAssetId", "asset-poster")
            },
        )

        val node = assertIs<MediaNode>(
            state(objects = listOf(media)).toDomainWorkspace("Media").objects.values.single(),
        )

        assertEquals("asset-original", node.assetId)
        assertEquals(MediaKind.Video, node.mediaKind)
        assertEquals("Launch demo", node.altText)
        assertEquals("asset-poster", node.thumbnailAssetId)
    }

    @Test
    fun malformedMediaProjectionFailsExplicitly() {
        val missingAsset = textObject(firstId, "ignored").copy(
            objectType = "media",
            properties = buildJsonObject { put("mediaKind", "image") },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(missingAsset)).toDomainWorkspace("Missing asset")
        }

        val unknownKind = missingAsset.copy(
            properties = buildJsonObject {
                put("assetId", "asset-original")
                put("mediaKind", "audio")
            },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(unknownKind)).toDomainWorkspace("Unknown media")
        }
    }

    @Test
    fun unknownObjectTypeFailsInsteadOfSilentlyDroppingContent() {
        val unsupported = textObject(firstId, "Image").copy(objectType = "image")
        val state = state(objects = listOf(unsupported))

        val error = assertFailsWith<BackendContractException> {
            state.toDomainWorkspace("Unsupported")
        }

        assertEquals(
            "Unsupported canvas object type 'image' for object $firstId",
            error.message,
        )
    }

    @Test
    fun unknownDirectionAndDanglingRelationsFailExplicitly() {
        val unknownDirection = state(
            objects = listOf(textObject(firstId, "First"), textObject(secondId, "Second")),
            relations = listOf(relation(direction = "sideways")),
        )
        assertFailsWith<BackendContractException> {
            unknownDirection.toDomainWorkspace("Unknown direction")
        }

        val dangling = state(
            objects = listOf(textObject(firstId, "First")),
            relations = listOf(relation(direction = "forward")),
        )
        assertFailsWith<BackendContractException> {
            dangling.toDomainWorkspace("Dangling")
        }
    }

    @Test
    fun duplicateIdsAndInvalidVersionsFailExplicitly() {
        assertFailsWith<BackendContractException> {
            state(objects = listOf(textObject(firstId, "First"), textObject(firstId, "Duplicate")))
                .toDomainWorkspace("Duplicates")
        }
        assertFailsWith<BackendContractException> {
            state(objects = listOf(textObject(firstId, "First").copy(objectVersion = 0)))
                .toDomainWorkspace("Bad version")
        }
    }

    @Test
    fun malformedTransformsAndPropertiesFailInsteadOfUsingSilentDefaults() {
        val malformedTransform = textObject(firstId, "First").copy(
            transform = buildJsonObject {
                put("x", "not-a-number")
                put("y", 0f)
                put("width", 260f)
                put("height", 132f)
            },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(malformedTransform)).toDomainWorkspace("Bad transform")
        }

        val infiniteTransform = malformedTransform.copy(
            transform = buildJsonObject {
                put("x", "Infinity")
                put("y", 0f)
                put("width", 260f)
                put("height", 132f)
            },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(infiniteTransform)).toDomainWorkspace("Infinite transform")
        }

        val negativeSize = textObject(firstId, "First").copy(
            transform = buildJsonObject {
                put("x", 0f)
                put("y", 0f)
                put("width", -1f)
                put("height", 132f)
            },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(negativeSize)).toDomainWorkspace("Negative size")
        }

        val malformedText = textObject(firstId, "First").copy(
            properties = buildJsonObject { put("text", 42) },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(malformedText)).toDomainWorkspace("Bad properties")
        }
    }

    @Test
    fun shapeTokenRoundTripsAndUnknownShapesFailExplicitly() {
        NodeShape.entries.forEach { shape ->
            val original = TextNode(CanvasObjectId(firstId), transform = CanvasTransform(Vec2(10f, 20f), CanvasSize(200f, 100f), 37f),
                text = "形狀🙂", shape = shape)
            val dto = CreateObjectsOperation("shape-roundtrip", listOf(original)).toExpandedDtos(40).single()
            val payload = dto.payload
            val objectDto = CanvasObjectDto(payload.getValue("objectId").jsonPrimitive.content,
                payload.getValue("objectType").jsonPrimitive.content, objectVersion = 1,
                zIndex = payload.getValue("zIndex").jsonPrimitive.long,
                locked = payload.getValue("locked").jsonPrimitive.boolean,
                transform = payload.getValue("transform").jsonObject,
                properties = payload.getValue("properties").jsonObject)
            val wire = Json.encodeToString(state(objects = listOf(objectDto)))
            val reopened = Json.decodeFromString<WorkspaceStateDto>(wire).toDomainWorkspace("Shapes")
            assertEquals(original, assertIs<TextNode>(reopened.objects.values.single()))
        }
        val diamond = textObject(firstId, "Decision").copy(
            properties = buildJsonObject {
                put("text", "Decision")
                put("colorToken", "amber")
                put("shapeToken", "diamond")
            },
        )

        val node = assertIs<TextNode>(
            state(objects = listOf(diamond)).toDomainWorkspace("Shapes").objects.values.single(),
        )
        assertEquals(NodeShape.Diamond, node.shape)

        val unknown = diamond.copy(
            properties = buildJsonObject {
                put("text", "Future")
                put("shapeToken", "starburst")
            },
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(unknown)).toDomainWorkspace("Unknown shape")
        }
    }

    @Test
    fun invalidParentGraphsAndSelfRelationsFailExplicitly() {
        val missingParent = textObject(firstId, "First").copy(
            parentId = "00000000-0000-0000-0000-000000000099",
        )
        assertFailsWith<BackendContractException> {
            state(objects = listOf(missingParent)).toDomainWorkspace("Missing parent")
        }

        val nonGroupParent = textObject(secondId, "Child").copy(parentId = firstId)
        assertFailsWith<BackendContractException> {
            state(objects = listOf(textObject(firstId, "Parent"), nonGroupParent))
                .toDomainWorkspace("Non-group parent")
        }

        val firstGroup = groupObject(firstId, "First group").copy(parentId = secondId)
        val secondGroup = groupObject(secondId, "Second group").copy(parentId = firstId)
        assertFailsWith<BackendContractException> {
            state(objects = listOf(firstGroup, secondGroup)).toDomainWorkspace("Parent cycle")
        }

        val selfRelation = relation(direction = "forward").copy(targetObjectId = firstId)
        assertFailsWith<BackendContractException> {
            state(objects = listOf(textObject(firstId, "First")), relations = listOf(selfRelation))
                .toDomainWorkspace("Self relation")
        }
    }

    private fun state(
        objects: List<CanvasObjectDto>,
        relations: List<RelationDto> = emptyList(),
    ) = WorkspaceStateDto(
        workspaceId = "00000000-0000-0000-0000-000000000010",
        workspaceVersion = 1,
        throughServerSeq = 1,
        objects = objects,
        relations = relations,
    )

    private fun textObject(id: String, text: String) = CanvasObjectDto(
        objectId = id,
        objectType = "text",
        objectVersion = 1,
        zIndex = 0,
        locked = false,
        transform = buildJsonObject {
            put("x", 0f)
            put("y", 0f)
            put("width", 260f)
            put("height", 132f)
            put("rotationDegrees", 0f)
        },
        properties = buildJsonObject {
            put("text", text)
            put("colorToken", "paper")
        },
    )

    private fun groupObject(id: String, title: String) = textObject(id, title).copy(
        objectType = "group",
        properties = buildJsonObject {
            put("title", title)
            put("colorToken", "group")
        },
    )

    private fun relation(direction: String) = RelationDto(
        relationId = "00000000-0000-0000-0000-000000000020",
        relationVersion = 1,
        sourceObjectId = firstId,
        targetObjectId = secondId,
        direction = direction,
        style = buildJsonObject { put("colorToken", "relation") },
    )
}
