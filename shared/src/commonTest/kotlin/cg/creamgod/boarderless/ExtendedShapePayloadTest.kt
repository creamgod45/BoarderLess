package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.QuickSchemeStore
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.encodeToString
import kotlin.test.*

class ExtendedShapePayloadTest {
    @Test fun allShapesSurviveGroupedClipboardAndSavedSchemeReopen() {
        val shapes = NodeShape.entries
        val payload =
            ClipboardPayload(
                version = 4,
                groups = listOf(ClipboardGroup("group", 0f, 0f, 1000f, 1000f, 0f, "圖形", "group")),
                nodes =
                    shapes.mapIndexed { index, shape ->
                        ClipboardNode(
                            "shape-$index",
                            10f,
                            index * 150f,
                            200f,
                            100f,
                            37f,
                            "文字🙂",
                            "paper",
                            shapeToken = shape.token,
                            parentOriginalId = "group",
                        )
                    },
                relations = listOf(ClipboardRelation("shape-0", "shape-1", "Forward", "supports", "關聯")),
            )
        assertNull(validateClipboardPayload(payload))
        val settings = InMemorySettings()
        QuickSchemeStore(settings).save(ClipboardJson.encodeToString(payload), "Shapes", schemaVersion = 4)
        val reopened = decodeQuickSchemePayload(checkNotNull(QuickSchemeStore(settings).latest()))
        assertEquals(payload, reopened)
        assertEquals(shapes.map { it.token }, reopened!!.nodes.map { it.shapeToken })
    }
}
