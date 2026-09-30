package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.ClipboardGroup
import cg.creamgod.boarderless.feature.canvas.ClipboardNode
import cg.creamgod.boarderless.feature.canvas.ClipboardPayload
import cg.creamgod.boarderless.feature.canvas.ClipboardPayloadIssue
import cg.creamgod.boarderless.feature.canvas.ClipboardRelation
import cg.creamgod.boarderless.feature.canvas.validateClipboardPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardPayloadValidationTest {
    private fun node(
        id: String,
        parentId: String? = null,
        x: Float = 0f,
        width: Float = 240f,
        shape: String = "rounded",
    ) = ClipboardNode(
        originalId = id,
        x = x,
        y = 0f,
        width = width,
        height = 120f,
        rotationDegrees = 0f,
        text = id,
        colorToken = "paper",
        shapeToken = shape,
        parentOriginalId = parentId,
    )

    private fun group(id: String, parentId: String? = null) = ClipboardGroup(
        originalId = id,
        x = 0f,
        y = 0f,
        width = 480f,
        height = 320f,
        rotationDegrees = 0f,
        title = id,
        colorToken = "group",
        parentOriginalId = parentId,
    )

    @Test
    fun acceptsCurrentAndLegacySelections() {
        assertNull(validateClipboardPayload(ClipboardPayload(version = 3, nodes = listOf(node("a")))))
        assertNull(validateClipboardPayload(ClipboardPayload(version = 1, nodes = listOf(node("legacy")))))
    }

    @Test
    fun rejectsWrongFormatUnsupportedVersionAndEmptySelection() {
        assertEquals(
            ClipboardPayloadIssue.UnsupportedFormat,
            validateClipboardPayload(ClipboardPayload(format = "text/plain", nodes = listOf(node("a")))),
        )
        assertEquals(
            ClipboardPayloadIssue.UnsupportedVersion,
            validateClipboardPayload(ClipboardPayload(version = 4, nodes = listOf(node("a")))),
        )
        assertEquals(ClipboardPayloadIssue.Empty, validateClipboardPayload(ClipboardPayload(nodes = emptyList())))
    }

    @Test
    fun rejectsBlankAndDuplicateObjectIds() {
        assertEquals(
            ClipboardPayloadIssue.InvalidObjectId,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node(" ")))),
        )
        assertEquals(
            ClipboardPayloadIssue.DuplicateObjectId,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node("same")), groups = listOf(group("same")))),
        )
    }

    @Test
    fun rejectsUnsafeTransformsAndUnknownShapes() {
        assertEquals(
            ClipboardPayloadIssue.InvalidTransform,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node("wide", width = 0f)))),
        )
        assertEquals(
            ClipboardPayloadIssue.InvalidTransform,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node("far", x = 1_000_001f)))),
        )
        assertEquals(
            ClipboardPayloadIssue.UnsupportedShape,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node("shape", shape = "future-shape")))),
        )
    }

    @Test
    fun validatesParentReferencesAndRejectsGroupCycles() {
        assertNull(
            validateClipboardPayload(
                ClipboardPayload(nodes = listOf(node("child", "group")), groups = listOf(group("group"))),
            ),
        )
        assertEquals(
            ClipboardPayloadIssue.InvalidParent,
            validateClipboardPayload(ClipboardPayload(nodes = listOf(node("child", "missing")))),
        )
        assertEquals(
            ClipboardPayloadIssue.ParentCycle,
            validateClipboardPayload(
                ClipboardPayload(
                    nodes = emptyList(),
                    groups = listOf(group("a", "b"), group("b", "a")),
                ),
            ),
        )
    }

    @Test
    fun relationsMustReferenceDistinctNodesAndKnownDirections() {
        val nodes = listOf(node("a"), node("b"))
        assertNull(
            validateClipboardPayload(
                ClipboardPayload(
                    nodes = nodes,
                    relations = listOf(ClipboardRelation("a", "b", "Forward")),
                ),
            ),
        )
        assertEquals(
            ClipboardPayloadIssue.InvalidRelation,
            validateClipboardPayload(
                ClipboardPayload(
                    nodes = nodes,
                    relations = listOf(ClipboardRelation("a", "missing", "Forward")),
                ),
            ),
        )
        assertEquals(
            ClipboardPayloadIssue.InvalidRelation,
            validateClipboardPayload(
                ClipboardPayload(
                    nodes = nodes,
                    relations = listOf(ClipboardRelation("a", "b", "Sideways")),
                ),
            ),
        )
    }
}
