package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiCanvasUnderstandingTest {
    private val transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f), 90f)
    private val g = GroupFrame(CanvasObjectId("g"), transform = transform, title = "服務")
    private val sub = GroupFrame(CanvasObjectId("sub"), parentId = g.id, transform = transform, title = "驗證")
    private val a = TextNode(CanvasObjectId("a"), parentId = sub.id, transform = transform, text = "登入")
    private val b = TextNode(CanvasObjectId("b"), parentId = g.id, transform = transform.copy(position = Vec2(300f, 0f)), text = "登入")
    private val media =
        MediaNode(
            CanvasObjectId("m"),
            parentId = g.id,
            transform = transform,
            assetId = "PRIVATE-ASSET",
            thumbnailAssetId = "PRIVATE-THUMB",
            mediaKind = MediaKind.Image,
            altText = "架構",
        )
    private val outside = TextNode(CanvasObjectId("outside"), transform = transform, text = "PRIVATE-OUTSIDE")
    private val objects = listOf(g, sub, a, b, media, outside)
    private val edges =
        listOf(
            Relation(RelationId("r1"), sourceObjectId = a.id, targetObjectId = b.id, label = "成功", intent = "授權"),
            Relation(RelationId("r2"), sourceObjectId = b.id, targetObjectId = a.id, direction = RelationDirection.Backward),
            Relation(RelationId("r3"), sourceObjectId = a.id, targetObjectId = media.id, direction = RelationDirection.Both),
            Relation(RelationId("external"), sourceObjectId = a.id, targetObjectId = outside.id),
        )
    private val workspace =
        Workspace(
            WorkspaceId("private-workspace"),
            "private title",
            12,
            objects.associateBy { it.id },
            edges.associateBy { it.id },
        )

    private fun packet(
        w: Workspace = workspace,
        ids: Set<CanvasObjectId> = setOf(g.id),
        whole: Boolean = false,
    ) = aiCanvasPacket(w, ids, whole, Viewport(Vec2(10f, 20f), 2f), CanvasSize(800f, 600f))

    private fun response(p: AiCanvasPacket) =
        buildJsonObject {
            put(
                "nodeIds",
                JsonArray(
                    p.flowchart.inventory
                        .getValue("nodes")
                        .jsonArray
                        .map { it.jsonObject.getValue("id") },
                ),
            )
            put(
                "links",
                JsonArray(
                    p.flowchart.inventory.getValue("relations").jsonArray.map { row ->
                        JsonObject(row.jsonObject.filterKeys { it in listOf("id", "source", "target", "direction") })
                    },
                ),
            )
            put(
                "parents",
                JsonArray(
                    p.flowchart.inventory.getValue("nodes").jsonArray.map { row ->
                        JsonObject(row.jsonObject.filterKeys { it in listOf("id", "parentId") })
                    },
                ),
            )
            put("summary", "這是測試文字，不代表已證明語意理解")
            put("uncertainties", JsonArray(listOf(JsonPrimitive("無圖片像素"))))
        }

    @Test fun scopedComplexGraphIsDeterministicAndDoesNotExportAssetsOrExternalContext() {
        val p = packet()
        assertEquals(
            p,
            packet(
                workspace.copy(
                    objects =
                        objects.reversed().associateBy {
                            it.id
                        },
                    relations = edges.reversed().associateBy { it.id },
                ),
            ),
        )
        listOf("PRIVATE-ASSET", "PRIVATE-THUMB", "PRIVATE-OUTSIDE", "private-workspace", "private title", "external").forEach {
            assertFalse(p.json.contains(it))
        }
        assertEquals(
            5,
            p.scene
                .getValue("nodes")
                .jsonArray.size,
        )
        assertEquals(
            3,
            p.scene
                .getValue("relations")
                .jsonArray.size,
        )
        assertTrue(p.json.contains("授權"))
        assertFalse(p.json.contains("worldCorners"))
        assertFalse(p.prompt.contains("rotationDegrees"))
        assertTrue(p.json.startsWith("flowchart LR"))
        assertTrue(p.json.contains("parentOutsideScope"))
        assertEquals(
            JsonPrimitive("backward"),
            p.scene
                .getValue("relations")
                .jsonArray[1]
                .jsonObject["direction"],
        )
        assertTrue(p.prompt.contains("untrusted data"))
    }

    @Test fun excludedParentIsExplicitAndRotatedGeometryIsInWorldCoordinates() {
        val p = packet(ids = setOf(a.id))
        val node =
            p.scene
                .getValue("nodes")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(JsonNull, node["parentId"])
        assertEquals(JsonPrimitive(true), node["parentOutsideScope"])
        val corner =
            node
                .getValue("worldCorners")
                .jsonArray
                .first()
                .jsonObject
        assertEquals(75f, corner.getValue("x").jsonPrimitive.float, 0.001f)
        assertEquals(-25f, corner.getValue("y").jsonPrimitive.float, 0.001f)
        assertTrue(
            p.scene
                .getValue("relations")
                .jsonArray
                .isEmpty(),
        )
    }

    @Test fun completeInventoryPassesButMissingDuplicatesAndInvertedEdgesFail() {
        val p = packet()
        val good = response(p)
        assertTrue(checkAiCanvasAnswer(p, good.toString()).isEmpty())
        val missing = JsonObject(good + ("nodeIds" to JsonArray(emptyList())))
        assertEquals(listOf("nodeIds"), checkAiCanvasAnswer(p, missing.toString()))
        val rows = good.getValue("links").jsonArray
        val reversed = JsonObject(rows.first().jsonObject + ("direction" to JsonPrimitive("backward")))
        assertEquals(
            listOf("links"),
            checkAiCanvasAnswer(p, JsonObject(good + ("links" to JsonArray(listOf(reversed) + rows.drop(1)))).toString()),
        )
        val parents = good.getValue("parents").jsonArray
        assertEquals(
            listOf("parents"),
            checkAiCanvasAnswer(
                p,
                JsonObject(good + ("parents" to JsonArray(List(parents.size) { parents.first() }))).toString(),
            ),
        )
    }

    @Test fun invalidAndOversizeInputsAreRejectedWithoutSilentTruncation() {
        assertFails { packet(ids = emptySet()) }
        assertFails { packet(ids = setOf(CanvasObjectId("unknown"))) }
        val huge = a.copy(text = "機".repeat(20000))
        assertFails { packet(workspace.copy(objects = mapOf(a.id to huge)), setOf(a.id)) }
        val many = (1..129).map { a.copy(id = CanvasObjectId("n$it")) }
        assertFails { packet(workspace.copy(objects = many.associateBy { it.id }), whole = true) }
        assertFails { checkAiCanvasAnswer(packet(), "```json\n{}\n```") }
        assertFails { checkAiCanvasAnswer(packet(), "x".repeat(128 * 1024 + 1)) }
        assertFails { checkAiCanvasAnswer(packet(), JsonObject(response(packet()) + ("summary" to JsonPrimitive(""))).toString()) }
    }
}
