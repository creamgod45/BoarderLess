package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class WorkspaceDraftBackupReaderTest {
    private val baseline = Workspace(WorkspaceId("workspace"), "Private 中文 😀")
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2(1f, 2f), CanvasSize(120f, 80f)),
        text = "Quoted \"[{\\ text", shape = NodeShape.PlainText)
    private val operation = CreateObjectsOperation("local-operation", listOf(node))
    private val proposed = (operation.applyTo(baseline) as OperationResult.Applied).workspace
    private val review = WorkspaceDraftReview("draft", 0, 0, 0, 0, baseline, proposed, baseline,
        listOf(operation), true, true)
    private val reader = WorkspaceDraftBackupReader()
    private fun mutate(key: String, value: JsonElement?): String {
        val fields = Json.parseToJsonElement(review.toBackupJson()).jsonObject.toMutableMap()
        if (value == null) fields.remove(key) else fields[key] = value
        return JsonObject(fields).toString()
    }

    @Test fun readsExistingBackupExactlyWithoutTreatingSavedCurrentAsAuthority() {
        assertEquals(review, reader.read(review.toBackupJson(), baseline.id))
        assertEquals(baseline, review.current)
        assertTrue(reader.read(review.toBackupJson(), baseline.id).hasUnconfirmedSubmission)
    }

    @Test fun rejectsUnsupportedMissingOrAdditionalEnvelopeFieldsAndWrongScope() {
        listOf(mutate("schemaVersion", JsonPrimitive(2)), mutate("format", null),
            mutate("importSupported", JsonPrimitive(true)), mutate("ticket", JsonPrimitive("private")),
            mutate("baseServerSeq", JsonPrimitive(-1)), mutate("currentVersion", JsonPrimitive(9_007_199_254_740_992L)))
            .forEach { assertFailsWith<IllegalArgumentException> { reader.read(it, baseline.id) } }
        assertFailsWith<IllegalArgumentException> { reader.read(review.toBackupJson(), WorkspaceId("other")) }
    }

    @Test fun rejectsTamperedReplayDuplicateOperationsAndBrokenHierarchy() {
        val samples = listOf(review.copy(proposed = baseline),
            review.copy(operations = listOf(operation, operation)),
            review.copy(baseline = baseline.copy(objects = mapOf(node.id to node.copy(parentId = CanvasObjectId("missing"))))))
        samples.forEach { assertFailsWith<IllegalArgumentException> { reader.read(it.toBackupJson(), baseline.id) } }
    }

    @Test fun boundsInputBeforeRecursiveDecodeAndSanitizesFailure() {
        listOf("[".repeat(65) + "0" + "]".repeat(65), " ".repeat(4 * 1024 * 1024 + 1),
            "{\"private-secret\": [", " ").forEach {
            val failure = assertFailsWith<IllegalArgumentException> { reader.read(it, baseline.id) }
            assertNull(failure.cause)
            assertFalse("private-secret" in failure.message.orEmpty())
        }
    }
}
