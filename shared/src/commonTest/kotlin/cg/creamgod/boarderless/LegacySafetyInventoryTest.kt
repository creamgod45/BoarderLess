package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacySafetyInventoryTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")

    private fun wire(
        target: PendingSubmissionScope = scope,
        tx: String = "tx",
        seq: Long = 20,
    ) = PendingWorkspaceSubmission(
        target,
        "local",
        SubmitOperationsRequest(
            clientId = target.clientId,
            transactionId = tx,
            baseVersion = 8,
            operations = listOf(OperationDto("wire-$tx", seq, "create_object", payload = buildJsonObject {})),
        ),
    )

    private fun deletion(
        store: CommittedDeletionStore,
        target: PendingSubmissionScope = scope,
    ) {
        val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "Deleted")
        val local = DeleteObjectsOperation("delete", listOf(node), emptyList())
        val operations = local.toExpandedDtos(40)
        val pending =
            PendingWorkspaceSubmission(
                target,
                local.operationId,
                SubmitOperationsRequest(clientId = target.clientId, transactionId = "deleted", baseVersion = 8, operations = operations),
                local.deletionVersions(),
                local.deletionSnapshotDigests(),
            )
        val sent = operations.single()
        val record =
            CommittedWorkspaceOperationDto(
                9000,
                sent.operationId,
                "deleted",
                target.userId,
                target.clientId,
                sent.clientSeq,
                8,
                9,
                sent.kind,
                JsonObject(sent.payload + ("cascadedRelationIds" to JsonArray(emptyList()))),
                1,
                "2026-10-05",
            )
        store.record(pending, AcceptedOperationsDto("accepted", 9, 9000, 9000, listOf(record)))
    }

    @Test fun capturesAllNamespacesAndOrphanFenceStatesWithoutReadingUnrelatedValues() {
        val memory = InMemorySettings()
        val fences = PendingFenceAttemptStore(memory)
        val original = wire()
        val orphan = wire(scope.copy(clientId = "foreign"), "orphan", 800)
        fences.begin(original)
        fences.observe(original, PendingReceiptStatus.Fenced)
        fences.begin(orphan)
        fences.observe(orphan, PendingReceiptStatus.Committed)
        deletion(CommittedDeletionStore(memory))
        deletion(CommittedDeletionStore(memory), scope.copy(workspaceId = "foreign"))
        memory.putString("unrelated.private.setting", "dummy-fixture-not-a-secret")
        val before = memory.keys.associateWith(memory::getStringOrNull)
        var writes = 0
        val guarded =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    check(key != "unrelated.private.setting")
                    return memory.getStringOrNull(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    writes++
                    memory.putString(key, value)
                }
            }
        val found = PendingFenceAttemptStore(guarded).inventoryForMigration()
        assertEquals(setOf(PendingFenceAttemptStore.State.Fenced, PendingFenceAttemptStore.State.Committed), found.map { it.state }.toSet())
        assertEquals(setOf(original.scope, orphan.scope), found.map { it.scope }.toSet())
        assertEquals(2, CommittedDeletionStore(guarded).inventoryForMigration().size)
        assertEquals(0, writes)
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
    }

    @Test fun malformedMetadataDigestVersionsOrKeysCannotBeSilentlySkipped() {
        val memory = InMemorySettings()
        PendingFenceAttemptStore(memory).begin(wire())
        deletion(CommittedDeletionStore(memory))
        for (prefix in listOf("fence1.", "wdel1.")) {
            val key = memory.keys.single { it.startsWith(prefix) }
            val original = memory.getStringOrNull(key)!!
            val parsed = Json.parseToJsonElement(original).jsonObject
            val variants =
                if (prefix == "fence1.") {
                    listOf(
                        parsed + ("schema" to JsonPrimitive(2)),
                        parsed + ("wireDigest" to JsonPrimitive("bad")),
                        parsed + ("transactionId" to JsonPrimitive("different")),
                        parsed + ("state" to JsonPrimitive("unknown")),
                    )
                } else {
                    listOf(
                        parsed + ("entity" to JsonPrimitive("other:node")),
                        parsed + ("snapshotDigest" to JsonPrimitive("bad")),
                        parsed + ("tombstoneVersion" to JsonPrimitive(1)),
                        parsed + ("throughServerSeq" to JsonPrimitive(-1)),
                    )
                }
            for (variant in variants) {
                val raw = JsonObject(variant).toString()
                memory.putString(key, raw)
                assertFailsWith<BackendContractException> {
                    if (prefix == "fence1.") {
                        PendingFenceAttemptStore(memory).inventoryForMigration()
                    } else {
                        CommittedDeletionStore(memory).inventoryForMigration()
                    }
                }
                assertEquals(raw, memory.getStringOrNull(key))
            }
            memory.putString(key, original)
            memory.putString(prefix + "not-a-hash", original)
            assertFailsWith<BackendContractException> {
                if (prefix == "fence1.") {
                    PendingFenceAttemptStore(memory).inventoryForMigration()
                } else {
                    CommittedDeletionStore(memory).inventoryForMigration()
                }
            }
            memory.remove(prefix + "not-a-hash")
        }
    }

    @Test fun captureKeepsSafetyRecordsAndRejectsKnownWireDigestMismatchWithoutChangingCounter() {
        val memory = InMemorySettings()
        val prefs = SessionPreferences(memory)
        val target = scope.copy(clientId = prefs.clientId)
        prefs.clientSequence = 10
        val pending = wire(target)
        prefs.pendingSubmissions.save(pending)
        prefs.fenceAttempts.begin(pending)
        val orphan = wire(target, "orphan", 500)
        prefs.fenceAttempts.begin(orphan) // No wire survives; never reconstruct it from its hash.
        deletion(prefs.deletionEvidence, target)
        val before = memory.keys.associateWith(memory::getStringOrNull)
        val capture = prefs.captureLegacySequenceForMigration(target, listOf(target), emptyList(), emptyList())
        assertEquals(2, capture.fenceAttempts.size)
        assertEquals(listOf("orphan"), capture.unboundFenceAttempts.map { it.transactionId })
        assertEquals(1, capture.deletionEvidence.size)
        assertEquals(20L, capture.floor) // Neither opaque hash nor serverSeq=9000 is a clientSeq.
        assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
        // Normal save correctly refuses replacing an unresolved wire. Inject a syntactically
        // valid but contradictory marker directly into this isolated fixture instead.
        val fenceKey =
            memory.keys.single {
                it.startsWith("fence1.") &&
                    Json
                        .parseToJsonElement(memory.getStringOrNull(it)!!)
                        .jsonObject["transactionId"]
                        ?.jsonPrimitive
                        ?.content == "tx"
            }
        val original = Json.parseToJsonElement(memory.getStringOrNull(fenceKey)!!).jsonObject
        memory.putString(fenceKey, JsonObject(original + ("wireDigest" to JsonPrimitive("0".repeat(64)))).toString())
        val changed = memory.keys.associateWith(memory::getStringOrNull)
        assertFails { prefs.captureLegacySequenceForMigration(target, listOf(target), emptyList(), emptyList()) }
        assertEquals(changed, memory.keys.associateWith(memory::getStringOrNull))
        assertEquals(10L, prefs.clientSequence)
    }

    @Test fun observedSafetyRecordChangeRejectsCaptureWithoutRollingBackOtherWriter() {
        val memory = InMemorySettings()
        val fences = PendingFenceAttemptStore(memory)
        val pending = wire()
        fences.begin(pending)
        var reads = 0
        var writes = 0
        val guarded =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    if (key.startsWith("fence1.") && ++reads == 2) fences.observe(pending, PendingReceiptStatus.Fenced)
                    return memory.getStringOrNull(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    writes++
                    memory.putString(key, value)
                }
            }
        assertFailsWith<BackendContractException> { PendingFenceAttemptStore(guarded).inventoryForMigration() }
        assertEquals(PendingFenceAttemptStore.State.Fenced, fences.state(pending))
        assertEquals(0, writes)
    }

    @Test fun recordAndAggregateBoundsPrecedeRecordDecoding() {
        val memory = InMemorySettings()
        repeat(257) { memory.putString("fence1." + it.toString(16).padStart(64, '0'), "{}") }
        var reads = 0
        val counted =
            object : Settings by memory {
                override fun getStringOrNull(key: String): String? {
                    reads++
                    return memory.getStringOrNull(key)
                }
            }
        assertFailsWith<BackendContractException> { PendingFenceAttemptStore(counted).inventoryForMigration() }
        assertEquals(0, reads)
        val large = InMemorySettings()
        repeat(1025) { large.putString("wdel1." + it.toString(16).padStart(64, '0'), " ".repeat(4095) + "x") }
        var decoded = false
        val failure =
            assertFailsWith<BackendContractException> {
                captureLegacyRecoveryRecords(large, "wdel1.", 4096) { _, _ ->
                    decoded = true
                    "unused"
                }
            }
        assertFalse(decoded)
        assertEquals("Recovery inventory exceeds byte limit", failure.cause?.message)
        assertEquals(1025, large.keys.size)
    }
}
