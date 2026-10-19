package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class QuickSchemeTransferTest {
    private val selection = ClipboardPayload(nodes = listOf(ClipboardNode("original", 0f, 0f, 100f, 80f, 0f, "方案🙂\n中文", "paper")))

    private fun scheme(payload: ClipboardPayload = selection) =
        QuickScheme(73, "方案🙂", ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), payload.version)

    private class Destination : DraftBackupDestination {
        var content: String? = null
        var closed = 0
        var beforeWrite: () -> Unit = {}

        override suspend fun write(
            json: String,
            canWrite: () -> Boolean,
        ) {
            beforeWrite()
            check(canWrite())
            content = json
        }

        override fun close() {
            closed++
        }
    }

    @Test fun supportedSelectionsRoundTripWithoutLocalLibraryIdsOrAutomaticImport() {
        for (version in 1..5) {
            val original = scheme(selection.copy(version = version))
            val text = QuickSchemeTransferCodec.encode(original)
            assertFalse("\"id\"" in text)
            val reviewed = QuickSchemeTransferCodec.decode(text)
            assertEquals(0, reviewed.id)
            assertEquals(original.name, reviewed.name)
            assertEquals(version, reviewed.schemaVersion)
            assertEquals(decodeQuickSchemePayload(original), decodeQuickSchemePayload(reviewed))
        }
    }

    @Test fun malformedDuplicateUnknownMissingVersionAndInvalidUnicodeFilesAreRejected() {
        val text = QuickSchemeTransferCodec.encode(scheme())
        val root = Json.parseToJsonElement(text).jsonObject
        val invalid =
            listOf(
                "",
                "[]",
                "{}",
                text.replace("boarderless.quick-scheme", "other"),
                JsonObject(root + ("schemaVersion" to JsonPrimitive(2))).toString(),
                JsonObject(root - "sourceWorkspaceId").toString(),
                JsonObject(root + ("privateKey" to JsonPrimitive("never-show"))).toString(),
                JsonObject(root + ("selection" to JsonObject(root.getValue("selection").jsonObject - "version"))).toString(),
                text.replace("\"name\":", "\"name\":\"first\",\"na\\u006de\":"),
                text.replace("方案🙂", "\\uD800"),
                " ".repeat(4 * 1024 * 1024 + 1),
            )
        for (value in invalid) {
            val error = assertFails { QuickSchemeTransferCodec.decode(value) }
            assertEquals("Scheme file is invalid or unsupported", error.message)
        }
        assertFails { QuickSchemeTransferCodec.encode(scheme(selection.copy(nodes = emptyList()))) }
    }

    @Test fun objectCountsAndDeepHierarchyAreBoundedBeforeRecursiveValidation() {
        val oversized = selection.copy(nodes = (0..1000).map { selection.nodes.single().copy(originalId = "node-$it") })
        assertFails { QuickSchemeTransferCodec.encode(scheme(oversized)) }
        val groups =
            (0..64).map {
                ClipboardGroup(
                    "group-$it",
                    0f,
                    0f,
                    100f,
                    80f,
                    0f,
                    "Group",
                    "paper",
                    if (it ==
                        64
                    ) {
                        null
                    } else {
                        "group-${it + 1}"
                    },
                )
            }
        assertFails { QuickSchemeTransferCodec.encode(scheme(selection.copy(groups = groups))) }
        val allowed = selection.copy(groups = groups.drop(1))
        val text = QuickSchemeTransferCodec.encode(scheme(allowed))
        assertEquals(64, decodeQuickSchemePayload(QuickSchemeTransferCodec.decode(text))!!.groups.size)
    }

    @Test fun mediaProvenanceSurvivesTransferButDoesNotAuthorizeAnotherCanvas() {
        val media = ClipboardMedia("image", 0f, 0f, 100f, 80f, 0f, "asset", "image")
        val original = scheme(selection.copy(media = listOf(media))).copy(sourceWorkspaceId = "source")
        val reviewed = QuickSchemeTransferCodec.decode(QuickSchemeTransferCodec.encode(original))
        assertEquals("source", reviewed.sourceWorkspaceId)
        val payload = assertNotNull(decodeQuickSchemePayload(reviewed))
        assertFalse(quickSchemeMediaAvailable(reviewed, payload, WorkspaceId("destination"), emptyMap()))
        assertFails { QuickSchemeTransferCodec.encode(original.copy(sourceWorkspaceId = null)) }
    }

    @Test fun exportSelectsDestinationThenReadsFreshAndWritesOnlyTheReviewedDefinition() =
        runTest {
            val selected = scheme()
            val destination = Destination()
            val order = mutableListOf<String>()
            val runtime =
                DraftBackupRuntime { name ->
                    assertEquals("boarderless-scheme.json", name)
                    order.add("choose")
                    destination
                }
            val result =
                exportQuickScheme(runtime, selected, { true }) {
                    order.add("read")
                    assertNull(destination.content)
                    selected
                }
            assertEquals(DraftBackupResult.Saved, result)
            assertEquals(listOf("choose", "read"), order)
            assertEquals(selected.name, QuickSchemeTransferCodec.decode(assertNotNull(destination.content)).name)
            assertEquals(1, destination.closed)
            assertEquals(
                DraftBackupResult.DownloadRequested,
                exportQuickScheme(
                    DraftBackupRuntime(usesBrowserDownload = true) {
                        Destination()
                    },
                    selected,
                    { true },
                ) { selected },
            )
        }

    @Test fun cancellationStaleDefinitionAndScopeChangeNeverWriteAndAlwaysDispose() =
        runTest {
            val selected = scheme()
            var reads = 0
            assertEquals(
                DraftBackupResult.Cancelled,
                exportQuickScheme(DraftBackupRuntime { null }, selected, { true }) {
                    reads++
                    selected
                },
            )
            assertEquals(0, reads)
            for (mode in 0..3) {
                var current = true
                val destination = Destination()
                val runtime =
                    DraftBackupRuntime {
                        if (mode == 0) current = false
                        destination.beforeWrite = { if (mode == 3) current = false }
                        destination
                    }
                assertFails {
                    exportQuickScheme(runtime, selected, { current }) {
                        if (mode == 1) throw CancellationException()
                        if (mode == 2) selected.copy(name = "changed") else selected
                    }
                }
                assertNull(destination.content)
                assertEquals(1, destination.closed)
            }
        }
}
