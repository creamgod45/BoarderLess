package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.data.persistence.DurableLocalRecord
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.ActivatedServerInstallation
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.WorkspaceStorageRuntime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AwaitedWorkspaceEnrollmentTest {
    private class Records : AwaitedDurableLocalRecordStore {
        val values = mutableMapOf<String, DurableLocalRecord>()
        var writes = 0
        var afterWrite: suspend (String, DurableLocalRecord) -> Unit = { _, _ -> }
        override suspend fun read(key: String) = values[key]?.let { it.copy(payload = it.payload.copyOf()) }
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(values[key]?.generation == generation)
            val next = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
            values[key] = next
            writes++
            afterWrite(key, next)
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(values[key]?.generation == generation) }
    }

    private class Fixture {
        val target = PendingSubmissionScope("https://example.test/api/v1", "10000000-0000-4000-8000-000000000001",
            "20000000-0000-4000-8000-000000000001", "30000000-0000-4000-8000-000000000001")
        val added = target.copy(workspaceId = "30000000-0000-4000-8000-000000000002")
        val controls = Records()
        val draftFiles = Records()
        val safetyFiles = Records()
        val ledgerFiles = Records()
        var excluded = true
        var originalIdentity = true
        fun guard() { check(excluded && originalIdentity) }
        val drafts = AwaitedAtomicWorkspaceDraftPersistence(draftFiles) { guard() }
        val safety = AwaitedAtomicRecoverySafetyPersistence(safetyFiles, { guard() })
        val sequences = AwaitedAtomicClientSequenceAllocator(ledgerFiles) { guard() }
        val plan = LegacySubmissionMigrationPlan("a".repeat(64), listOf(PlannedLegacyScope(target, null,
            RecoverySafetyBundleCodec.prepareLegacy(target, emptyList(), emptyList()), 47, false)))
        val review = LegacySubmissionMigrationReview(plan)
        var captured = plan
        val publisher = AwaitedLegacyMigrationPublisher(Records(), drafts, safety, sequences,
            LegacyMigrationReviewStore(Records()) { guard() }, ::guard)
        fun manager() = AwaitedSubmissionActivationManager(controls, publisher, drafts, sequences, safety,
            ::guard, { check(originalIdentity && it == target) })
        val manager = manager()
        suspend fun activate(): ActivatedAwaitedSubmission {
            publisher.publish(target, review) { captured }
            return manager.confirm(target, review) { captured }
        }
        suspend fun prepare() { activate().closeAndAwaitCalls() }
        suspend fun enroll(scope: PendingSubmissionScope = added) = manager.registerWorkspace(target, scope, review) { captured }
        suspend fun reopen(manager: AwaitedSubmissionActivationManager = this.manager) = manager.reopen(target, review) { captured }
        fun control() = controls.values.values.single()
        fun preferences(): SessionPreferences = SessionPreferences(InMemorySettings().also {
            it.putString("backend.userId", target.userId)
            it.putString("backend.clientId", target.clientId)
            it.putString("backend.workspaceId", target.workspaceId)
        })
    }

    @Test fun enrolledWorkspaceSurvivesManagerRecreationWithoutResettingTheSharedLedger() = runTest {
        val f = Fixture(); f.prepare()
        val before = f.sequences.readDurable(f.target)
        f.enroll()
        assertEquals(before, f.sequences.readDurable(f.added))
        assertEquals(AwaitedDraftBundle(1, null), f.drafts.readDurable(f.added))
        val reopened = f.reopen(f.manager())
        reopened.runtime.requireActive(f.added)
        assertEquals(48L..49L, reopened.runtime.sequences.reserve(f.added, 2))
        assertEquals(50L..50L, reopened.runtime.sequences.reserve(f.target, 1))
        reopened.closeAndAwaitCalls()
        f.reopen(f.manager()).also { it.runtime.requireActive(f.added); it.closeAndAwaitCalls() }
    }

    @Test fun liveLeaseRejectsEnrollmentAndTheUnregisteredScope() = runTest {
        val f = Fixture(); val active = f.activate()
        val writes = f.controls.writes
        assertFails { active.runtime.requireActive(f.added) }
        assertFails { f.enroll() }
        assertEquals(writes, f.controls.writes)
        assertNull(f.drafts.read(f.added))
        active.closeAndAwaitCalls()
        f.enroll()
    }

    @Test fun lostPreparingAcknowledgementRequiresOriginalEnrollmentBeforeReopening() = runTest {
        val f = Fixture(); f.prepare()
        f.controls.afterWrite = { _, value -> if (value.generation == 2L) error("Response lost") }
        assertFails { f.enroll() }
        assertNull(f.drafts.read(f.added))
        assertFails { f.reopen(f.manager()) }
        assertFails { f.enroll(f.added.copy(workspaceId = "30000000-0000-4000-8000-000000000003")) }
        f.controls.afterWrite = { _, _ -> }
        f.manager().registerWorkspace(f.target, f.added, f.review) { f.captured }
        assertEquals(3L, f.control().generation)
        f.reopen(f.manager()).closeAndAwaitCalls()
    }

    @Test fun cancellationAfterDraftPublicationPreservesPreparingAndCanResumeOnRestart() = runTest {
        val f = Fixture(); f.prepare()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.draftFiles.afterWrite = { _, value ->
            if (value.payload.decodeToString().contains("\"version\":2")) {
                entered.complete(Unit); release.await()
            }
        }
        val job = async { f.enroll() }
        runCurrent(); entered.await()
        job.cancelAndJoin()
        assertFails { f.reopen(f.manager()) }
        f.draftFiles.afterWrite = { _, _ -> }
        f.manager().registerWorkspace(f.target, f.added, f.review) { f.captured }
        assertEquals(AwaitedDraftBundle(1, null), f.drafts.readDurable(f.added))
        f.reopen(f.manager()).closeAndAwaitCalls()
    }

    @Test fun lostReadyAcknowledgementIsConfirmedWithoutAnotherWriteOrInitialization() = runTest {
        val f = Fixture(); f.prepare()
        f.controls.afterWrite = { _, value -> if (value.generation == 3L) error("Response lost") }
        assertFails { f.enroll() }
        val counts = listOf(f.controls.writes, f.draftFiles.writes, f.safetyFiles.writes, f.ledgerFiles.writes)
        f.controls.afterWrite = { _, _ -> }
        f.enroll()
        assertEquals(counts, listOf(f.controls.writes, f.draftFiles.writes, f.safetyFiles.writes, f.ledgerFiles.writes))
        f.reopen(f.manager()).closeAndAwaitCalls()
    }

    @Test fun orphanArtifactsAreNeverAdoptedEvenWhenTheyAreEmpty() = runTest {
        for (orphanDraft in listOf(true, false)) {
            val f = Fixture(); f.prepare()
            if (orphanDraft) f.drafts.adoptReviewedBundle(f.added, null)
            else f.safety.adoptLegacy(f.added, emptyList(), emptyList())
            val writes = f.controls.writes
            assertFails { f.enroll() }
            assertEquals(writes, f.controls.writes)
        }
    }

    @Test fun missingRegisteredArtifactsAndLedgerFailReopenWithoutRepair() = runTest {
        for (missing in 0..2) {
            val f = Fixture(); f.prepare(); f.enroll()
            val files = listOf(f.draftFiles, f.safetyFiles, f.ledgerFiles)[missing]
            files.values.clear()
            val writes = files.writes
            assertFails { f.reopen(f.manager()) }
            assertEquals(writes, files.writes)
            assertTrue(files.values.isEmpty())
        }
    }

    @Test fun sourceIdentityAndExclusionChangesRejectEnrollmentBeforeAnyNewArtifact() = runTest {
        for (change in 0..3) {
            val f = Fixture(); f.prepare()
            val writes = f.controls.writes
            when (change) {
                0 -> f.captured = f.plan.copy(sourceDigest = "b".repeat(64))
                1 -> f.originalIdentity = false
                2 -> f.excluded = false
            }
            assertFails { f.enroll(if (change == 3) f.added.copy(clientId = "20000000-0000-4000-8000-000000000002") else f.added) }
            assertEquals(writes, f.controls.writes)
        }
    }

    @Test fun legacyV1ReceiptReopensWithoutRewriteAndUpgradesOnlyOnExplicitEnrollment() = runTest {
        val f = Fixture(); f.prepare()
        val key = f.controls.values.keys.single()
        val value = f.control()
        val raw = Json.parseToJsonElement(value.payload.decodeToString()).jsonObject
        val v1 = JsonObject(raw.filterKeys { it != "enrollments" } + ("version" to JsonPrimitive(1)))
        f.controls.values[key] = value.copy(payload = v1.toString().encodeToByteArray())
        val writes = f.controls.writes
        f.reopen(f.manager()).closeAndAwaitCalls()
        assertEquals(writes, f.controls.writes)
        assertEquals(1, Json.parseToJsonElement(f.control().payload.decodeToString()).jsonObject["version"]!!.jsonPrimitive.int)
        f.enroll()
        assertEquals(3L, f.control().generation)
        assertEquals(2, Json.parseToJsonElement(f.control().payload.decodeToString()).jsonObject["version"]!!.jsonPrimitive.int)
    }

    @Test fun malformedV2CatalogCannotMasqueradeAsAnEmptyV1Catalog() = runTest {
        for (mutation in 0..3) {
            val f = Fixture(); f.prepare(); f.enroll()
            val key = f.controls.values.keys.single()
            val original = f.control()
            val raw = Json.parseToJsonElement(original.payload.decodeToString()).jsonObject
            val broken = when (mutation) {
                0 -> JsonObject(raw - "enrollments")
                1 -> JsonObject(raw + ("version" to JsonPrimitive("2")))
                2 -> JsonObject(raw + ("enrollments" to JsonArray(emptyList())))
                else -> JsonObject(raw + ("enrollments" to JsonArray(raw["enrollments"]!!.jsonArray.toList() + raw["enrollments"]!!.jsonArray)))
            }
            f.controls.values[key] = original.copy(payload = broken.toString().encodeToByteArray())
            val writes = f.controls.writes
            assertFails { f.reopen(f.manager()) }
            assertEquals(writes, f.controls.writes)
        }
    }

    @Test fun activatedFactoryUsesTheOriginalEndpointAndNeverCreatesMissingIdentity() = runTest {
        val f = Fixture(); val activation = f.activate()
        assertEquals("https://example.test", activation.runtime.serverBaseUrl())
        val original = f.preferences()
        assertSame(original, activation.runtime.originalPreferences(original))
        val missing = InMemorySettings().also {
            it.putString("backend.userId", f.target.userId)
            it.putString("backend.workspaceId", f.target.workspaceId)
        }
        assertFails { activation.runtime.originalPreferences(SessionPreferences(missing)) }
        assertNull(missing.getStringOrNull("backend.clientId"))
        assertNull(missing.getLongOrNull("backend.clientSequence"))
        original.workspaceId = f.added.workspaceId
        assertFails { activation.runtime.originalPreferences(original) }
        activation.closeAndAwaitCalls()
        assertFails { activation.runtime.serverBaseUrl() }
        f.enroll()
        val reopened = f.reopen(f.manager())
        assertSame(original, reopened.runtime.originalPreferences(original))
        assertEquals(f.added.workspaceId, original.workspaceId)
        reopened.closeAndAwaitCalls()
    }

    @Test fun failedMediaPreparationRetainsTheExactCandidateThroughFailedCloseAndRetry() = runTest {
        val f = Fixture(); val activation = f.activate()
        var failClose = true
        var created = 0
        var closes = 0
        val events = mutableListOf<String>()
        val storage = WorkspaceStorageRuntime(localRepository = { error("Not used") },
            serverRepositoryWithSubmission = { runtime ->
                created++
                BackendWorkspaceRepository(baseUrl = runtime.serverBaseUrl(), preferences = f.preferences(),
                    client = HttpClient(MockEngine { error("No HTTP expected") }), awaitedSubmissionRuntime = runtime,
                    releaseServerWriter = { closes++; if (failClose) error("Release failed") })
            }, mediaRuntimeFor = { error("Media construction failed") })
        // Retain before preparation; no failed companion factory can discard this handle.
        val installation = ActivatedServerInstallation(activation, f.target,
            awaitPhysicalDrain = { events += "drain" }, releaseExclusion = { events += "release" })
        assertFails { installation.prepare(storage) }
        assertEquals(1, created)
        assertEquals(0, closes)
        assertFails { installation.adopt() }
        assertFails { installation.closeAndDrain() }
        assertTrue(events.isEmpty())
        assertFalse(installation.released)
        failClose = false
        installation.closeAndDrain()
        installation.closeAndDrain()
        assertEquals(1, created)
        assertEquals(2, closes)
        assertEquals(listOf("drain", "release"), events)
        assertTrue(installation.released)
        assertFails { installation.prepare(storage) }
    }

    @Test fun cancellationDuringPhysicalDrainDoesNotReleaseExclusionOrPermitAdoption() = runTest {
        val f = Fixture(); val activation = f.activate()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var releases = 0
        var closes = 0
        val storage = WorkspaceStorageRuntime(localRepository = { error("Not used") },
            serverRepositoryWithSubmission = { runtime ->
                BackendWorkspaceRepository(baseUrl = runtime.serverBaseUrl(), preferences = f.preferences(),
                    client = HttpClient(MockEngine { error("No HTTP expected") }), awaitedSubmissionRuntime = runtime,
                    releaseServerWriter = { closes++ })
            }, mediaRuntimeFor = { MediaImportRuntime.Unavailable })
        val installation = ActivatedServerInstallation(activation, f.target,
            awaitPhysicalDrain = { entered.complete(Unit); finish.await() }, releaseExclusion = { releases++ })
        installation.prepare(storage)
        installation.adopt()
        assertFails { installation.adopt() }
        val closing = async { installation.closeAndDrain() }
        runCurrent(); entered.await(); closing.cancelAndJoin()
        assertFalse(installation.released)
        assertEquals(0, releases)
        assertFails { installation.adopt() }
        finish.complete(Unit)
        installation.closeAndDrain()
        assertEquals(1, closes)
        assertEquals(1, releases)
    }
}
