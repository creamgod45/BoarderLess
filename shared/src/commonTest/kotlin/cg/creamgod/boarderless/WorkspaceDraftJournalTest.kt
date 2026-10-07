package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class WorkspaceDraftJournalTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val baseline = Workspace(WorkspaceId("workspace"), "Fixture")
    private val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, baseline)

    private fun create(text: String = "Draft") =
        CreateObjectsOperation(
            "create",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                    text = text,
                ),
            ),
        )

    private fun apply(
        operation: WorkspaceOperation,
        before: Workspace,
    ) = (operation.applyTo(before) as OperationResult.Applied).workspace

    private fun edit(before: String = "Draft") = EditTextOperation("edit", listOf(TextChange(CanvasObjectId("node"), 1, before, "Edited")))

    @Test fun failedChunkOrManifestPublicationNeverReclaimsPriorReadableJournal() {
        for (prefix in listOf("wdc1.", "wdj1.")) {
            val memory = InMemorySettings()
            var dropWrites = false
            val settings =
                object : Settings by memory {
                    override fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (!(dropWrites && key.startsWith(prefix))) memory.putString(key, value)
                    }
                }
            val store = WorkspaceDraftJournalStore(settings)
            store.append(scope, session, baseline, create())
            val saved = assertNotNull(store.load(scope))
            val originalChunks = memory.keys.filter { it.startsWith("wdc1.") }.associateWith(memory::getStringOrNull)
            dropWrites = true
            assertFails { store.quarantine(scope) }
            assertEquals(saved, store.load(scope))
            assertEquals(originalChunks, originalChunks.keys.associateWith(memory::getStringOrNull))
            dropWrites = false
            store.quarantine(scope)
            assertEquals(saved.copy(quarantined = true), store.load(scope))
        }
    }

    @Test fun excessiveSerializedTransactionDepthIsRejectedBeforePublishingChunks() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        var operation: WorkspaceOperation = create()
        repeat(40) { operation = TransactionOperation("nested-$it", listOf(operation)) }
        val encoded = Json.encodeToString(WorkspaceOperation.serializer(), operation)
        assertFailsWith<IllegalArgumentException> { requireBoundedDraftJsonDepth(encoded) }
        assertFailsWith<IllegalArgumentException> { store.append(scope, session, baseline, operation) }
        assertTrue(memory.keys.isEmpty())
        store.append(scope, session, baseline, create())
        assertEquals(listOf(create()), store.load(scope)?.operations)
    }

    @Test fun invalidInitialScopeAndSequencesNeverPublishAnUnreadableJournal() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        val maximum = 9_007_199_254_740_991L
        for (invalid in listOf(
            session.copy(workspaceVersion = -1),
            session.copy(lastServerSeq = -1),
            session.copy(workspaceVersion = maximum + 1),
            session.copy(lastServerSeq = maximum + 1),
        )) {
            assertFailsWith<IllegalStateException> { store.append(scope, invalid, baseline, create()) }
            assertTrue(memory.keys.isEmpty())
        }
        assertFailsWith<IllegalStateException> { store.append(scope.copy(apiBase = " "), session, baseline, create()) }
        assertTrue(memory.keys.isEmpty())
        store.append(scope, session, baseline, create())
        assertEquals(listOf(create()), store.load(scope)?.operations)
    }

    @Test fun invalidSubmissionAndAckLeaveExactHeadAndTailRecoverable() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        store.append(scope, session, baseline, create())
        store.append(scope, session, apply(create(), baseline), edit())
        val original = store.load(scope)
        assertFailsWith<IllegalStateException> { store.markSubmitted(scope, "create", " ") }
        assertEquals(original, store.load(scope))
        store.markSubmitted(scope, "create", "tx")
        val submitted = store.load(scope)
        val originalKeys = memory.keys.toSet()
        val maximum = 9_007_199_254_740_991L
        listOf(maximum + 1 to 20L, 9L to maximum + 1).forEach { (version, sequence) ->
            assertFailsWith<IllegalStateException> { store.acknowledge(scope, "create", "tx", version, sequence) }
            assertEquals(submitted, store.load(scope))
            assertEquals(originalKeys, memory.keys)
        }
        store.acknowledge(scope, "create", "tx", 9, 20)
        assertEquals(listOf(edit()), store.load(scope)?.operations)
    }

    @Test fun malformedManifestAndExcessChunkBytesStopWithoutDeletingEvidence() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        store.append(scope, session, baseline, create("🙂".repeat(2000)))
        val original = store.load(scope)
        val key = memory.keys.single { it.startsWith("wdj1.") }
        val raw = memory.getStringOrNull(key)!!
        memory.putString(key, raw + " ".repeat(512))
        assertFailsWith<BackendContractException> { store.load(scope) }
        assertEquals(raw + " ".repeat(512), memory.getStringOrNull(key))
        val manifest = Json.parseToJsonElement(raw).jsonObject
        memory.putString(key, JsonObject(manifest + ("bytes" to JsonPrimitive(1))).toString())
        var chunkReads = 0
        val counted =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (key.startsWith("wdc1.")) chunkReads++
                    return memory.getStringOrNull(key)
                }
            }
        assertFailsWith<BackendContractException> { WorkspaceDraftJournalStore(counted).load(scope) }
        assertEquals(1, chunkReads)
        memory.putString(key, raw)
        assertEquals(original, store.load(scope))
    }

    @Test fun restartPreservesDependentOperationsOriginalIdsAndUnicode() {
        val memory = InMemorySettings()
        val limited =
            object : Settings by memory {
                override fun putString(
                    key: String,
                    value: String,
                ) {
                    assertTrue(key.length <= 80)
                    assertTrue(value.length <= 8192)
                    assertFalse(value.lastOrNull()?.let { it in '\uD800'..'\uDBFF' } == true)
                    memory.putString(key, value)
                }
            }
        val first = create("🙂素材".repeat(3000))
        val after = apply(first, baseline)
        val second = edit(first.objects.single().let { (it as TextNode).text })
        val store = WorkspaceDraftJournalStore(limited)
        store.append(scope, session, baseline, first)
        store.append(scope, session, after, second)
        val restored = assertNotNull(WorkspaceDraftJournalStore(limited).load(scope))
        assertEquals(listOf(first, second), restored.operations)
        assertEquals(apply(second, after), restored.replay())
        assertNull(restored.headTransactionId)
    }

    @Test fun acknowledgementAdvancesOnlyHeadAndIsIdempotentAcrossWireCleanupBoundary() {
        val store = WorkspaceDraftJournalStore(InMemorySettings())
        val first = create()
        store.append(scope, session, baseline, first)
        store.append(scope, session, apply(first, baseline), edit())
        store.markSubmitted(scope, "create", "tx")
        assertFailsWith<IllegalStateException> { store.acknowledge(scope, "create", "wrong", 9, 20) }
        store.acknowledge(scope, "create", "tx", 9, 20)
        store.markSubmitted(scope, "create", "tx") // Old wire record survived a process stop.
        store.acknowledge(scope, "create", "tx", 9, 20)
        val journal = assertNotNull(store.load(scope))
        assertEquals(listOf(edit()), journal.operations)
        assertEquals(apply(first, baseline), journal.baseWorkspace)
        assertEquals(9L, journal.baseVersion)
        assertEquals(20L, journal.baseServerSeq)
        assertNull(journal.headTransactionId)
        val authoritative = session.copy(workspaceVersion = 9, lastServerSeq = 20, workspace = journal.baseWorkspace.copy(version = 9))
        val restored = store.restore(scope, journal.id, authoritative)
        assertEquals(authoritative.workspace, restored.baseWorkspace)
        assertEquals(
            "Edited",
            (
                restored
                    .replay()
                    .objects.values
                    .single() as TextNode
            ).text,
        )
    }

    @Test fun changedScopeBaselineAndAbandonedWireNeverAutomaticallyRebase() {
        val store = WorkspaceDraftJournalStore(InMemorySettings())
        store.append(scope, session, baseline, create())
        val journal = assertNotNull(store.load(scope))
        for (changed in listOf(
            session.copy(userId = "other"),
            session.copy(clientId = "other"),
            session.copy(workspaceVersion = 9),
            session.copy(lastServerSeq = 20),
            session.copy(workspace = apply(create(), baseline)),
        )) {
            assertFailsWith<IllegalStateException> { store.restore(scope, journal.id, changed) }
        }
        for (other in listOf(
            scope.copy(apiBase = "https://other.invalid"),
            scope.copy(userId = "other"),
            scope.copy(clientId = "other"),
            scope.copy(workspaceId = "other"),
        )) {
            assertNull(store.load(other))
        }
        store.markSubmitted(scope, "create", "tx")
        assertFailsWith<IllegalStateException> { store.restore(scope, journal.id, session) }
        store.quarantine(scope)
        assertFailsWith<IllegalStateException> { store.restore(scope, journal.id, session) }
        assertFalse(store.remove(scope, "wrong"))
        assertEquals(listOf(create()), store.load(scope)?.operations)
    }

    @Test fun interruptedAppendLeavesPriorManifestAndCorruptionFailsClosed() {
        val memory = InMemorySettings()
        val store = WorkspaceDraftJournalStore(memory)
        store.append(scope, session, baseline, create())
        val saved = store.load(scope)
        val interrupted =
            object : Settings by memory {
                override fun putString(
                    key: String,
                    value: String,
                ) {
                    if (key.startsWith("wdj1.")) error("Storage unavailable before publication")
                    memory.putString(key, value)
                }
            }
        assertFailsWith<IllegalStateException> {
            WorkspaceDraftJournalStore(interrupted).append(scope, session, apply(create(), baseline), edit())
        }
        assertEquals(saved, store.load(scope))
        val chunk = memory.keys.first { it.startsWith("wdc1.") }
        memory.putString(chunk, "corrupt")
        assertFailsWith<BackendContractException> { store.load(scope) }
        assertFailsWith<BackendContractException> { store.append(scope, session, baseline, create()) }
        assertTrue(memory.keys.any { it.startsWith("wdj1.") })
    }

    @Test fun materialReferencesGroupsRelationsAndTransactionsRoundTrip() {
        val group = GroupFrame(CanvasObjectId("group"), transform = CanvasTransform(Vec2.Zero, CanvasSize(800f, 400f)))
        val text = (create().objects.single() as TextNode).copy(parentId = group.id, shape = NodeShape.Diamond)
        val media =
            MediaNode(
                CanvasObjectId("gif"),
                parentId = group.id,
                transform = text.transform,
                assetId = "asset",
                mediaKind = MediaKind.Gif,
                thumbnailAssetId = "thumbnail",
            )
        val relation = Relation(RelationId("relation"), sourceObjectId = text.id, targetObjectId = media.id, intent = "Explains")
        val transaction =
            TransactionOperation(
                "transaction",
                listOf(
                    CreateObjectsOperation("objects", listOf(group, text, media)),
                    CreateRelationsOperation("relations", listOf(relation)),
                ),
            )
        val store = WorkspaceDraftJournalStore(InMemorySettings())
        store.append(scope, session, baseline, transaction)
        val journal = assertNotNull(store.load(scope))
        assertEquals(listOf(transaction), journal.operations)
        assertEquals(apply(transaction, baseline), journal.replay())
        store.append(scope, session, journal.replay(), transaction.inverse())
        assertTrue(assertNotNull(store.load(scope)).replay().objects.isEmpty())
    }

    @Test fun attributeTransformParentAndInverseSnapshotsKeepTheirExactTypesAndFields() {
        val id = CanvasObjectId("node")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f))
        val text = TextNodeAttributes(1, false, "paper", NodeShape.Diamond)
        val group = GroupFrameAttributes(1, false, "group", "Title")
        val media = MediaNodeAttributes(1, false, "Description")
        val relation = RelationAttributes(RelationDirection.Forward, "Intent", null, "line")
        val operations =
            listOf<WorkspaceOperation>(
                TransformObjectsOperation("transform", listOf(TransformChange(id, 1, transform, transform.copy(rotationDegrees = 45f)))),
                ReparentObjectsOperation("parent", listOf(ParentChange(id, 1, null, CanvasObjectId("group")))),
                UpdateTextNodeAttributesOperation(
                    "text-attributes",
                    listOf(TextNodeAttributesChange(id, 1, text, text.copy(locked = true))),
                ),
                UpdateGroupFrameAttributesOperation(
                    "group-attributes",
                    listOf(GroupFrameAttributesChange(id, 1, group, group.copy(title = "Updated"))),
                ),
                UpdateMediaNodeAttributesOperation(
                    "media-attributes",
                    listOf(MediaNodeAttributesChange(id, 1, media, media.copy(altText = "Updated"))),
                ),
                UpdateRelationAttributesOperation(
                    "relation-attributes",
                    listOf(RelationAttributesChange(RelationId("relation"), 1, relation, relation.copy(intent = null, label = "Updated"))),
                ),
            )
        operations.flatMap { listOf(it, it.inverse()) }.forEach { operation ->
            val content = Json.encodeToString(WorkspaceOperation.serializer(), operation)
            assertEquals(operation, Json.decodeFromString(WorkspaceOperation.serializer(), content))
        }
    }
}
