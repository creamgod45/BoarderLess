package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.BackendContractException
import cg.creamgod.boarderless.data.remote.CanvasObjectDto
import cg.creamgod.boarderless.data.remote.RelationDto
import cg.creamgod.boarderless.data.remote.WorkspaceStateDto
import cg.creamgod.boarderless.data.remote.toDomainWorkspace
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BackendProjectionTest {
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
