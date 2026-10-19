package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CommittedProjectionReducerTest {
    private val empty = WorkspaceStateDto("workspace", 0, 0, emptyList())

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    private fun records(
        state: WorkspaceStateDto,
        vararg ops: Pair<String, String>,
    ) = ops.mapIndexed { index, op ->
        CommittedWorkspaceOperationDto(
            state.throughServerSeq + index + 1,
            "op-$index",
            "transaction",
            "actor",
            "client",
            index.toLong(),
            state.workspaceVersion,
            state.workspaceVersion + 1,
            op.first,
            json(op.second),
            1,
            "2026-10-03T00:00:00Z",
        )
    }

    private fun apply(
        state: WorkspaceStateDto,
        vararg ops: Pair<String, String>,
    ): WorkspaceStateDto {
        val records = records(state, *ops)
        return reduceCommittedProjection(
            state,
            "workspace",
            records,
            records.first().serverSeq,
            records.last().serverSeq,
            state.workspaceVersion + 1,
        )
    }

    private fun fixture() =
        apply(
            empty,
            "create_object" to """{"objectId":"group","objectType":"group","properties":{"title":"Group"}}""",
            "create_object" to
                """{"objectId":"text","objectType":"text","parentId":"group","properties":{"text":"Draft","colorToken":"paper"}}""",
            "create_object" to
                """{"objectId":"image","objectType":"media","properties":{"assetId":"image-asset","mediaKind":"image","thumbnailAssetId":"poster"}}""",
            "create_object" to """{"objectId":"gif","objectType":"media","properties":{"assetId":"gif-asset","mediaKind":"gif"}}""",
            "create_object" to """{"objectId":"video","objectType":"media","properties":{"assetId":"video-asset","mediaKind":"video"}}""",
            "create_relation" to
                """{"relationId":"link","sourceObjectId":"video","targetObjectId":"text","direction":"forward","label":"Link","style":{"colorToken":"violet","extra":"preserved"}}""",
        )

    @Test fun fullTransactionCreatesMixedMediaGroupTextAndRelationWithOneWorkspaceVersion() {
        val state = fixture()
        val workspace = state.toDomainWorkspace("Board")
        assertEquals(1L, state.workspaceVersion)
        assertEquals(6L, state.throughServerSeq)
        assertEquals(5, workspace.objects.size)
        assertEquals(
            setOf(MediaKind.Image, MediaKind.Gif, MediaKind.Video),
            workspace.objects.values
                .filterIsInstance<MediaNode>()
                .map { it.mediaKind }
                .toSet(),
        )
        assertEquals(CanvasObjectId("group"), workspace.objectById(CanvasObjectId("text"))?.parentId)
        assertEquals("poster", (workspace.objectById(CanvasObjectId("image")) as MediaNode).thumbnailAssetId)
        assertEquals(1, workspace.relations.size)
        assertTrue(workspace.objects.values.all { it.version == 1L })
        assertTrue(empty.objects.isEmpty())
    }

    @Test fun patchesMergePropertiesAndStyleButReplaceTransformAndPreserveExplicitNull() {
        val initial = fixture()
        val state =
            apply(
                initial,
                "move_objects" to
                    """{"moves":[{"objectId":"text","transform":{"x":30,"y":40,"width":200,"height":100,"rotationDegrees":37}}]}""",
                "update_object" to """{"objectId":"text","parentId":null,"locked":true,"properties":{"text":"Changed"}}""",
                "update_relation" to """{"relationId":"link","intent":null,"label":null,"direction":"both","style":{"colorToken":"blue"}}""",
            )
        val text = state.toDomainWorkspace("Board").objectById(CanvasObjectId("text")) as TextNode
        assertEquals(3L, text.version)
        assertEquals(Vec2(30f, 40f), text.transform.position)
        assertEquals(37f, text.transform.rotationDegrees)
        assertEquals("Changed", text.text)
        assertEquals("paper", text.colorToken)
        assertNull(text.parentId)
        assertTrue(text.locked)
        val relation = state.relations.single()
        assertEquals(2L, relation.relationVersion)
        assertNull(relation.label)
        assertEquals(
            "preserved",
            relation.style
                .getValue("extra")
                .jsonPrimitive.content,
        )
        assertEquals(
            "Draft",
            initial.objects
                .first { it.objectId == "text" }
                .properties
                .getValue("text")
                .jsonPrimitive.content,
        )
    }

    @Test fun explicitDeleteAndServerRecordedCascadeAreAppliedAtomically() {
        val initial = fixture()
        val removed =
            apply(
                initial,
                "delete_objects" to
                    """{"objectIds":["text","group"],"cascadedRelationIds":["link"]}""",
            )
        assertEquals(setOf("image", "gif", "video"), removed.objects.map { it.objectId }.toSet())
        assertTrue(removed.relations.isEmpty())
        assertEquals(1, initial.relations.size)
        val relationRemoved = apply(initial, "delete_relations" to """{"relationIds":["link"]}""")
        assertTrue(relationRemoved.relations.isEmpty())
        assertEquals(5, relationRemoved.objects.size)
        assertFailsWith<BackendContractException> {
            apply(initial, "delete_objects" to """{"objectIds":["text"],"cascadedRelationIds":[]}""")
        }
    }

    @Test fun invalidTailUnknownKindParentCycleAndDanglingReferencesNeverPublishPartialState() {
        val initial = fixture()
        val saved = initial.copy()
        for (tail in listOf(
            "future_operation" to "{}",
            "update_object" to """{"objectId":"missing","locked":true}""",
            "update_object" to """{"objectId":"group","parentId":"group"}""",
            "update_object" to """{"objectId":"text","parentId":"image"}""",
            "create_relation" to """{"relationId":"bad","sourceObjectId":"missing","targetObjectId":"text","direction":"forward"}""",
            "move_objects" to """{"moves":[{"objectId":"text","transform":{"width":-1}}]}""",
        )) {
            assertFailsWith<BackendContractException> {
                apply(initial, "update_object" to """{"objectId":"text","properties":{"text":"Do not publish"}}""", tail)
            }
            assertEquals(saved, initial)
        }
    }

    @Test fun scopeGapVersionAndMalformedFieldRequireResyncRatherThanCheckpointAdvance() {
        val initial = fixture()
        val records = records(initial, "update_object" to """{"objectId":"text","locked":true}""")
        for ((workspace, from, version) in listOf(
            Triple("foreign", 7L, 2L),
            Triple("workspace", 8L, 2L),
            Triple("workspace", 7L, 3L),
        )) {
            assertFailsWith<BackendContractException> {
                reduceCommittedProjection(initial, workspace, records, from, from, version)
            }
        }
        assertFailsWith<BackendContractException> {
            apply(initial, "update_object" to """{"objectId":"text","locked":"true"}""")
        }
        assertFailsWith<BackendContractException> {
            apply(initial, "update_object" to """{"objectId":"text","properties":null}""")
        }
        assertEquals(6L, initial.throughServerSeq)
    }
}
