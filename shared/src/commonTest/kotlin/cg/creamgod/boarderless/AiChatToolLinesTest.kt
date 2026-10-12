package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.feature.canvas.aiToolLines
import kotlin.test.Test
import kotlin.test.assertEquals

class AiChatToolLinesTest {
    private fun call(
        id: String,
        name: String,
    ) = AiDiagramToolCall("r", "s", id, name, "{}")

    @Test fun toolNamesAndOutcomesStayAlignedByIndex() {
        val lines =
            aiToolLines(
                listOf(call("1", "read_approved_graph"), call("2", "create_nodes")),
                listOf(
                    AiDiagramToolResult("1", AiDiagramToolStatus.Read),
                    AiDiagramToolResult("2", AiDiagramToolStatus.Rejected, failure = AiDiagramToolFailure.Schema),
                ),
            )
        assertEquals(listOf("read_approved_graph", "create_nodes"), lines.map { it.name })
        assertEquals(AiDiagramToolFailure.Schema, lines[1].failure)
        assertEquals(AiDiagramToolStatus.Read, lines[0].status)
    }
}
