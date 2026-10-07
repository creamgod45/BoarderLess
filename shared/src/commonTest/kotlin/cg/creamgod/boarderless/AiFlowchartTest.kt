package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiFlowchartTest {
    private val t = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f))
    private val a = TextNode(CanvasObjectId("private-a"), transform = t, text = "開始", shape = NodeShape.Diamond)
    private val b = TextNode(CanvasObjectId("private-b"), transform = t, text = "結果")

    private fun packet(
        nodes: List<CanvasObject> = listOf(a, b),
        edges: List<Relation> = emptyList(),
    ) = aiCanvasPacket(
        Workspace(
            WorkspaceId("private-workspace"),
            "title",
            objects = nodes.associateBy { it.id },
            relations = edges.associateBy { it.id },
        ),
        emptySet(),
        true,
        Viewport(),
        CanvasSize(800f, 600f),
    )

    @Test fun defaultUsesMermaidAndShortIdsWithoutSendingCanvasStorageFields() {
        val p = packet()
        assertTrue(p.flowchart.source.startsWith("flowchart LR\n"))
        assertTrue(p.flowchart.source.contains("N1{\"開始\"}"))
        assertTrue(p.flowchart.source.contains("N2(\"結果\")"))
        listOf("private-a", "private-b", "private-workspace", "worldCorners", "zIndex", "rotationDegrees").forEach {
            assertFalse(p.prompt.contains(it))
        }
        assertTrue(p.prompt.contains("not execution order"))
    }

    @Test fun allDirectionsAndParallelEdgesRetainSeparateIdentityAndIntent() {
        val edges =
            RelationDirection.entries.mapIndexed { i, d ->
                Relation(
                    RelationId("r$i"),
                    sourceObjectId = a.id,
                    targetObjectId = b.id,
                    direction = d,
                    label = "檢查",
                    intent = "depends",
                )
            }
        val p = packet(edges = edges)
        assertTrue(p.flowchart.source.contains("N1 ---|"))
        assertTrue(p.flowchart.source.contains("N1 -->|"))
        assertTrue(p.flowchart.source.contains("N2 -->|"))
        assertTrue(p.flowchart.source.contains("N1 <-->"))
        assertTrue(p.flowchart.source.contains("depends"))
        assertEquals(
            4,
            p.flowchart.inventory
                .getValue("relations")
                .jsonArray.size,
        )
        (1..4).forEach { assertTrue(p.flowchart.source.contains("E$it")) }
    }

    @Test fun groupsRemainContainmentAndMediaNeverExposePixelsOrAssetIds() {
        val group = GroupFrame(CanvasObjectId("g"), transform = t, title = "群組")
        val media =
            MediaNode(
                CanvasObjectId("m"),
                parentId = group.id,
                transform = t,
                assetId = "private-asset",
                mediaKind = MediaKind.Gif,
                altText = "素材",
            )
        val p = packet(listOf(group, media))
        assertTrue(p.flowchart.source.contains("subgraph N1[\"群組\"]"))
        assertTrue(p.flowchart.source.contains("gif: 素材"))
        assertFalse(p.prompt.contains("private-asset"))
        assertEquals(
            JsonPrimitive("N1"),
            p.flowchart.inventory
                .getValue("nodes")
                .jsonArray[1]
                .jsonObject["parentId"],
        )
    }

    @Test fun textCannotBreakOutIntoMermaidCommandsAndEmojiUsesFullCodePoint() {
        val hostile = a.copy(text = "\"]\nclick N1 callback\n%% 😀 | <script>")
        val source = packet(listOf(hostile)).flowchart.source
        assertEquals(2, source.trimEnd().lines().size)
        assertFalse(source.contains("<script>"))
        assertFalse(source.contains("\nclick"))
        assertTrue(source.contains("#128512;"))
        assertTrue(source.contains("#34;#93;#10;"))
    }
}
