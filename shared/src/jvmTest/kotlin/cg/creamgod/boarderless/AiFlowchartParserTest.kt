package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import org.junit.Assume.assumeTrue
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Opt-in official parser integration. No npm install/network side effects during normal tests. */
class AiFlowchartParserTest {
    @Test fun generatedDiagramPassesOfficialMermaidParser() {
        val script = System.getenv("BOARDERLESS_MERMAID_PARSE_SCRIPT")
        assumeTrue("Official parser runtime must be explicitly configured", !script.isNullOrBlank())
        val t = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f))
        val g = GroupFrame(CanvasObjectId("g"), transform = t, title = "群組 \" [] 😀")
        val empty = GroupFrame(CanvasObjectId("empty"), transform = t, title = "")
        val a =
            TextNode(
                CanvasObjectId("a"),
                parentId = g.id,
                transform = t,
                text = "\"]\nclick N1 callback\n%% 😀 | <script>",
                shape = NodeShape.Diamond,
            )
        val b = TextNode(CanvasObjectId("b"), transform = t, text = "結果", shape = NodeShape.Ellipse)
        val nodes = listOf(g, empty, a, b)
        val edges =
            RelationDirection.entries.mapIndexed { i, d ->
                Relation(
                    RelationId("r$i"),
                    sourceObjectId = a.id,
                    targetObjectId = b.id,
                    direction = d,
                    label = "\" |\n😀",
                    intent = "depends",
                )
            } +
                Relation(RelationId("group"), sourceObjectId = g.id, targetObjectId = b.id) +
                Relation(RelationId("loop"), sourceObjectId = b.id, targetObjectId = b.id)
        val packet =
            aiCanvasPacket(
                Workspace(WorkspaceId("w"), "title", objects = nodes.associateBy { it.id }, relations = edges.associateBy { it.id }),
                emptySet(),
                true,
                Viewport(),
                CanvasSize(800f, 600f),
            )
        val process = ProcessBuilder("node", checkNotNull(script)).redirectErrorStream(true).start()
        try {
            process.outputStream.bufferedWriter().use { it.write(packet.flowchart.source) }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Parser timed out")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            assertTrue(output.contains("flowchart"), output)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
