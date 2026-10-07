package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopLegacyMigrationCoordinatorTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val unsent = scope.copy(workspaceId = "unsent")
    private val foreign = scope.copy(userId = "foreign", workspaceId = "foreign-workspace")
    private val safetyOnly = scope.copy(workspaceId = "safety-only")
    private val node = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "Draft")
    private val operation = CreateObjectsOperation("head", listOf(node))

    private fun wire(
        target: PendingSubmissionScope = scope,
        tx: String = "tx",
        seq: Long = 20,
    ) = PendingWorkspaceSubmission(
        target,
        "head",
        SubmitOperationsRequest(
            clientId = target.clientId,
            transactionId = tx,
            baseVersion = 8,
            operations =
                operation.toExpandedDtos(seq - 1),
        ),
    )

    private fun source(): Pair<SessionPreferences, LegacySequenceCapture> {
        val prefs = SessionPreferences(InMemorySettings().apply { putString("backend.clientId", "client") }).apply { clientSequence = 15 }
        for (target in listOf(scope, unsent)) {
            val before = Workspace(WorkspaceId(target.workspaceId), "Fixture")
            prefs.draftJournals.append(
                target,
                WorkspaceSession(target.userId, target.clientId, WorkspaceMemberRole.Editor, 8, 19, before),
                before,
                operation,
            )
        }
        val pending = wire()
        prefs.draftPersistence.stage(pending)
        prefs.pendingSubmissions.archiveStopped(pending) // Partially stopped exact submission.
        prefs.pendingSubmissions.archiveStopped(wire(tx = "older", seq = 100).copy(localOperationId = "historic"))
        prefs.fenceAttempts.begin(pending)
        prefs.pendingSubmissions.save(wire(foreign, "foreign-tx", 800))
        return prefs to capture(prefs)
    }

    private fun capture(prefs: SessionPreferences) =
        prefs
            .captureLegacySequenceForMigration(
                scope,
                listOf(scope, unsent, foreign),
                emptyList(),
                emptyList(),
            ).copy(
                deletionEvidence =
                    listOf(CommittedDeletionEvidence(safetyOnly, "object:a", 2, "deleted", 9000, "a".repeat(64))),
            )

    private inline fun temporary(block: (Path) -> Unit) {
        val parent = Files.createTempDirectory("boarderless-migration-coordinator")
        try {
            block(parent.resolve("native"))
        } finally {
            Files.walk(parent).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun completePublicationReopensEveryScopeAndNamespacePreservingSourcesAndRetryQuarantine() =
        temporary { root ->
            val (prefs, initial) = source()
            val files = DesktopAtomicDraftStore(root)
            var guards = 0
            val coordinator = DesktopLegacyMigrationCoordinator(files) { guards++ }
            val published = coordinator.publish(scope) { capture(prefs) }
            assertEquals(DesktopMigrationPhase.Published, published.receipt.phase)
            assertEquals(2L, published.generation)
            assertTrue(guards > published.receipt.artifacts.size)
            assertEquals(10, published.receipt.artifacts.size) // Four scopes * draft+safety + two namespace ledgers.
            val reopened = DesktopAtomicDraftStore(root)
            val drafts = DesktopDraftScopeBundleStore(reopened)
            val draft = drafts.read(scope)!!.bundle!!
            assertEquals(initial.pending.single { it.scope == scope }, draft.stoppedPending)
            assertTrue(draft.journal!!.quarantined)
            assertNull(draft.pending)
            assertEquals(
                "older",
                draft.retainedStopped
                    .single()
                    .submitted.request.transactionId,
            )
            assertEquals(initial.journals.single { it.scope == unsent }, drafts.read(unsent)!!.bundle!!.journal)
            assertNull(drafts.read(safetyOnly))
            val safety = DesktopRecoverySafetyStore(reopened)
            assertEquals(initial.fenceAttempts, safety.read(scope)!!.bundle.fences)
            assertEquals(initial.deletionEvidence, safety.read(safetyOnly)!!.bundle.deletions)
            val ledger = DesktopClientSequenceLedger(reopened)
            assertEquals(100L, ledger.read(scope)!!.highWater)
            assertEquals(ledger.read(scope), ledger.read(unsent))
            assertEquals(800L, ledger.read(foreign)!!.highWater)
            assertEquals(initial, capture(prefs)) // Zero legacy writes or default runtime activation.
            val before = reopened.recordKeys().associateWith { reopened.read(it)!!.generation }
            val second = DesktopLegacyMigrationCoordinator(reopened) {}.publish(scope) { capture(prefs) }
            assertEquals(published, second)
            assertEquals(before, reopened.recordKeys().associateWith { reopened.read(it)!!.generation })
        }

    @Test fun everyPublicationCheckpointCanResumeOnlyTheExactIntentWithNoGenerationRewrites() =
        temporary { parent ->
            Files.createDirectory(parent)
            val (_, capture) = source()
            // Receipt intent + nine nonempty files + final receipt = eleven durable publications.
            for (stage in AtomicDraftStage.entries) {
                for (failAt in 1..11) {
                    val root = parent.resolve("${stage.name}-$failAt")
                    var seen = 0
                    val failing =
                        DesktopAtomicDraftStore(root) { point ->
                            if (point == stage && ++seen == failAt) error("publication failure")
                        }
                    val coordinator = DesktopLegacyMigrationCoordinator(failing) {}
                    assertFails { coordinator.publish(scope) { capture } }
                    val reopened = DesktopAtomicDraftStore(root)
                    val before = reopened.recordKeys().associateWith { reopened.read(it)!!.generation }
                    val fresh = DesktopLegacyMigrationCoordinator(reopened) {}
                    val result = fresh.publish(scope) { capture }
                    assertEquals(DesktopMigrationPhase.Published, result.receipt.phase)
                    assertEquals(2L, result.generation)
                    for (artifact in result.receipt.artifacts) {
                        if (artifact.payloadDigest == null) {
                            assertNull(reopened.read(artifact.key))
                        } else {
                            assertEquals(1L, reopened.read(artifact.key)!!.generation)
                        }
                    }
                    before.forEach { (key, generation) ->
                        if (key != fresh.receiptKey(scope)) assertEquals(generation, reopened.read(key)!!.generation)
                    }
                }
            }
        }

    @Test fun advancedMissingTombstonedOrUninventoriedNativeRecordsNeverReinitializeOrRewind() {
        for (mode in listOf("advanced", "missing", "tombstone", "unknown")) {
            temporary { root ->
                val (_, capture) = source()
                val files = DesktopAtomicDraftStore(root)
                val coordinator = DesktopLegacyMigrationCoordinator(files) {}
                val receipt = coordinator.publish(scope) { capture }
                val artifact = receipt.receipt.artifacts.first { it.kind == DesktopMigrationArtifactKind.Sequence }
                when (mode) {
                    "advanced" -> files.compareAndSet(artifact.key, 1, files.read(artifact.key)!!.payload)
                    "missing" -> Files.delete(root.resolve("${artifact.key}.record"))
                    "tombstone" -> files.compareAndSet(artifact.key, 1, null)
                    else -> files.compareAndSet("f".repeat(64), null, byteArrayOf(1))
                }
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { coordinator.publish(scope) { capture } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
                assertEquals(receipt, coordinator.read(scope))
            }
        }
    }

    @Test fun partialIntentRejectsAdvancedTombstonedCorruptAndUninventoriedFilesBeforeFurtherWrites() {
        for (mode in listOf("advanced", "tombstone", "corrupt", "bad-catalog", "symlink")) {
            temporary { root ->
                val (_, capture) = source()
                var publications = 0
                val failing =
                    DesktopAtomicDraftStore(root) { stage ->
                        if (stage == AtomicDraftStage.Published && ++publications == 3) error("fixture stops partial migration")
                    }
                assertFails { DesktopLegacyMigrationCoordinator(failing) {}.publish(scope) { capture } }
                val files = DesktopAtomicDraftStore(root)
                val coordinator = DesktopLegacyMigrationCoordinator(files) {}
                val receipt = coordinator.read(scope)!!
                assertEquals(DesktopMigrationPhase.Preparing, receipt.receipt.phase)
                val present = receipt.receipt.artifacts.first { files.read(it.key) != null }
                val record = root.resolve("${present.key}.record")
                when (mode) {
                    "advanced" -> files.compareAndSet(present.key, 1, files.read(present.key)!!.payload)
                    "tombstone" -> files.compareAndSet(present.key, 1, null)
                    "corrupt" -> Files.write(record, Files.readAllBytes(record).also { it[it.lastIndex] = (it.last() + 1).toByte() })
                    "bad-catalog" -> Files.writeString(root.resolve("bad.record"), "unknown")
                    else -> Files.createSymbolicLink(root.resolve("f".repeat(64) + ".record"), record)
                }
                val before =
                    Files.list(root).use { paths ->
                        paths
                            .filter { it.fileName.toString().endsWith(".record") }
                            .toList()
                            .associate { it.fileName.toString() to Files.readAllBytes(it).toList() }
                    }
                assertFails { coordinator.publish(scope) { capture } }
                val after =
                    Files.list(root).use { paths ->
                        paths
                            .filter { it.fileName.toString().endsWith(".record") }
                            .toList()
                            .associate { it.fileName.toString() to Files.readAllBytes(it).toList() }
                    }
                assertEquals(before, after)
                assertEquals(receipt, coordinator.read(scope))
            }
        }
    }

    @Test fun sourceDriftOrLossOfExclusionStopsBeforePublishedAndChangedSourceCannotResume() =
        temporary { parent ->
            Files.createDirectory(parent)
            val (_, original) = source()
            val driftRoot = parent.resolve("drift")
            val files = DesktopAtomicDraftStore(driftRoot)
            val coordinator = DesktopLegacyMigrationCoordinator(files) {}
            var reads = 0
            assertFails {
                coordinator.publish(scope) {
                    if (++reads >= 4) original.copy(counter = 101, floor = 101) else original
                }
            }
            assertEquals(DesktopMigrationPhase.Preparing, coordinator.read(scope)!!.receipt.phase)
            val before = files.recordKeys().associateWith { files.read(it)!!.generation }
            assertFails { coordinator.publish(scope) { original.copy(counter = 101, floor = 101) } }
            assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
            val excluded = DesktopAtomicDraftStore(parent.resolve("exclusion"))
            var guardCalls = 0
            val gated = DesktopLegacyMigrationCoordinator(excluded) { if (++guardCalls > 3) error("Writer exclusion lost") }
            assertFails { gated.publish(scope) { original } }
            assertTrue(excluded.recordKeys().isEmpty())
        }

    @org.junit.Test(timeout = 60000L)
    fun killedMigrationProcessResumesExactWireAtEveryFileAndReceiptBoundary() =
        temporary { parent ->
            Files.createDirectory(parent)
            val pending = wire()
            val wirePath = parent.resolve("source.json")
            Files.writeString(wirePath, Json.encodeToString(pending))
            val captured = migrationProcessCapture(pending)
            val classpath = checkNotNull(System.getProperty("boarderless.test.classpath"))
            for (stage in AtomicDraftStage.entries) {
                for (stopAt in 1..5) {
                    val root = parent.resolve("${stage.name}-$stopAt")
                    val process =
                        ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-cp",
                            classpath,
                            DesktopMigrationProcessFixture::class.java.name,
                            root.toString(),
                            wirePath.toString(),
                            stage.name,
                            stopAt.toString(),
                        ).redirectErrorStream(true).start()
                    try {
                        val reader = process.inputStream.bufferedReader()
                        val checkpoint = CompletableFuture.supplyAsync { reader.readLine() }.get(8, TimeUnit.SECONDS)
                        assertEquals("CHECKPOINT", checkpoint)
                        assertTrue(process.isAlive)
                        process.destroyForcibly()
                        assertTrue(process.waitFor(8, TimeUnit.SECONDS))
                        val files = DesktopAtomicDraftStore(root)
                        val coordinator = DesktopLegacyMigrationCoordinator(files) {}
                        val complete = coordinator.publish(scope) { captured }
                        assertEquals(DesktopMigrationPhase.Published, complete.receipt.phase)
                        assertEquals(2L, complete.generation)
                        assertEquals(pending, DesktopDraftScopeBundleStore(files).read(scope)!!.bundle!!.pending)
                        assertEquals(20L, DesktopClientSequenceLedger(files).read(scope)!!.highWater)
                        assertEquals(pending, Json.decodeFromString<PendingWorkspaceSubmission>(Files.readString(wirePath)))
                        complete.receipt.artifacts.forEach { assertEquals(1L, files.read(it.key)!!.generation) }
                    } finally {
                        process.destroyForcibly()
                        process.waitFor(8, TimeUnit.SECONDS)
                    }
                }
            }
        }

    @Test fun preexistingNativeStateAndCorruptReceiptCannotBeAdoptedWithoutExactIntent() =
        temporary { parent ->
            Files.createDirectory(parent)
            val (_, capture) = source()
            for (mode in listOf("native", "receipt", "tombstone", "scope")) {
                val files = DesktopAtomicDraftStore(parent.resolve(mode))
                val coordinator = DesktopLegacyMigrationCoordinator(files) {}
                when (mode) {
                    "native" -> {
                        DesktopClientSequenceLedger(files).initialize(scope, 100)
                    }

                    "receipt" -> {
                        files.compareAndSet(coordinator.receiptKey(scope), null, "{bad".encodeToByteArray())
                    }

                    "tombstone" -> {
                        files.compareAndSet(coordinator.receiptKey(scope), null, null)
                    }

                    else -> {
                        val receipt = coordinator.publish(scope) { capture }
                        files.compareAndSet(coordinator.receiptKey(unsent), null, files.read(coordinator.receiptKey(scope))!!.payload)
                        assertEquals(DesktopMigrationPhase.Published, receipt.receipt.phase)
                    }
                }
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { coordinator.publish(if (mode == "scope") unsent else scope) { capture } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
            }
        }
}

private fun migrationProcessCapture(pending: PendingWorkspaceSubmission) =
    LegacySequenceCapture(
        counter = 15,
        floor = 20,
        pending = listOf(pending),
        stopped = emptyList(),
        journals = emptyList(),
        fenceAttempts = emptyList(),
        deletionEvidence = emptyList(),
        unboundFenceAttempts = emptyList(),
    )

/** Independent JVM in an isolated temporary namespace; no APP, Settings or HTTP. */
object DesktopMigrationProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        val pending = Json.decodeFromString<PendingWorkspaceSubmission>(Files.readString(Path.of(args[1])))
        val selected = AtomicDraftStage.valueOf(args[2])
        var publications = 0
        val files =
            DesktopAtomicDraftStore(Path.of(args[0])) { stage ->
                if (stage == selected && ++publications == args[3].toInt()) {
                    println("CHECKPOINT")
                    System.out.flush()
                    System.`in`.read()
                }
            }
        DesktopLegacyMigrationCoordinator(files) {}.publish(pending.scope) { migrationProcessCapture(pending) }
        println("COMPLETED")
        System.out.flush()
    }
}
