package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.MediaRecoveryStore
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.mediaNodeFromReadyAsset
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

/** OPT-IN: creates fresh QA identities/workspace/assets on the already-running dev backend.
 * Never reads APP preferences, starts a server/worker, bulk-cleans QA data, or logs signed URLs.
 * Normal APP pre-completion cleanup and revoking this fixture's viewer are in scope.
 * Ordinary test runs SKIP this case; green skipped output is not live acceptance evidence.
 */
class MediaBackendLiveAcceptanceTest {
    /** Read-only server acceptance against a retained QA PNG; never creates a new server scope. */
    @Test fun realDownloadToJvmFileSinkAndConsumerCancellation() =
        runBlocking {
            val base = System.getenv("BOARDERLESS_MEDIA_LIVE_BASE_URL")
            val user = System.getenv("BOARDERLESS_MEDIA_LIVE_DOWNLOAD_USER")
            val workspace = System.getenv("BOARDERLESS_MEDIA_LIVE_DOWNLOAD_WORKSPACE")
            val assetId = System.getenv("BOARDERLESS_MEDIA_LIVE_DOWNLOAD_ASSET")
            assumeTrue(
                "Read-only live download requires explicit QA scope and opt-in",
                base != null &&
                    user != null && workspace != null && assetId != null &&
                    System.getenv("BOARDERLESS_MEDIA_LIVE_ACCEPTANCE") == "true",
            )
            require(base in setOf("http://localhost:3000", "http://127.0.0.1:3000"))
            listOf(user, workspace, assetId).forEach { UUID.fromString(requireNotNull(it)) }
            val root = File(requireNotNull(System.getenv("BOARDERLESS_TEST_REPO_ROOT")), "backend/tests/fixtures/media")
            val row =
                Json
                    .parseToJsonElement(File(root, "manifest.json").readText())
                    .jsonArray
                    .single {
                        it.jsonObject
                            .getValue("file")
                            .jsonPrimitive.content == "image.png"
                    }.jsonObject
            val fixture = Fixture(row, root)
            assertEquals(fixture.checksum, hash(fixture.bytes))
            assertEquals(fixture.byteSize, fixture.bytes.size.toLong())
            val prefs =
                SessionPreferences(InMemorySettings()).apply {
                    userId = user!!
                    workspaceId = workspace!!
                }
            val repo = BackendWorkspaceRepository(baseUrl = base!!, preferences = prefs)
            val gateway = BackendAssetTransferGateway(baseUrl = base)
            var stage = "readonly download setup"
            try {
                withTimeout(60_000) {
                    // refresh is GET-only and fails if this retained scope is gone. Do not use
                    // openOrCreateWorkspace: its 404 fallback can create unrelated QA data.
                    val session =
                        repo.refresh(
                            WorkspaceSession(
                                user!!,
                                UUID.randomUUID().toString(),
                                WorkspaceMemberRole.Viewer,
                                0L,
                                0L,
                                Workspace(WorkspaceId(workspace!!), "QA read-only seed"),
                            ),
                        )
                    assertEquals(workspace, session.workspace.id.value)
                    val before = repo.refresh(session).workspace
                    val asset = repo.getAsset(session, assetId!!)
                    assertEquals(AssetStatus.Ready, asset.status)
                    assertEquals(fixture.mediaType, asset.mediaType)
                    assertEquals(fixture.byteSize, asset.byteSize)
                    assertEquals(fixture.checksum, asset.checksum)
                    for (cancelAfterChunk in listOf(false, true)) {
                        stage = if (cancelAfterChunk) "real download consumer cancellation" else "real verified file publication"
                        val directory = Files.createTempDirectory("boarderless-live-file-sink-")
                        try {
                            val sink = JvmFileAssetDownloadSink.create(directory.toString(), "fixture", asset.mediaType)
                            val stages = mutableListOf<AssetDownloadStage>()
                            if (cancelAfterChunk) {
                                val received = CompletableDeferred<Unit>()
                                var receivedBytes = 0L
                                val gatedGateway =
                                    object : AssetDownloadGateway by gateway {
                                        override suspend fun download(
                                            ticket: AssetDownloadTicket,
                                            onChunk: suspend (ByteArray) -> Unit,
                                        ) {
                                            gateway.download(ticket) { bytes ->
                                                onChunk(bytes)
                                                receivedBytes += bytes.size
                                                received.complete(Unit)
                                                // Cancel the actual response consumer, not a fake transfer.
                                                awaitCancellation()
                                            }
                                        }
                                    }
                                val transfer =
                                    async {
                                        AssetDownloadCoordinator(gatedGateway).download(session, asset.id, sink) { stages += it }
                                    }
                                received.await()
                                assertTrue(receivedBytes > 0)
                                assertFalse(Files.exists(directory.resolve("fixture.png")))
                                transfer.cancel(CancellationException("QA download consumer cancellation"))
                                assertFailsWith<CancellationException> { transfer.await() }
                                transfer.join()
                                assertTrue(stages.none { it is AssetDownloadStage.Ready })
                                assertFalse(Files.list(directory).use { it.findAny().isPresent })
                            } else {
                                val local = AssetDownloadCoordinator(gateway).download(session, asset.id, sink) { stages += it }
                                assertEquals("image/png", local.mediaType)
                                assertContentEquals(fixture.bytes, Files.readAllBytes(Paths.get(local.token)))
                                assertEquals(1, stages.count { it is AssetDownloadStage.Ready })
                                assertEquals(
                                    listOf("fixture.png"),
                                    Files.list(directory).use { paths ->
                                        paths.map { it.fileName.toString() }.toList()
                                    },
                                )
                                sink.abort()
                                assertContentEquals(fixture.bytes, Files.readAllBytes(Paths.get(local.token)))
                            }
                        } finally {
                            // Only this test-owned flat directory and its part/verified file.
                            Files.newDirectoryStream(directory).use { entries -> entries.forEach { Files.deleteIfExists(it) } }
                            Files.deleteIfExists(directory)
                        }
                    }
                    assertEquals(before, repo.refresh(session).workspace)
                    assertEquals(AssetStatus.Ready, repo.getAsset(session, asset.id).status)
                    println(
                        "LIVE_MEDIA_FILE_SINK workspace=$workspace asset=${asset.id} verifiedFile=true cancelAfterChunk=true partRemoved=true noServerMutation=true",
                    )
                }
            } catch (failure: Throwable) {
                throw AssertionError(
                    "Live file sink acceptance failed at $stage (${failure.javaClass.simpleName}); no server mutation requested",
                )
            } finally {
                gateway.close()
                repo.close()
            }
        }

    @Test fun realCancellationBeforeAndAfterCompletion() =
        runBlocking {
            val base = System.getenv("BOARDERLESS_MEDIA_LIVE_BASE_URL")
            assumeTrue(
                "Live acceptance requires explicit opt-in",
                base != null &&
                    System.getenv("BOARDERLESS_MEDIA_LIVE_ACCEPTANCE") == "true",
            )
            require(base in setOf("http://localhost:3000", "http://127.0.0.1:3000"))
            val root = File(requireNotNull(System.getenv("BOARDERLESS_TEST_REPO_ROOT")), "backend/tests/fixtures/media")
            val row =
                Json
                    .parseToJsonElement(File(root, "manifest.json").readText())
                    .jsonArray
                    .single {
                        it.jsonObject
                            .getValue("file")
                            .jsonPrimitive.content == "image.png"
                    }.jsonObject
            val fixture = Fixture(row, root)
            assertEquals(fixture.checksum, hash(fixture.bytes))
            assertEquals(fixture.byteSize, fixture.bytes.size.toLong())
            val setup = HttpClient { followRedirects = false }
            val prefs = SessionPreferences(InMemorySettings())
            val repo = BackendWorkspaceRepository(baseUrl = base!!, preferences = prefs)
            val gateway = BackendAssetTransferGateway(baseUrl = base, maximumProcessingPolls = 60)
            var stage = "cancel setup"
            try {
                withTimeout(90_000) {
                    val runId = UUID.randomUUID().toString()
                    val user =
                        setup
                            .post("$base/api/v1/users") {
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("displayName", "QA media cancel $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    val workspace =
                        setup
                            .post("$base/api/v1/workspaces") {
                                header("x-user-id", user)
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("title", "QA media cancel $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    println("LIVE_MEDIA_CANCEL_SCOPE workspace=$workspace owner=$user")
                    prefs.userId = user
                    prefs.workspaceId = workspace
                    val session = repo.openOrCreateWorkspace()
                    val reminders = MediaRecoveryStore(InMemorySettings())
                    for (afterComplete in listOf(false, true)) {
                        stage = if (afterComplete) "cancel accepted completion" else "cancel uploaded before completion"
                        val reached = CompletableDeferred<Unit>()
                        var ticket: AssetUploadTicket? = null
                        var prepareCalls = 0
                        var uploadCalls = 0
                        var completeCalls = 0
                        var abandonCalls = 0
                        val stages = mutableListOf<AssetImportStage>()
                        val gatedGateway =
                            object : AssetTransferGateway by gateway {
                                override suspend fun prepare(
                                    session: WorkspaceSession,
                                    source: AssetTransferSource,
                                ): AssetUploadTicket {
                                    prepareCalls++
                                    return gateway.prepare(session, source).also { ticket = it }
                                }

                                override suspend fun upload(
                                    ticket: AssetUploadTicket,
                                    source: AssetTransferSource,
                                    onProgress: (Long) -> Unit,
                                ) {
                                    uploadCalls++
                                    gateway.upload(ticket, source, onProgress)
                                    if (!afterComplete) {
                                        reached.complete(Unit)
                                        awaitCancellation()
                                    }
                                }

                                override suspend fun confirm(
                                    session: WorkspaceSession,
                                    ticket: AssetUploadTicket,
                                ): WorkspaceAsset {
                                    completeCalls++
                                    val result = gateway.confirm(session, ticket)
                                    if (afterComplete) {
                                        reached.complete(Unit)
                                        awaitCancellation()
                                    }
                                    return result
                                }

                                override suspend fun abandon(
                                    session: WorkspaceSession,
                                    assetId: String,
                                ) {
                                    abandonCalls++
                                    gateway.abandon(session, assetId)
                                }
                            }
                        val import =
                            async {
                                AssetImportCoordinator(gatedGateway, beforeComplete = { reminders.record(user, workspace, it) })
                                    .import(session, fixture) { stages += it }
                            }
                        reached.await()
                        import.cancel(CancellationException("QA boundary cancellation"))
                        assertFailsWith<CancellationException> { import.await() }
                        // await does not itself guarantee finally/cleanup has finished on every engine.
                        import.join()
                        val original = requireNotNull(ticket)
                        assertEquals(1, prepareCalls)
                        assertEquals(1, uploadCalls)
                        assertTrue(stages.none { it is AssetImportStage.Ready })
                        assertTrue(
                            repo
                                .refresh(session)
                                .workspace.objects
                                .isEmpty(),
                        )
                        if (afterComplete) {
                            assertEquals(1, completeCalls)
                            assertEquals(0, abandonCalls)
                            assertTrue(stages.any { it == AssetImportStage.RecoveryRequired(original.asset.id) })
                            assertEquals(listOf(original.asset.id), reminders.list(user, workspace))
                            val ready = gateway.awaitReady(session, original.asset.id) {}
                            assertEquals(original.asset.id, ready.id)
                            verifyDownload(gateway, session, ready.id, fixture.bytes)
                            assertEquals(
                                HttpStatusCode.Conflict,
                                assertFailsWith<BackendHttpException> {
                                    gateway.abandon(session, ready.id)
                                }.status,
                            )
                            assertEquals(AssetStatus.Ready, repo.getAsset(session, ready.id).status)
                        } else {
                            assertEquals(0, completeCalls)
                            assertEquals(1, abandonCalls)
                            assertTrue(stages.none { it is AssetImportStage.RecoveryRequired })
                            assertTrue(reminders.list(user, workspace).isEmpty())
                            assertEquals(
                                HttpStatusCode.NotFound,
                                assertFailsWith<BackendHttpException> {
                                    repo.getAsset(session, original.asset.id)
                                }.status,
                            )
                            // Idempotent abandon, and completion cannot resurrect the upload.
                            gateway.abandon(session, original.asset.id)
                            assertEquals(
                                HttpStatusCode.Conflict,
                                assertFailsWith<BackendHttpException> {
                                    gateway.confirm(session, original)
                                }.status,
                            )
                        }
                        println(
                            "LIVE_MEDIA_CANCELLED asset=${original.asset.id} afterComplete=$afterComplete abandonCalls=$abandonCalls noReady=true noNode=true",
                        )
                    }
                }
            } catch (failure: Throwable) {
                throw AssertionError(
                    "Live media cancellation acceptance failed at $stage (${failure.javaClass.simpleName}); QA data retained",
                )
            } finally {
                gateway.close()
                repo.close()
                setup.close()
            }
        }

    @Test fun realChecksumRejectionAndLostCompleteResponseRecovery() =
        runBlocking {
            val base = System.getenv("BOARDERLESS_MEDIA_LIVE_BASE_URL")
            assumeTrue(
                "Live acceptance requires explicit opt-in",
                base != null &&
                    System.getenv("BOARDERLESS_MEDIA_LIVE_ACCEPTANCE") == "true",
            )
            require(base in setOf("http://localhost:3000", "http://127.0.0.1:3000"))
            val fixtureRoot = File(requireNotNull(System.getenv("BOARDERLESS_TEST_REPO_ROOT")), "backend/tests/fixtures/media")
            val row =
                Json
                    .parseToJsonElement(File(fixtureRoot, "manifest.json").readText())
                    .jsonArray
                    .single {
                        it.jsonObject
                            .getValue("file")
                            .jsonPrimitive.content == "image.png"
                    }.jsonObject
            val fixture = Fixture(row, fixtureRoot)
            assertEquals(fixture.checksum, hash(fixture.bytes))
            assertEquals(fixture.byteSize, fixture.bytes.size.toLong())
            val setup = HttpClient { followRedirects = false }
            val prefs = SessionPreferences(InMemorySettings())
            val repo = BackendWorkspaceRepository(baseUrl = base!!, preferences = prefs)
            val gateway = BackendAssetTransferGateway(baseUrl = base, maximumProcessingPolls = 60)
            var stage = "failure setup"
            try {
                withTimeout(90_000) {
                    val runId = UUID.randomUUID().toString()
                    val user =
                        setup
                            .post("$base/api/v1/users") {
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("displayName", "QA media failures $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    val workspace =
                        setup
                            .post("$base/api/v1/workspaces") {
                                header("x-user-id", user)
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("title", "QA media failures $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    println("LIVE_MEDIA_FAILURE_SCOPE workspace=$workspace owner=$user")
                    prefs.userId = user
                    prefs.workspaceId = workspace
                    var session = repo.openOrCreateWorkspace()
                    val reminderSettings = InMemorySettings()
                    val reminders = MediaRecoveryStore(reminderSettings)
                    val wrongChecksum =
                        object : AssetTransferSource by fixture {
                            override val checksum = "sha256:" + "0".repeat(64)
                        }
                    var rejectedId: String? = null
                    var abandonCalls = 0
                    val observedGateway =
                        object : AssetTransferGateway by gateway {
                            override suspend fun abandon(
                                session: WorkspaceSession,
                                assetId: String,
                            ) {
                                abandonCalls++
                                gateway.abandon(session, assetId)
                            }
                        }
                    val rejectionStages = mutableListOf<AssetImportStage>()
                    stage = "worker checksum rejection"
                    val failure =
                        assertFailsWith<AssetImportException> {
                            AssetImportCoordinator(observedGateway, beforeComplete = { id ->
                                rejectedId = id
                                reminders.record(user, workspace, id)
                            }).import(session, wrongChecksum) { rejectionStages += it }
                        }
                    assertEquals(AssetImportIssue.Rejected, failure.issue)
                    val rejected = repo.getAsset(session, requireNotNull(rejectedId))
                    assertEquals(AssetStatus.Rejected, rejected.status)
                    assertEquals(AssetRejectionReason.ChecksumMismatch, rejected.rejectionReason)
                    assertTrue(rejectionStages.any { it == AssetImportStage.RecoveryRequired(rejected.id) })
                    assertTrue(rejectionStages.none { it is AssetImportStage.Ready })
                    assertEquals(0, abandonCalls)
                    assertEquals(
                        HttpStatusCode.Conflict,
                        assertFailsWith<BackendHttpException> {
                            gateway.authorize(session, rejected.id)
                        }.status,
                    )
                    assertTrue(
                        repo
                            .refresh(session)
                            .workspace.objects
                            .isEmpty(),
                    )
                    println("LIVE_MEDIA_REJECTED asset=${rejected.id} reason=checksum_mismatch noNode=true noDelete=true")

                    stage = "lost complete response"
                    var prepareCalls = 0
                    var uploadCalls = 0
                    var completeCalls = 0
                    var originalTicket: AssetUploadTicket? = null
                    val lostResponse = IOException("Fixture dropped completion response after server acceptance")
                    val droppingGateway =
                        object : AssetTransferGateway by observedGateway {
                            override suspend fun prepare(
                                session: WorkspaceSession,
                                source: AssetTransferSource,
                            ): AssetUploadTicket {
                                prepareCalls++
                                return gateway.prepare(session, source).also { originalTicket = it }
                            }

                            override suspend fun upload(
                                ticket: AssetUploadTicket,
                                source: AssetTransferSource,
                                onProgress: (Long) -> Unit,
                            ) {
                                uploadCalls++
                                gateway.upload(ticket, source, onProgress)
                            }

                            override suspend fun confirm(
                                session: WorkspaceSession,
                                ticket: AssetUploadTicket,
                            ): WorkspaceAsset {
                                completeCalls++
                                gateway.confirm(session, ticket)
                                // Fault injection at the APP boundary, not real TCP packet loss.
                                throw lostResponse
                            }
                        }
                    val lostStages = mutableListOf<AssetImportStage>()
                    assertSame(
                        lostResponse,
                        assertFailsWith<IOException> {
                            AssetImportCoordinator(droppingGateway, beforeComplete = { reminders.record(user, workspace, it) })
                                .import(session, fixture) { lostStages += it }
                        },
                    )
                    val ticket = requireNotNull(originalTicket)
                    assertEquals(1, prepareCalls)
                    assertEquals(1, uploadCalls)
                    assertEquals(1, completeCalls)
                    assertEquals(0, abandonCalls)
                    assertTrue(lostStages.any { it == AssetImportStage.RecoveryRequired(ticket.asset.id) })
                    assertTrue(lostStages.none { it is AssetImportStage.Ready })
                    assertEquals(listOf(rejected.id, ticket.asset.id), MediaRecoveryStore(reminderSettings).list(user, workspace))
                    assertTrue(
                        repo
                            .refresh(session)
                            .workspace.objects
                            .isEmpty(),
                    )
                    stage = "reconcile same asset"
                    val ready = gateway.awaitReady(session, ticket.asset.id) {}
                    assertEquals(AssetStatus.Ready, ready.status)
                    assertEquals(ticket.asset.id, ready.id)
                    verifyDownload(gateway, session, ready.id, fixture.bytes)
                    assertEquals(ready.id, gateway.confirm(session, ticket).id)
                    assertEquals(ready.id, gateway.confirm(session, ticket).id)
                    assertEquals(setOf(rejected.id, ready.id), repo.listAssets(session).map { it.id }.toSet())
                    // Reconciliation itself never creates a Node or clears the reminder.
                    assertTrue(
                        repo
                            .refresh(session)
                            .workspace.objects
                            .isEmpty(),
                    )
                    assertTrue(ready.id in reminders.list(user, workspace))
                    stage = "explicit insertion after reconciliation"
                    val node =
                        mediaNodeFromReadyAsset(
                            ready,
                            session.workspace.id,
                            CanvasObjectId(UUID.randomUUID().toString()),
                            Vec2(0f, 0f),
                            1f,
                            0L,
                            fixture.displayName,
                        )
                    assertIs<SubmitOutcome.Accepted>(
                        repo.submit(
                            session,
                            CreateObjectsOperation(UUID.randomUUID().toString(), listOf(node)),
                        ),
                    )
                    session = repo.refresh(session)
                    assertEquals(1, session.workspace.objects.size)
                    assertEquals(ready.id, (session.workspace.objectById(node.id) as MediaNode).assetId)
                    reminders.dismiss(user, workspace, ready.id)
                    assertEquals(listOf(rejected.id), MediaRecoveryStore(reminderSettings).list(user, workspace))
                    assertEquals(AssetStatus.Ready, repo.getAsset(session, ready.id).status)
                    println(
                        "LIVE_MEDIA_RECOVERED asset=${ready.id} node=${node.id.value} sameAsset=true noReupload=true noAutoNode=true duplicateComplete=true reminderExplicit=true",
                    )
                }
            } catch (failure: Throwable) {
                throw AssertionError("Live media failure acceptance failed at $stage (${failure.javaClass.simpleName}); QA data retained")
            } finally {
                gateway.close()
                repo.close()
                setup.close()
            }
        }

    @Test fun realStorageWorkerAclInsertAndColdReopen() =
        runBlocking {
            val base = System.getenv("BOARDERLESS_MEDIA_LIVE_BASE_URL")
            assumeTrue(
                "Live acceptance requires explicit opt-in",
                base != null &&
                    System.getenv("BOARDERLESS_MEDIA_LIVE_ACCEPTANCE") == "true",
            )
            require(base in setOf("http://localhost:3000", "http://127.0.0.1:3000"))
            val root = File(requireNotNull(System.getenv("BOARDERLESS_TEST_REPO_ROOT")))
            val fixtureRoot = File(root, "backend/tests/fixtures/media")
            val fixtures =
                Json.parseToJsonElement(File(fixtureRoot, "manifest.json").readText()).jsonArray.map { row ->
                    Fixture(row.jsonObject, fixtureRoot)
                }
            assertEquals(
                setOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm"),
                fixtures.map { it.mediaType }.toSet(),
            )
            // Check local inputs before making any remote mutation.
            fixtures.forEach {
                assertEquals(it.byteSize, it.bytes.size.toLong())
                assertEquals(it.checksum, hash(it.bytes))
            }
            val setup = HttpClient { followRedirects = false }
            val prefs = SessionPreferences(InMemorySettings())
            val repo = BackendWorkspaceRepository(baseUrl = base!!, preferences = prefs)
            val gateway = BackendAssetTransferGateway(baseUrl = base, maximumProcessingPolls = 60)
            var stage = "setup"
            try {
                withTimeout(180_000) {
                    val runId = UUID.randomUUID().toString()

                    suspend fun createUser(label: String) =
                        setup
                            .post("$base/api/v1/users") {
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("displayName", "QA media $label $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    val owner = createUser("owner")
                    val viewer = createUser("viewer")
                    val outsider = createUser("outsider")
                    val workspace =
                        setup
                            .post("$base/api/v1/workspaces") {
                                header("x-user-id", owner)
                                contentType(ContentType.Application.Json)
                                setBody(buildJsonObject { put("title", "QA media live $runId") }.toString())
                            }.let { response ->
                                check(response.status.value in 200..299)
                                Json
                                    .parseToJsonElement(response.bodyAsText())
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive.content
                            }
                    println("LIVE_MEDIA_SCOPE workspace=$workspace owner=$owner viewer=$viewer outsider=$outsider")
                    prefs.userId = owner
                    prefs.workspaceId = workspace
                    var session = repo.openOrCreateWorkspace()
                    repo.setWorkspaceMemberRole(session, viewer, WorkspaceMemberRole.Viewer)
                    val nodes = mutableListOf<MediaNode>()
                    for (fixture in fixtures) {
                        stage = "import ${fixture.displayName}"
                        val imported = AssetImportCoordinator(gateway).import(session, fixture)
                        assertEquals(AssetStatus.Ready, imported.asset.status)
                        assertEquals(fixture.width, imported.asset.width)
                        assertEquals(fixture.height, imported.asset.height)
                        stage = "download ${fixture.displayName}"
                        verifyDownload(gateway, session, imported.asset.id, fixture.bytes)
                        val viewerSession = session.copy(userId = viewer, role = WorkspaceMemberRole.Viewer)
                        verifyDownload(gateway, viewerSession, imported.asset.id, fixture.bytes)
                        stage = "ACL ${fixture.displayName}"
                        assertEquals(
                            HttpStatusCode.Forbidden,
                            assertFailsWith<BackendHttpException> { gateway.prepare(viewerSession, fixture) }.status,
                        )
                        assertEquals(
                            HttpStatusCode.NotFound,
                            assertFailsWith<BackendHttpException> {
                                gateway.authorize(session.copy(userId = outsider), imported.asset.id)
                            }.status,
                        )
                        if (imported.mediaKind != MediaKind.Image) {
                            stage = "thumbnail ${fixture.displayName}"
                            var asset = repo.getAsset(session, imported.asset.id)
                            repeat(60) {
                                if (asset.thumbnailAssetId == null) {
                                    delay(500)
                                    asset = repo.getAsset(session, asset.id)
                                }
                            }
                            val thumbnail = requireNotNull(asset.thumbnailAssetId)
                            val metadata = repo.getAsset(session, thumbnail)
                            assertEquals(AssetStatus.Ready, metadata.status)
                            assertTrue(metadata.mediaType.startsWith("image/"))
                            verifyDownload(gateway, viewerSession, thumbnail)
                        }
                        stage = "insert ${fixture.displayName}"
                        val node =
                            mediaNodeFromReadyAsset(
                                imported.asset,
                                session.workspace.id,
                                CanvasObjectId(UUID.randomUUID().toString()),
                                Vec2(nodes.size * 360f, 0f),
                                1f,
                                nodes.size.toLong(),
                                fixture.displayName,
                            )
                        assertIs<SubmitOutcome.Accepted>(
                            repo.submit(
                                session,
                                CreateObjectsOperation(UUID.randomUUID().toString(), listOf(node)),
                            ),
                        )
                        session = repo.refresh(session)
                        assertEquals(node.assetId, (session.workspace.objectById(node.id) as MediaNode).assetId)
                        nodes += node
                        println("LIVE_MEDIA_ACCEPTED mime=${fixture.mediaType} asset=${imported.asset.id} node=${node.id.value}")
                    }
                    stage = "cold reopen"
                    val freshPrefs =
                        SessionPreferences(InMemorySettings()).apply {
                            userId = owner
                            workspaceId = workspace
                        }
                    val reopenedRepo = BackendWorkspaceRepository(baseUrl = base, preferences = freshPrefs)
                    try {
                        val reopened = reopenedRepo.openOrCreateWorkspace()
                        assertNotEquals(session.clientId, reopened.clientId)
                        assertEquals(6, reopened.workspace.objects.size)
                        nodes.forEach { node ->
                            val persisted = reopened.workspace.objectById(node.id) as MediaNode
                            assertEquals(node.assetId, persisted.assetId)
                            assertEquals(node.mediaKind, persisted.mediaKind)
                            verifyDownload(gateway, reopened, persisted.assetId)
                        }
                    } finally {
                        reopenedRepo.close()
                    }
                    stage = "revoked viewer"
                    repo.removeWorkspaceMember(session, viewer)
                    assertEquals(
                        HttpStatusCode.NotFound,
                        assertFailsWith<BackendHttpException> {
                            gateway.authorize(session.copy(userId = viewer, role = WorkspaceMemberRole.Viewer), nodes.last().assetId)
                        }.status,
                    )
                    println(
                        "LIVE_MEDIA_COMPLETE formats=6 coldReopen=true viewerRead=true outsiderDenied=true viewerWriteDenied=true revokedViewerDenied=true",
                    )
                }
            } catch (failure: Throwable) {
                // Do not expose HttpClient signed URL/body details in test reports. Keep QA data
                // for investigation, do not automatically delete/abandon an uncertain completion.
                throw AssertionError("Live media acceptance failed at $stage (${failure.javaClass.simpleName}); QA data retained")
            } finally {
                gateway.close()
                repo.close()
                setup.close()
            }
        }

    private suspend fun verifyDownload(
        gateway: BackendAssetTransferGateway,
        session: WorkspaceSession,
        assetId: String,
        expected: ByteArray? = null,
    ) {
        val downloaded = ByteArrayOutputStream()
        var committed = false
        AssetDownloadCoordinator(gateway).download(
            session,
            assetId,
            object : AssetDownloadSink {
                override suspend fun writeChunk(
                    offset: Long,
                    bytes: ByteArray,
                ) {
                    assertEquals(downloaded.size().toLong(), offset)
                    downloaded.write(bytes)
                }

                override suspend fun commit(
                    expectedByteSize: Long,
                    expectedChecksum: String,
                ): LocalAssetReference {
                    assertEquals(expectedByteSize, downloaded.size().toLong())
                    assertEquals(expectedChecksum, hash(downloaded.toByteArray()))
                    committed = true
                    return LocalAssetReference("fixture:$assetId", "application/octet-stream")
                }

                override suspend fun abort() {
                    downloaded.reset()
                }
            },
        )
        assertTrue(committed)
        expected?.let { assertContentEquals(it, downloaded.toByteArray()) }
    }

    private class Fixture(
        row: JsonObject,
        root: File,
    ) : AssetTransferSource {
        override val displayName = row.getValue("file").jsonPrimitive.content
        val bytes = File(root, displayName).readBytes()
        override val mediaType = row.getValue("mediaType").jsonPrimitive.content
        override val byteSize = row.getValue("byteSize").jsonPrimitive.long
        override val checksum = row.getValue("checksum").jsonPrimitive.content
        override val width = row.getValue("width").jsonPrimitive.int
        override val height = row.getValue("height").jsonPrimitive.int
        override val durationMs = row["durationMs"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.long

        override suspend fun readChunk(
            offset: Long,
            maximumBytes: Int,
        ): ByteArray = bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + maximumBytes))
    }

    companion object {
        private fun hash(bytes: ByteArray) =
            "sha256:" +
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
    }
}
