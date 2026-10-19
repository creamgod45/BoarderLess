package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopStorageActivationTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val before = Workspace(WorkspaceId("workspace"), "Fixture")
    private val head =
        CreateObjectsOperation(
            "head",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)),
                    text = "Draft",
                ),
            ),
        )
    private val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, before)

    private fun settings() =
        InMemorySettings().apply {
            putString("backend.clientId", "client")
            putLong("backend.clientSequence", 40)
        }

    private fun capture(prefs: SessionPreferences) = prefs.captureLegacySequenceForMigration(scope, listOf(scope), emptyList(), emptyList())

    private inline fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-activation-test")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun activatedActualRepositoryUsesNativeDraftSafetyAndSequenceWithoutLegacyMirrorAndReopensAdvancedState() =
        temporary { root ->
            runTest {
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val source = capture(legacy)
                val files = DesktopAtomicDraftStore(root)
                val activation = DesktopStorageActivation(files) {}
                val receipt = activation.activate(scope) { capture(legacy) }
                val preferences = activation.openPreferences(memory, scope) { capture(legacy) }
                assertEquals(receipt, activation.read())
                assertEquals("client", preferences.clientId)
                assertFails { preferences.clientSequence }
                assertFails { preferences.clientSequence = 0 }
                assertFails { preferences.reserveClientSequences(1) }
                var posts = 0
                val client =
                    HttpClient(
                        MockEngine { http ->
                            posts++
                            val request =
                                Json.decodeFromString<SubmitOperationsRequest>(
                                    (http.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                                )
                            assertEquals(41L, request.operations.single().clientSeq)
                            assertEquals(
                                request,
                                DesktopDraftScopeBundleStore(files)
                                    .read(scope)!!
                                    .bundle!!
                                    .pending!!
                                    .request,
                            )
                            assertEquals(41L, DesktopClientSequenceLedger(files).read(scope)!!.highWater)
                            val ack =
                                AcceptedOperationsDto(
                                    "accepted",
                                    9,
                                    20,
                                    20,
                                    request.operations.map {
                                        CommittedWorkspaceOperationDto(
                                            20,
                                            it.operationId,
                                            request.transactionId,
                                            "actor",
                                            "client",
                                            it.clientSeq,
                                            8,
                                            9,
                                            it.kind,
                                            it.payload,
                                            1,
                                            "2026-10-06T00:00:00Z",
                                        )
                                    },
                                )
                            respond(Json.encodeToString(ack), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                    ) { install(ContentNegotiation) { json() } }
                val repo = BackendWorkspaceRepository("https://qa.invalid", preferences, client)
                try {
                    repo.retainDraft(session, before, head)
                    assertEquals(SubmitOutcome.Accepted(9, 20), repo.submit(session, head))
                    assertNull(repo.pendingChange(session))
                } finally {
                    repo.close()
                }
                assertEquals(1, posts)
                assertEquals(source, capture(legacy))
                assertEquals(setOf("backend.clientId", "backend.clientSequence"), memory.keys)
                assertEquals(40L, legacy.clientSequence)
                val reopened = DesktopStorageActivation(DesktopAtomicDraftStore(root)) {}.openPreferences(memory, scope) { capture(legacy) }
                assertNull(reopened.draftPersistence.pending(scope))
                assertNotNull(reopened.draftPersistence.journal(scope)!!.lastAcknowledgedTransactionId)
                assertEquals(42L..42L, reopened.sequenceAllocator!!.reserve(scope, 1))
                assertEquals(40L, legacy.clientSequence)
                // Native safety facade, not an ordinary Settings fence writer.
                val ackWire =
                    DesktopDraftScopeBundleStore(files)
                        .read(scope)!!
                        .bundle!!
                        .acknowledged!!
                        .submitted
                reopened.fenceAttempts.begin(ackWire)
                assertEquals(PendingFenceAttemptStore.State.Unconfirmed, DesktopRecoverySafetyStore(files).fenceState(ackWire))
                assertEquals(setOf("backend.clientId", "backend.clientSequence"), memory.keys)
                assertFails { activation.activate(scope) { capture(legacy) } }
            }
        }

    @Test fun originallyEmptyScopeCannotSilentlyResetWhenItsLaterDraftFileDisappears() =
        temporary { root ->
            val memory = settings()
            val legacy = SessionPreferences(memory)
            val files = DesktopAtomicDraftStore(root)
            val activation = DesktopStorageActivation(files) {}
            activation.activate(scope) { capture(legacy) }
            val prefs = activation.openPreferences(memory, scope) { capture(legacy) }
            prefs.draftPersistence.append(scope, session, before, head)
            assertNotNull(prefs.draftPersistence.journal(scope))
            Files.delete(root.resolve("${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}.record"))
            assertFails { activation.openPreferences(memory, scope) { capture(legacy) } }
            assertFails { prefs.draftPersistence.journal(scope) }
        }

    @Test fun eachActivationPublicationFailureRequiresFreshOpenOrReverifiedActivationNeverFallback() {
        for (stage in AtomicDraftStage.entries) {
            for (failAt in 1..3) {
                temporary { root ->
                    val memory = settings()
                    val legacy = SessionPreferences(memory)
                    val normal = DesktopAtomicDraftStore(root)
                    DesktopLegacyMigrationCoordinator(normal) {}.publish(scope) { capture(legacy) }
                    var writes = 0
                    val failing = DesktopAtomicDraftStore(root) { if (it == stage && ++writes == failAt) error("activation fault") }
                    assertFails { DesktopStorageActivation(failing) {}.activate(scope) { capture(legacy) } }
                    val reopened = DesktopStorageActivation(DesktopAtomicDraftStore(root)) {}
                    if (reopened.read()?.phase != DesktopActivationPhase.Active) {
                        assertFails { reopened.openPreferences(memory, scope) { capture(legacy) } }
                        reopened.activate(scope) { capture(legacy) }
                    }
                    assertEquals(DesktopActivationPhase.Active, reopened.read()!!.phase)
                    assertEquals("client", reopened.openPreferences(memory, scope) { capture(legacy) }.clientId)
                    assertEquals(40L, legacy.clientSequence)
                }
            }
        }
    }

    @org.junit.Test(timeout = 60000L)
    fun killedActivationProcessNeverOpensPreparingAndResumesTheSameEmptySlotAndControlGeneration() =
        temporary { parent ->
            val classpath = checkNotNull(System.getProperty("boarderless.test.classpath"))
            for (stage in AtomicDraftStage.entries) {
                for (stopAt in 1..3) {
                    val root = parent.resolve("${stage.name}-$stopAt")
                    val files = DesktopAtomicDraftStore(root)
                    DesktopLegacyMigrationCoordinator(files) {}.publish(scope) { emptyActivationCapture() }
                    val process =
                        ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-cp",
                            classpath,
                            DesktopActivationProcessFixture::class.java.name,
                            root.toString(),
                            stage.name,
                            stopAt.toString(),
                        ).redirectErrorStream(true).start()
                    try {
                        val reader = process.inputStream.bufferedReader()
                        assertEquals("CHECKPOINT", CompletableFuture.supplyAsync { reader.readLine() }.get(8, TimeUnit.SECONDS))
                        assertTrue(process.isAlive)
                        process.destroyForcibly()
                        assertTrue(process.waitFor(8, TimeUnit.SECONDS))
                        val memory = settings()
                        val activation = DesktopStorageActivation(DesktopAtomicDraftStore(root)) {}
                        if (activation.read()?.phase != DesktopActivationPhase.Active) {
                            assertFails { activation.openPreferences(memory, scope) { emptyActivationCapture() } }
                            activation.activate(scope) { emptyActivationCapture() }
                        }
                        val prefs = activation.openPreferences(memory, scope) { emptyActivationCapture() }
                        assertEquals(2L, files.read(activation.key)!!.generation)
                        val slot = DesktopDraftScopeBundleStore(files).read(scope)!!
                        assertEquals(1L, slot.generation)
                        assertNull(slot.bundle)
                        assertEquals(41L..41L, prefs.sequenceAllocator!!.reserve(scope, 1))
                        assertEquals(40L, memory.getLongOrNull("backend.clientSequence"))
                    } finally {
                        process.destroyForcibly()
                        process.waitFor(8, TimeUnit.SECONDS)
                    }
                }
            }
        }

    @Test fun multipleMigratedWorkspacesShareNativeLedgerWhileForeignClientArchivesRemainClosed() =
        temporary { root ->
            val memory = settings()
            val legacy = SessionPreferences(memory)
            val other = scope.copy(workspaceId = "other")
            val foreign = scope.copy(clientId = "archived-client", userId = "foreign")
            legacy.draftJournals.append(
                other,
                session.copy(workspace = before.copy(id = WorkspaceId("other"))),
                before.copy(id = WorkspaceId("other")),
                head,
            )
            legacy.pendingSubmissions.save(
                cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission(
                    foreign,
                    "old-head",
                    SubmitOperationsRequest(
                        clientId = foreign.clientId,
                        transactionId = "archived-wire",
                        baseVersion = 8,
                        operations = head.toExpandedDtos(799),
                    ),
                ),
            )

            fun source() = legacy.captureLegacySequenceForMigration(scope, listOf(scope, other, foreign), emptyList(), emptyList())
            val captured = source()
            val files = DesktopAtomicDraftStore(root)
            val activation = DesktopStorageActivation(files) {}
            activation.activate(scope, ::source)
            val prefs = activation.openPreferences(memory, scope, ::source)
            assertEquals(
                "head",
                prefs.draftPersistence
                    .journal(other)!!
                    .operations
                    .single()
                    .operationId,
            )
            assertEquals(41L..41L, prefs.sequenceAllocator!!.reserve(other, 1))
            assertEquals(42L..42L, prefs.sequenceAllocator!!.reserve(scope, 1))
            assertEquals(800L, DesktopClientSequenceLedger(files).read(foreign)!!.highWater)
            assertFails { prefs.draftPersistence.pending(foreign) }
            assertFails { prefs.sequenceAllocator!!.reserve(foreign, 1) }
            assertEquals(captured, source())
            val reopened = activation.openPreferences(memory, scope, ::source)
            assertEquals(43L..43L, reopened.sequenceAllocator!!.reserve(other, 1))
        }

    @Test fun preparingActivationRefusesDriftAdvancedNativeRecordsOrLostExclusionWithoutCompletingTheSwitch() {
        for (mode in listOf("advanced", "missing", "source", "exclusion")) {
            temporary { root ->
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val files = DesktopAtomicDraftStore(root)
                DesktopLegacyMigrationCoordinator(files) {}.publish(scope) { capture(legacy) }
                val failing =
                    DesktopAtomicDraftStore(root) {
                        if (it ==
                            AtomicDraftStage.Published
                        ) {
                            error("Preparing marker publication unknown")
                        }
                    }
                assertFails { DesktopStorageActivation(failing) {}.activate(scope) { capture(legacy) } }
                var excluded = true
                val activation = DesktopStorageActivation(files) { check(excluded) }
                assertEquals(DesktopActivationPhase.Preparing, activation.read()!!.phase)
                when (mode) {
                    "advanced" -> {
                        val key = DesktopClientSequenceLedger(files).key(scope)
                        files.compareAndSet(key, 1, files.read(key)!!.payload)
                    }

                    "missing" -> {
                        Files.delete(root.resolve("${DesktopRecoverySafetyStore(files).key(scope)}.record"))
                    }

                    "source" -> {
                        legacy.clientSequence = 50
                    }

                    else -> {
                        excluded = false
                    }
                }
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { activation.activate(scope) { capture(legacy) } }
                assertFails { activation.openPreferences(memory, scope) { capture(legacy) } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
                assertEquals(1L, files.read(activation.key)!!.generation)
            }
        }
    }

    @Test fun missingCorruptTombstonedOrChangedControlRecordsAndLedgerFloorStopReopenWithoutAnyWrites() {
        for (mode in listOf(
            "activation-missing",
            "activation-corrupt",
            "activation-tombstone",
            "migration-missing",
            "ledger-missing",
            "ledger-rewind",
            "safety-tombstone",
            "source-drift",
        )) {
            temporary { root ->
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val files = DesktopAtomicDraftStore(root)
                val activation = DesktopStorageActivation(files) {}
                activation.activate(scope) { capture(legacy) }
                val migrationKey = DesktopLegacyMigrationCoordinator(files) {}.receiptKey(scope)
                val sequence = DesktopClientSequenceLedger(files)
                when (mode) {
                    "activation-missing" -> Files.delete(root.resolve("${activation.key}.record"))
                    "activation-corrupt" -> files.compareAndSet(activation.key, 2, "{bad".encodeToByteArray())
                    "activation-tombstone" -> files.compareAndSet(activation.key, 2, null)
                    "migration-missing" -> Files.delete(root.resolve("$migrationKey.record"))
                    "ledger-missing" -> Files.delete(root.resolve("${sequence.key(scope)}.record"))
                    "ledger-rewind" -> files.compareAndSet(sequence.key(scope), 1, sequence.encode(30))
                    "safety-tombstone" -> files.compareAndSet(DesktopRecoverySafetyStore(files).key(scope), 1, null)
                    else -> legacy.clientSequence = 50
                }
                val generations = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { activation.openPreferences(memory, scope) { capture(legacy) } }
                assertEquals(generations, files.recordKeys().associateWith { files.read(it)!!.generation })
                assertEquals("client", memory.getStringOrNull("backend.clientId"))
            }
        }
    }

    @Test fun runtimeIdentityExclusionAndUnenrolledScopeGuardsPreventMutationsAndNeverMintAClient() =
        temporary { root ->
            val memory = settings()
            val legacy = SessionPreferences(memory)
            val files = DesktopAtomicDraftStore(root)
            var excluded = true
            val activation = DesktopStorageActivation(files) { check(excluded) }
            activation.activate(scope) { capture(legacy) }
            val prefs = activation.openPreferences(memory, scope) { capture(legacy) }
            val generations = files.recordKeys().associateWith { files.read(it)!!.generation }
            assertFails { prefs.draftPersistence.append(scope.copy(workspaceId = "new"), session, before, head) }
            assertFails { prefs.sequenceAllocator!!.reserve(scope.copy(userId = "other"), 1) }
            excluded = false
            assertFails { prefs.sequenceAllocator!!.reserve(scope, 1) }
            assertFails { prefs.clientId }
            excluded = true
            memory.remove("backend.clientId")
            assertFails { prefs.clientId }
            assertFails { prefs.draftPersistence.append(scope, session, before, head) }
            assertNull(memory.getStringOrNull("backend.clientId"))
            assertEquals(generations, files.recordKeys().associateWith { files.read(it)!!.generation })
        }

    @Test fun nativeDraftAbsenceCanAdvanceToARemovalTombstoneButMigratedDraftCannotDisappear() =
        temporary { root ->
            val memory = settings()
            val legacy = SessionPreferences(memory)
            legacy.draftJournals.append(scope, session, before, head)
            val files = DesktopAtomicDraftStore(root)
            val activation = DesktopStorageActivation(files) {}
            activation.activate(scope) { capture(legacy) }
            val prefs = activation.openPreferences(memory, scope) { capture(legacy) }
            val id = prefs.draftPersistence.journal(scope)!!.id
            assertTrue(prefs.draftPersistence.remove(scope, id))
            val reopened = activation.openPreferences(memory, scope) { capture(legacy) }
            assertNull(reopened.draftPersistence.journal(scope))
            Files.delete(root.resolve("${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}.record"))
            assertFails { activation.openPreferences(memory, scope) { capture(legacy) } }
        }
}

private fun emptyActivationCapture() =
    LegacySequenceCapture(
        40,
        40,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

object DesktopActivationProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
        val selected = AtomicDraftStage.valueOf(args[1])
        var writes = 0
        val files =
            DesktopAtomicDraftStore(Path.of(args[0])) { stage ->
                if (stage == selected && ++writes == args[2].toInt()) {
                    println("CHECKPOINT")
                    System.out.flush()
                    System.`in`.read()
                }
            }
        DesktopStorageActivation(files) {}.activate(scope) { emptyActivationCapture() }
        println("COMPLETED")
        System.out.flush()
    }
}
