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

class DesktopWorkspaceEnrollmentTest {
    private val target = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val added = target.copy(workspaceId = "new-workspace")
    private val before = Workspace(WorkspaceId(added.workspaceId), "Fixture")
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

    private fun source(prefs: SessionPreferences) =
        prefs.captureLegacySequenceForMigration(target, listOf(target), emptyList(), emptyList())

    private inline fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-workspace-enrollment")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun existingLivePreferencesAndActualRepositoryUseReadyWorkspaceWithSharedLedgerAndZeroLegacyWrites() =
        temporary { root ->
            runTest {
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val captured = source(legacy)
                val files = DesktopAtomicDraftStore(root)
                val activation = DesktopStorageActivation(files) {}
                activation.activate(target) { source(legacy) }
                val prefs = activation.openPreferences(memory, target) { source(legacy) }
                assertFails { prefs.draftPersistence.journal(added) }
                activation.enrollWorkspace(memory, target, added) { source(legacy) }
                assertEquals(
                    DesktopWorkspaceEnrollmentPhase.Ready,
                    activation
                        .read()!!
                        .enrollments
                        .single()
                        .phase,
                )
                assertEquals(4L, files.read(activation.key)!!.generation)
                assertNull(prefs.draftPersistence.journal(added)) // The already-created facade learns only Ready registrations.
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
                                    .read(added)!!
                                    .bundle!!
                                    .pending!!
                                    .request,
                            )
                            assertEquals(41L, DesktopClientSequenceLedger(files).read(target)!!.highWater)
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
                val repo = BackendWorkspaceRepository("https://qa.invalid", prefs, client)
                try {
                    repo.retainDraft(session, before, head)
                    assertEquals(SubmitOutcome.Accepted(9, 20), repo.submit(session, head))
                    assertNull(repo.pendingChange(session))
                } finally {
                    repo.close()
                }
                assertEquals(1, posts)
                assertEquals(captured, source(legacy))
                assertEquals(setOf("backend.clientId", "backend.clientSequence"), memory.keys)
                val snapshot = files.recordKeys().associateWith { files.read(it)!!.generation }
                activation.enrollWorkspace(memory, target, added) { source(legacy) } // Ready is a read, never an initial-state rewind.
                assertEquals(snapshot, files.recordKeys().associateWith { files.read(it)!!.generation })
                val reopened = activation.openPreferences(memory, target) { source(legacy) }
                assertEquals(42L..42L, reopened.sequenceAllocator!!.reserve(added, 1))
                assertEquals(43L..43L, reopened.sequenceAllocator!!.reserve(target, 1))
                assertEquals(40L, legacy.clientSequence)
                activation.enrollWorkspace(memory, target, added.copy(workspaceId = "second")) { source(legacy) }
                assertEquals(44L..44L, prefs.sequenceAllocator!!.reserve(added.copy(workspaceId = "second"), 1))
            }
        }

    @Test fun everyRegistrationPublicationFaultKeepsPreparingClosedAndResumesExactSlotsWithoutRewindingLedger() {
        for (stage in AtomicDraftStage.entries) {
            for (failAt in 1..4) {
                temporary { root ->
                    val memory = settings()
                    val legacy = SessionPreferences(memory)
                    val files = DesktopAtomicDraftStore(root)
                    val normal = DesktopStorageActivation(files) {}
                    normal.activate(target) { source(legacy) }
                    val prefs = normal.openPreferences(memory, target) { source(legacy) }
                    prefs.sequenceAllocator!!.reserve(target, 5)
                    var writes = 0
                    val failing =
                        DesktopStorageActivation(
                            DesktopAtomicDraftStore(root) {
                                if (it == stage && ++writes == failAt) error("registration fault")
                            },
                        ) {}
                    assertFails { failing.enrollWorkspace(memory, target, added) { source(legacy) } }
                    if (normal.read()!!.enrollments.any { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing }) {
                        assertFails { normal.openPreferences(memory, target) { source(legacy) } }
                        assertFails { prefs.draftPersistence.journal(added) }
                        assertFails { prefs.sequenceAllocator!!.reserve(target, 1) }
                    }
                    normal.enrollWorkspace(memory, target, added) { source(legacy) }
                    assertEquals(
                        DesktopWorkspaceEnrollmentPhase.Ready,
                        normal
                            .read()!!
                            .enrollments
                            .single()
                            .phase,
                    )
                    assertEquals(4L, files.read(normal.key)!!.generation)
                    assertEquals(1L, DesktopDraftScopeBundleStore(files).read(added)!!.generation)
                    assertNull(DesktopDraftScopeBundleStore(files).read(added)!!.bundle)
                    assertEquals(1L, DesktopRecoverySafetyStore(files).read(added)!!.generation)
                    assertEquals(45L, DesktopClientSequenceLedger(files).read(target)!!.highWater)
                    assertEquals(46L..46L, prefs.sequenceAllocator!!.reserve(added, 1))
                    assertEquals(40L, legacy.clientSequence)
                }
            }
        }
    }

    @Test fun preparingCannotAdoptAdvancedOrConflictingSlotsAndReadyMissingFilesNeverBecomeEmptyAgain() {
        for (mode in listOf(
            "preparing-advanced",
            "preparing-safety",
            "ready-draft-missing",
            "ready-safety-missing",
            "ready-slot-corrupt",
        )) {
            temporary { root ->
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val files = DesktopAtomicDraftStore(root)
                val normal = DesktopStorageActivation(files) {}
                normal.activate(target) { source(legacy) }
                val draftKey = WorkspaceDraftScopeBundleCodec.scopeHash(added)
                val safetyKey = DesktopRecoverySafetyStore(files).key(added)
                if (mode.startsWith("preparing")) {
                    val failing =
                        DesktopStorageActivation(
                            DesktopAtomicDraftStore(root) {
                                if (it == AtomicDraftStage.Published) error("Preparing intent")
                            },
                        ) {}
                    assertFails { failing.enrollWorkspace(memory, target, added) { source(legacy) } }
                    if (mode == "preparing-advanced") {
                        files.compareAndSet(draftKey, null, null)
                        files.compareAndSet(draftKey, 1, null)
                    } else {
                        files.compareAndSet(
                            safetyKey,
                            null,
                            RecoverySafetyBundleCodec.encode(
                                RecoverySafetyBundle(
                                    scope = added,
                                    fences =
                                        listOf(
                                            PendingFenceAttemptRecord(
                                                scope = added,
                                                transactionId = "other",
                                                wireDigest = "a".repeat(64),
                                                state = PendingFenceAttemptStore.State.Unconfirmed,
                                            ),
                                        ),
                                ),
                            ),
                        )
                    }
                } else {
                    normal.enrollWorkspace(memory, target, added) { source(legacy) }
                    when (mode) {
                        "ready-draft-missing" -> Files.delete(root.resolve("$draftKey.record"))
                        "ready-safety-missing" -> Files.delete(root.resolve("$safetyKey.record"))
                        else -> files.compareAndSet(draftKey, 1, "{bad".encodeToByteArray())
                    }
                }
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { normal.enrollWorkspace(memory, target, added) { source(legacy) } }
                assertFails { normal.openPreferences(memory, target) { source(legacy) } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
            }
        }
    }

    @Test fun namespaceIdentitySourceCatalogAndExclusionFailuresPublishNoRegistration() {
        for (mode in listOf("origin", "user", "client", "identity", "source", "orphan", "exclusion")) {
            temporary { root ->
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val files = DesktopAtomicDraftStore(root)
                var excluded = true
                val activation = DesktopStorageActivation(files) { check(excluded) }
                activation.activate(target) { source(legacy) }
                val scope =
                    when (mode) {
                        "origin" -> added.copy(apiBase = "https://other.invalid/api/v1")
                        "user" -> added.copy(userId = "other")
                        "client" -> added.copy(clientId = "other")
                        else -> added
                    }
                when (mode) {
                    "identity" -> memory.remove("backend.clientId")
                    "source" -> legacy.clientSequence = 100
                    "orphan" -> files.compareAndSet(WorkspaceDraftScopeBundleCodec.scopeHash(added), null, null)
                    "exclusion" -> excluded = false
                }
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { activation.enrollWorkspace(memory, target, scope) { source(legacy) } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
                assertEquals(2L, files.read(activation.key)!!.generation)
            }
        }
    }

    @Test fun registrationControlCannotDowngradeSchemaRemoveReadyEntriesOrForgeAReadyGeneration() {
        for (mode in listOf("schema", "removed", "phase", "identity")) {
            temporary { root ->
                val memory = settings()
                val legacy = SessionPreferences(memory)
                val files = DesktopAtomicDraftStore(root)
                val activation = DesktopStorageActivation(files) {}
                activation.activate(target) { source(legacy) }
                activation.enrollWorkspace(memory, target, added) { source(legacy) }
                val prefs = activation.openPreferences(memory, target) { source(legacy) }
                val previous = activation.read()!!
                val changed =
                    when (mode) {
                        "schema" -> {
                            previous.copy(schema = 1)
                        }

                        "removed" -> {
                            previous.copy(enrollments = emptyList())
                        }

                        "phase" -> {
                            previous.copy(
                                enrollments = previous.enrollments.map { it.copy(phase = DesktopWorkspaceEnrollmentPhase.Preparing) },
                            )
                        }

                        else -> {
                            previous.copy(enrollments = previous.enrollments.map { it.copy(scope = it.scope.copy(userId = "other")) })
                        }
                    }
                files.compareAndSet(activation.key, 4, Json.encodeToString(changed).encodeToByteArray())
                val before = files.recordKeys().associateWith { files.read(it)!!.generation }
                assertFails { prefs.draftPersistence.journal(added) }
                assertFails { activation.enrollWorkspace(memory, target, added) { source(legacy) } }
                assertFails { activation.openPreferences(memory, target) { source(legacy) } }
                assertEquals(before, files.recordKeys().associateWith { files.read(it)!!.generation })
            }
        }
    }

    @org.junit.Test(timeout = 60000L)
    fun independentlyKilledEnrollmentProcessResumesEveryFileAndReadyBoundaryWithoutAllocatingSequences() =
        temporary { parent ->
            val classpath = checkNotNull(System.getProperty("boarderless.test.classpath"))
            for (stage in AtomicDraftStage.entries) {
                for (stopAt in 1..4) {
                    val root = parent.resolve("${stage.name}-$stopAt")
                    val files = DesktopAtomicDraftStore(root)
                    val memory = settings()
                    val legacy = SessionPreferences(memory)
                    val normal = DesktopStorageActivation(files) {}
                    normal.activate(target) { source(legacy) }
                    val process =
                        ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-cp",
                            classpath,
                            DesktopEnrollmentProcessFixture::class.java.name,
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
                        val reopened = DesktopStorageActivation(DesktopAtomicDraftStore(root)) {}
                        if (reopened.read()!!.enrollments.any { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing }) {
                            assertFails { reopened.openPreferences(memory, target) { source(legacy) } }
                        }
                        reopened.enrollWorkspace(memory, target, added) { source(legacy) }
                        val prefs = reopened.openPreferences(memory, target) { source(legacy) }
                        assertNull(prefs.draftPersistence.journal(added))
                        assertEquals(4L, files.read(reopened.key)!!.generation)
                        assertEquals(40L, DesktopClientSequenceLedger(files).read(target)!!.highWater)
                        assertEquals(41L..41L, prefs.sequenceAllocator!!.reserve(added, 1))
                    } finally {
                        process.destroyForcibly()
                        process.waitFor(8, TimeUnit.SECONDS)
                    }
                }
            }
        }
}

object DesktopEnrollmentProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        val target = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
        val memory =
            InMemorySettings().apply {
                putString("backend.clientId", "client")
                putLong("backend.clientSequence", 40)
            }
        val legacy = SessionPreferences(memory)
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
        DesktopStorageActivation(files) {}.enrollWorkspace(memory, target, target.copy(workspaceId = "new-workspace")) {
            legacy.captureLegacySequenceForMigration(target, listOf(target), emptyList(), emptyList())
        }
        println("COMPLETED")
        System.out.flush()
    }
}
