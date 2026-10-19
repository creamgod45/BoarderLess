package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class PenPathDraftTest {
    private val size = CanvasSize(200f, 100f)
    private val start = PenAnchor(Vec2(10f, 10f))
    private val end = PenAnchor(Vec2(180f, 80f))

    @Test fun serializesOpenStrokeAndClosedFilledCommandsWithoutMarkup() {
        val draft =
            PenPathDraft(size)
                .append(start)
                .append(end)
                .append(PenAnchor(Vec2(10f, 80f)))
                .copy(closed = true, style = VectorPathStyle(fillColorToken = "#abcdef", fillRule = VectorFillRule.EvenOdd))
        assertEquals(draft, Json.decodeFromString<PenPathDraft>(Json.encodeToString(draft)))
        val path = draft.toVectorPath()
        assertEquals(path, Json.decodeFromString<VectorPath>(Json.encodeToString(path)))
        assertIs<VectorPathCommand.Close>(path.commands.last())
        assertEquals(VectorFillRule.EvenOdd, path.style.fillRule)
        val encoded =
            Json
                .parseToJsonElement(Json.encodeToString(path))
                .jsonObject
                .getValue("commands")
                .jsonArray
        assertEquals(
            listOf("move", "line", "line", "line", "close"),
            encoded.map {
                it.jsonObject
                    .getValue("type")
                    .jsonPrimitive.content
            },
        )
        assertNull(
            PenPathDraft(size)
                .append(start)
                .append(end)
                .toVectorPath()
                .style.fillColorToken,
        )
    }

    @Test fun usesLineQuadraticAndCubicAndClosingHandles() {
        val first = start.copy(incoming = Vec2(5f, 5f), outgoing = Vec2(20f, 0f))
        val second = end.copy(incoming = Vec2(170f, 60f))
        val third = PenAnchor(Vec2(40f, 80f), outgoing = Vec2(30f, 60f))
        val path = PenPathDraft(size, listOf(first, second, third), closed = true).toVectorPath()
        assertEquals(VectorPathCommand.Cubic(first.outgoing!!, second.incoming!!, second.point), path.commands[1])
        assertIs<VectorPathCommand.Line>(path.commands[2])
        assertEquals(VectorPathCommand.Cubic(third.outgoing!!, first.incoming!!, first.point), path.commands[3])
        assertIs<VectorPathCommand.Quadratic>(
            PenPathDraft(size, listOf(start.copy(outgoing = Vec2(50f, 20f)), end)).toVectorPath().commands[1],
        )
        assertIs<VectorPathCommand.Quadratic>(
            PenPathDraft(size, listOf(start, end.copy(incoming = Vec2(160f, 10f)))).toVectorPath().commands[1],
        )
    }

    @Test fun movingAnchorTranslatesHandlesAndUndoRedoRestoresEveryProperty() {
        val initial = PenPathDraft(size, listOf(start.copy(incoming = Vec2(0f, 5f), outgoing = Vec2(20f, 15f)), end))
        val moved = initial.replace(0, initial.anchors[0].translated(Vec2(30f, 40f)))
        assertEquals(Vec2(30f, 45f), moved.anchors[0].incoming)
        assertEquals(Vec2(50f, 55f), moved.anchors[0].outgoing)
        val history = PenPathHistory(initial).edit(moved).edit(moved.copy(style = VectorPathStyle(strokeWidth = 8f)))
        assertEquals(initial, history.undo().undo().draft)
        assertEquals(
            history.draft,
            history
                .undo()
                .undo()
                .redo()
                .redo()
                .draft,
        )
        assertTrue(
            history
                .undo()
                .edit(initial)
                .redoStack
                .isEmpty(),
        )
    }

    @Test fun deletingAnchorReopensTooSmallContourAndClosedDraftCannotAppend() {
        val closed = PenPathDraft(size, listOf(start, end, PenAnchor(Vec2(10f, 80f))), closed = true)
        assertTrue(closed.remove(1).closed)
        assertFalse(closed.remove(1).remove(1).closed)
        assertEquals(2, closed.remove(1).anchors.size)
        assertFails { closed.append(start) }
        assertFails { closed.replace(-1, end) }
        assertFails { closed.remove(3) }
        assertFails { PenPathDraft(size, listOf(start), closed = true) }
        assertFails { PenPathDraft(size).toVectorPath() }
    }

    @Test fun rejectsMalformedSubpathsFutureSchemaAndUnboundedInput() {
        val move = VectorPathCommand.Move(start.point)
        val line = VectorPathCommand.Line(end.point)
        listOf(
            listOf(line, move),
            listOf(move, move),
            listOf(move, VectorPathCommand.Close),
            listOf(move, line, VectorPathCommand.Close, line),
            listOf(move, line, move),
        ).forEach {
            assertFails { VectorPath(size, it) }
        }
        assertFails { VectorPath(size, listOf(move, line), schemaVersion = 2) }
        assertFails { VectorPath(size, listOf(move) + List(4096) { line }) }
        assertFails { VectorPath(size, List(129) { listOf(move, line) }.flatten()) }
        assertFails { PenPathDraft(size, List(1025) { start }) }
        assertFails { PenAnchor(Vec2(1_000_001f, 0f)) }
        assertFails { PenPathDraft(CanvasSize(0f, 20f)) }
        assertFails { PenPathDraft(size, schemaVersion = 2) }
    }

    @Test fun rejectsScriptColorUrlsAndNonFiniteGeometry() {
        assertFails { VectorPathStyle(fillColorToken = "url(https://private)") }
        assertFails { VectorPathStyle(strokeColorToken = "<script>") }
        assertFails { VectorPathStyle(fillColorToken = null, strokeColorToken = null) }
        assertFails { VectorPathStyle(strokeWidth = Float.NaN) }
        assertFails { VectorPathStyle(strokeWidth = 0f) }
        assertFails { VectorPathStyle(strokeWidth = 129f) }
        assertFails { Vec2(Float.POSITIVE_INFINITY, 0f) }
        assertFails {
            Json.decodeFromString<VectorPath>(
                "{\"viewBox\":{\"width\":200,\"height\":100},\"commands\":[{\"type\":\"script\",\"point\":{\"x\":0,\"y\":0}}]}",
            )
        }
    }

    @Test fun historyIsBoundedAndNoOpDoesNotClearRedo() {
        val initial = PenPathDraft(size, listOf(start, end))
        var history = PenPathHistory(initial)
        repeat(150) { number -> history = history.edit(history.draft.replace(0, start.copy(point = Vec2(number.toFloat(), 1f)))) }
        assertEquals(100, history.undoStack.size)
        val undone = history.undo()
        assertEquals(undone, undone.edit(undone.draft))
        assertEquals(history.draft, undone.redo().draft)
    }

    @Test fun twoCurvedAnchorsCanCloseWithoutArtificialTriangleRestriction() {
        val first = PenAnchor(Vec2(10f, 50f), incoming = Vec2(10f, 90f), outgoing = Vec2(10f, 10f))
        val second = PenAnchor(Vec2(190f, 50f), incoming = Vec2(190f, 10f), outgoing = Vec2(190f, 90f))
        val path = PenPathDraft(size, listOf(first, second), closed = true).toVectorPath()
        assertEquals(4, path.commands.size)
        assertIs<VectorPathCommand.Cubic>(path.commands[1])
        assertIs<VectorPathCommand.Cubic>(path.commands[2])
        assertIs<VectorPathCommand.Close>(path.commands[3])
    }
}
