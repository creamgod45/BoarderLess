package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class SequenceCanvasPublicationTest {
    private val draft =
        assertIs<SequenceParseResult.Parsed>(
            MermaidSequenceAdapter.parse(
                """
                sequenceDiagram
                participant Client
                participant Transport
                participant Stateful
                Client->>Transport: request
                Stateful-->>Transport: state
                alt existing
                Transport->>Transport: prepare
                Transport-->>Client: response
                else missing
                loop retry
                Transport->>Stateful: create
                Stateful-->>Transport: created
                end
                end
                """.trimIndent(),
            ),
        ).draft
    private val workspace = Workspace(WorkspaceId("w"), "Board")
    private val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, 0, 0, workspace)

    private fun create(): TransactionOperation {
        var id = 0
        return SequenceCanvasCreation(owner, workspace, Vec2(300f, 400f)).operation(draft, "Transport sequence") { "id_${++id}" }
    }

    private fun published() = (create().applyTo(workspace) as OperationResult.Applied).workspace

    @Test fun atomicCreateBindsAllIdentitiesPreservesSemanticsAndActualHistoryUndoRedo() {
        val op = create()
        assertEquals(3, op.operations.size)
        val first = WorkspaceHistory(workspace).execute(op)
        assertTrue(first.succeeded)
        val w = first.history.workspace
        assertEquals(1, w.version)
        assertEquals(1, w.sequenceDiagramsVersion)
        assertTrue(w.hasValidSequenceBindings())
        val document = w.sequenceDiagrams.values.single()
        assertEquals(draft, document.toDraft())
        assertEquals(document.allObjectIds(), w.objects.keys)
        assertEquals(document.allRelationIds(), w.relations.keys)
        assertEquals(6, w.relations.size)
        assertEquals(
            6,
            document.messages
                .map { it.message.id }
                .toSet()
                .size,
        )
        val encoded =
            Json {
                encodeDefaults = true
                allowStructuredMapKeys = true
            }.encodeToString(w)
        assertEquals(w, Json { allowStructuredMapKeys = true }.decodeFromString<Workspace>(encoded))
        val undone = first.history.undo(remoteRestoration = true)
        assertTrue(undone.succeeded)
        assertTrue(
            undone.history.workspace.objects
                .isEmpty(),
        )
        assertTrue(
            undone.history.workspace.relations
                .isEmpty(),
        )
        assertTrue(
            undone.history.workspace.sequenceDiagrams
                .isEmpty(),
        )
        assertEquals(2, undone.history.workspace.sequenceDiagramsVersion)
        val redone = undone.history.redo(remoteRestoration = true)
        assertTrue(redone.succeeded)
        assertEquals(
            document,
            redone.history.workspace.sequenceDiagrams.values
                .single(),
        )
        assertEquals(3, redone.history.workspace.sequenceDiagramsVersion)
        assertTrue(
            redone.history.workspace.objects.values
                .all { it.version == 3L },
        )
        assertTrue(
            redone.history.workspace.relations.values
                .all { it.version == 3L },
        )
        assertTrue(redone.history.workspace.hasValidSequenceBindings())
    }

    @Test fun staleMetadataAndPartialDeletionAndOrdinarySemanticEditsRejectWithoutMutation() {
        val w = published()
        val d = w.sequenceDiagrams.values.single()
        val meta = create().operations.last() as UpdateSequenceDiagramsOperation
        assertIs<OperationResult.Rejected>(meta.applyTo(w))
        assertIs<OperationResult.Rejected>(meta.copy(expectedSequenceDiagramsVersion = 1).applyTo(w))
        assertIs<OperationResult.Rejected>(
            DeleteObjectsOperation("partial", listOf(w.objects.getValue(d.participants.first().objectId))).applyTo(w),
        )
        assertIs<OperationResult.Rejected>(
            DeleteRelationsOperation("partial", listOf(w.relations.getValue(d.messages.first().relationId))).applyTo(w),
        )
        val node = w.objects.getValue(d.participants.first().objectId) as TextNode
        val result =
            WorkspaceHistory(
                w,
            ).execute(EditTextOperation("ordinary", listOf(TextChange(node.id, node.version, node.text, "different"))))
        assertFalse(result.succeeded)
        assertEquals(w, result.history.workspace)
        val bad = w.copy(relations = w.relations - w.relations.keys.first())
        assertFalse(bad.hasValidSequenceBindings())
        assertFalse(w.copy(objects = w.objects + (node.id to node.copy(id = CanvasObjectId("wrong-key")))).hasValidSequenceBindings())
        val root = w.objects.getValue(d.containerId) as GroupFrame
        assertFalse(w.copy(objects = w.objects + (root.id to root.copy(parentId = root.id))).hasValidSequenceBindings())
        assertFalse(w.copy(objects = w.objects + (root.id to root.copy(parentId = CanvasObjectId("missing")))).hasValidSequenceBindings())
        assertIs<OperationResult.Rejected>(
            TransactionOperation(
                "bad",
                listOf(EditTextOperation("rename", listOf(TextChange(node.id, node.version, node.text, "different")))),
            ).applyTo(w),
        )
    }

    @Test fun wholeDiagramDeleteAndUndoRestoreAllBindingsAndExternalRelationsInOneRevision() {
        val w = published()
        val d = w.sequenceDiagrams.values.single()
        val delete = DeleteObjectsOperation("delete", w.objects.values.toList(), w.relations.values.toList())
        val op = deleteSequenceAware(w, delete, "clear-metadata")
        val removed = WorkspaceHistory(w).execute(op)
        assertTrue(removed.succeeded)
        assertEquals(2, removed.history.workspace.version)
        assertTrue(
            removed.history.workspace.sequenceDiagrams
                .isEmpty(),
        )
        val restored = removed.history.undo(remoteRestoration = true)
        assertTrue(restored.succeeded)
        assertEquals(
            d,
            restored.history.workspace.sequenceDiagrams.values
                .single(),
        )
        assertTrue(restored.history.workspace.hasValidSequenceBindings())
        assertTrue(
            restored.history
                .redo(remoteRestoration = true)
                .history.workspace.sequenceDiagrams
                .isEmpty(),
        )
        assertFails {
            deleteSequenceAware(
                w,
                delete.copy(objects = listOf(w.objects.getValue(d.participants.first().objectId))),
                "partial",
            )
        }
    }

    @Test fun renameRequiresAtomicMetadataAndLabelChangeAndReversedInverseRestoresBoth() {
        val w = published()
        val d = w.sequenceDiagrams.values.single()
        val first = d.participants.first()
        val node = w.objects.getValue(first.objectId) as TextNode
        val updated =
            d.copy(
                participants =
                    d.participants.map {
                        if (it ==
                            first
                        ) {
                            it.copy(participant = it.participant.copy(label = "Renamed participant"))
                        } else {
                            it
                        }
                    },
            )
        val metadata =
            UpdateSequenceDiagramsOperation(
                "metadata",
                w.sequenceDiagramsVersion,
                w.sequenceDiagrams,
                w.sequenceDiagrams + (d.containerId to updated),
            )
        assertIs<OperationResult.Rejected>(metadata.applyTo(w))
        assertIs<OperationResult.Rejected>(TransactionOperation("missing-label", listOf(metadata)).applyTo(w))
        val rename =
            TransactionOperation(
                "rename",
                listOf(EditTextOperation("label", listOf(TextChange(node.id, node.version, node.text, "Renamed participant"))), metadata),
            )
        val edited = WorkspaceHistory(w).execute(rename)
        assertTrue(edited.succeeded)
        assertEquals(2, edited.history.workspace.version)
        assertEquals(
            updated,
            edited.history.workspace.sequenceDiagrams.values
                .single(),
        )
        val undo = edited.history.undo(remoteRestoration = true)
        assertTrue(undo.succeeded)
        assertEquals(
            d,
            undo.history.workspace.sequenceDiagrams.values
                .single(),
        )
        assertEquals(
            node.text,
            (
                undo.history.workspace.objects
                    .getValue(node.id) as TextNode
            ).text,
        )
        assertTrue(undo.history.workspace.hasValidSequenceBindings())
        val redo = undo.history.redo(remoteRestoration = true)
        assertTrue(redo.succeeded)
        assertEquals(
            updated,
            redo.history.workspace.sequenceDiagrams.values
                .single(),
        )
        assertTrue(redo.history.workspace.hasValidSequenceBindings())
    }

    @Test fun flatDocumentRejectsOrderGapsUnknownParentsDuplicateBindingsAndCycles() {
        val d = published().sequenceDiagrams.values.single()
        assertFails {
            d.copy(
                messages =
                    d.messages.mapIndexed {
                        i,
                        m,
                        ->
                        if (i == 0) m.copy(position = m.position.copy(order = 20)) else m
                    },
            )
        }
        assertFails {
            d.copy(
                messages =
                    d.messages.mapIndexed { i, m ->
                        if (i ==
                            0
                        ) {
                            m.copy(position = m.position.copy(blockId = "missing"))
                        } else {
                            m
                        }
                    },
            )
        }
        assertFails {
            d.copy(
                participants =
                    d.participants.mapIndexed { i, p ->
                        if (i ==
                            1
                        ) {
                            p.copy(objectId = d.participants[0].objectId)
                        } else {
                            p
                        }
                    },
            )
        }
        assertFails { d.copy(blocks = d.blocks.map { it.copy(position = it.position.copy(blockId = it.id)) }) }
        assertFails { d.copy(schema = "future") }
        assertFails { SequenceCanvasDiagram.bind(draft, d.containerId, emptyMap(), emptyMap(), emptyMap()) }
        val json =
            Json {
                encodeDefaults = true
                allowStructuredMapKeys = true
            }.encodeToJsonElement(published()).jsonObject
        val entries = json.getValue("sequenceDiagrams").jsonArray
        assertFails { requireStrictSequenceCanvasFields(JsonObject(json + ("sequenceDiagrams" to JsonArray(entries + entries)))) }
        val noSchema = JsonArray(listOf(entries[0], JsonObject(entries[1].jsonObject - "schema")))
        assertFails { requireStrictSequenceCanvasFields(JsonObject(json + ("sequenceDiagrams" to noSchema))) }
    }

    @Test fun actorScopeSnapshotPermissionAndBoundsAreCheckedAndOrdinaryReceiptsRemainStable() {
        val c = SequenceCanvasCreation(owner, workspace, Vec2.Zero)
        assertTrue(c.isCurrent(owner, workspace))
        for (s in listOf(
            owner.copy(userId = "other"),
            owner.copy(clientId = "other"),
            owner.copy(role = WorkspaceMemberRole.Viewer),
            owner.copy(workspaceVersion = 1),
            owner.copy(lastServerSeq = 1),
        )) {
            assertFalse(c.isCurrent(s, workspace))
        }
        assertFalse(c.isCurrent(null, workspace))
        assertFalse(c.isCurrent(owner, workspace.copy(title = "Changed")))
        assertFails { SequenceCanvasCreation(owner, workspace.copy(id = WorkspaceId("other")), Vec2.Zero) }
        assertFails { c.operation(draft, "Sequence") { "same" } }
        assertFails {
            SequenceCanvasCreation(
                owner,
                workspace,
                Vec2(1_000_000f, 1_000_000f),
            ).operation(draft, "Sequence") { "n_${idCounter++}" }
        }
        assertFalse(
            "sequenceDiagrams" in
                Json {
                    encodeDefaults = true
                    allowStructuredMapKeys = true
                }.encodeToString(workspace),
        )
        val dto =
            buildJsonObject {
                put("workspaceId", "w")
                put("workspaceVersion", 0)
                put("throughServerSeq", 0)
                put("objects", JsonArray(emptyList()))
            }
        assertEquals("w", decodeLegacyWorkspaceState(dto).workspaceId)
        for (field in listOf("sequenceDiagrams", "sequenceDiagramsVersion")) {
            for (value in listOf(JsonNull, JsonObject(emptyMap()), JsonArray(emptyList()))) {
                assertFails { decodeLegacyWorkspaceState(JsonObject(dto + (field to value))) }
            }
        }
        assertTrue(DraftMergeContractGap.SequenceDiagrams in draftMergeContractGaps(workspace, create()))
        assertFails { create().toExpandedDtos(1) }
    }

    @Test fun backendRejectsBeforeDraftSequenceReservationOrHttpIncludingNestedOperations() =
        runTest {
            val preferences = SessionPreferences(InMemorySettings())
            val session = owner.copy(clientId = preferences.clientId)
            var calls = 0
            val repository =
                BackendWorkspaceRepository(
                    "http://fixture",
                    preferences,
                    HttpClient(
                        MockEngine {
                            calls++
                            error("Unexpected HTTP")
                        },
                    ),
                )
            try {
                assertFalse(repository.supportsSequenceDiagrams)
                for (op in listOf(create(), TransactionOperation("nested", listOf(create())))) {
                    assertFails { repository.retainDraft(session, workspace, op) }
                    assertFails { repository.submit(session, op) }
                    assertEquals(0, calls)
                    assertEquals(0, preferences.clientSequence)
                    assertNull(repository.pendingChange(session))
                }
            } finally {
                repository.close()
            }
        }

    private var idCounter = 0
}
