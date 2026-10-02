package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.AssetImportCoordinator
import cg.creamgod.boarderless.data.AssetImportStage
import cg.creamgod.boarderless.data.AssetTransferSource
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.AssetTransferUnavailableException
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.BackendHttpException
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.writeFully
import kotlinx.io.readByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendAssetTransferGatewayTest {
    @Test
    fun importCoordinatorCompletesHttpLifecycleBeforeReturningReadyMedia() = runTest {
        val requests = mutableListOf<String>()
        val client = client { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.method == HttpMethod.Put -> {
                    val channel = ByteChannel(autoFlush = true)
                    (request.body as OutgoingContent.WriteChannelContent).writeTo(channel)
                    channel.close()
                    assertTrue(channel.readBuffer().readByteArray().contentEquals(byteArrayOf(1, 2, 3, 4)))
                    respond("", HttpStatusCode.NoContent)
                }
                request.url.encodedPath.endsWith("/complete") -> jsonResponse("{\"asset\":${assetJson("ready")}}")
                request.method == HttpMethod.Post -> jsonResponse(
                    prepareResponse(""""upload":{"method":"PUT","url":"https://objects.invalid/upload-1"}"""),
                )
                request.method == HttpMethod.Get -> jsonResponse(assetJson("ready", "thumb-1"))
                else -> error("Unexpected request")
            }
        }
        val gateway = gateway(client)
        val stages = mutableListOf<AssetImportStage>()
        try {
            val imported = AssetImportCoordinator(gateway).import(session(), source()) { stages += it }

            assertEquals(AssetStatus.Ready, imported.asset.status)
            assertEquals("thumb-1", imported.thumbnailAssetId)
            assertTrue(stages.last() is AssetImportStage.Ready)
            assertEquals(
                listOf(
                    "POST /api/v1/workspaces/workspace-1/assets",
                    "PUT /upload-1",
                    "POST /api/v1/workspaces/workspace-1/assets/asset-1/complete",
                    "GET /api/v1/workspaces/workspace-1/assets/asset-1",
                ),
                requests,
            )
        } finally {
            gateway.close()
        }
    }

    @Test
    fun missingUploadDirectiveCleansPendingMetadataAndFailsClearly() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = client { request ->
            requests += request.method to request.url.encodedPath
            when (request.method) {
                HttpMethod.Post -> jsonResponse(prepareResponse(upload = "\"uploadUrl\":null"))
                HttpMethod.Delete -> jsonResponse("{}")
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val gateway = gateway(client)

        val error = assertFailsWith<AssetTransferUnavailableException> {
            gateway.prepare(session(), source())
        }

        assertTrue(error.message.orEmpty().contains("without an upload directive"))
        assertEquals(
            listOf(
                HttpMethod.Post to "/api/v1/workspaces/workspace-1/assets",
                HttpMethod.Delete to "/api/v1/workspaces/workspace-1/assets/asset-1",
            ),
            requests,
        )
    }

    @Test
    fun prepareAcceptsSignedUploadDirectiveWithoutPersistingProviderDetails() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("user-1", request.headers["x-user-id"])
            jsonResponse(
                prepareResponse(
                    upload = """"upload":{"method":"PUT","url":"https://objects.invalid/upload-1","headers":{"x-storage-token":"secret"}}""",
                ),
            )
        }

        val ticket = gateway(client).prepare(session(), source())

        assertEquals("asset-1", ticket.asset.id)
        assertEquals(AssetStatus.Pending, ticket.asset.status)
        assertEquals("https://objects.invalid/upload-1", ticket.uploadUrl)
        assertEquals(mapOf("x-storage-token" to "secret"), ticket.requiredHeaders)
    }

    @Test
    fun signedUploadStreamsSourceBytesAndReportsExactProgress() = runTest {
        var uploaded = byteArrayOf()
        val client = client { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("secret", request.headers["x-storage-token"])
            val content = request.body as OutgoingContent.WriteChannelContent
            val channel = ByteChannel(autoFlush = true)
            content.writeTo(channel)
            channel.close()
            uploaded = channel.readBuffer().readByteArray()
            respond("", HttpStatusCode.NoContent)
        }
        val progress = mutableListOf<Long>()

        gateway(client).upload(
            gateway(client).prepareTicketForTest().copy(requiredHeaders = mapOf("x-storage-token" to "secret")),
            source(),
        ) { progress += it }

        assertTrue(uploaded.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(listOf(4L), progress)
    }

    @Test
    fun confirmPollThumbnailAndDownloadAuthorizationFollowContract() = runTest {
        var assetReads = 0
        val client = client { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/complete") ->
                    jsonResponse("{\"asset\":${assetJson("pending")}}")

                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/content") ->
                    jsonResponse(
                        """{"asset":${assetJson("ready")},"download":{"method":"GET","url":"https://objects.invalid/content-1","headers":{"x-download-token":"secret"}}}""",
                    )

                request.method == HttpMethod.Get -> {
                    assetReads += 1
                    jsonResponse(assetJson(if (assetReads == 1) "pending" else "ready", thumbnailAssetId = "thumb-1"))
                }

                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val gateway = gateway(client)
        val ticket = gateway.prepareTicketForTest()

        assertEquals(AssetStatus.Pending, gateway.confirm(session(), ticket).status)
        assertEquals(AssetStatus.Ready, gateway.awaitReady(session(), "asset-1") {}.status)
        assertEquals("thumb-1", gateway.thumbnailAssetId(session(), "asset-1"))
        val download = gateway.authorize(session(), "asset-1")
        assertEquals("https://objects.invalid/content-1", download.downloadUrl)
        assertEquals("secret", download.requiredHeaders["x-download-token"])
    }

    @Test
    fun nonSuccessfulBackendResponseRetainsStatusAndBody() = runTest {
        val client = client { jsonResponse("{\"error\":\"denied\"}", HttpStatusCode.Forbidden) }

        val error = assertFailsWith<BackendHttpException> {
            gateway(client).prepare(session(), source())
        }

        assertEquals(HttpStatusCode.Forbidden, error.status)
        assertTrue(error.responseBody.contains("denied"))
    }

    @Test
    fun signedDownloadForwardsHeadersAndEmitsResponseBytes() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("secret", request.headers["x-download-token"])
            respond(byteArrayOf(5, 6, 7, 8), HttpStatusCode.OK)
        }
        val chunks = mutableListOf<ByteArray>()
        val ticket = cg.creamgod.boarderless.data.AssetDownloadTicket(
            asset = gateway(client).prepareTicketForTest().asset.copy(status = AssetStatus.Ready),
            downloadUrl = "https://objects.invalid/content-1",
            requiredHeaders = mapOf("x-download-token" to "secret"),
        )

        gateway(client).download(ticket) { chunks += it }

        val downloaded = buildList { chunks.forEach { chunk -> chunk.forEach(::add) } }.toByteArray()
        assertTrue(downloaded.contentEquals(byteArrayOf(5, 6, 7, 8)))
    }

    @Test
    fun downloadDeliversFirstChunkBeforeServerFinishesResponse() = runTest {
        val firstChunkDelivered = CompletableDeferred<Unit>()
        val responseChannel = ByteChannel(autoFlush = true)
        val producer = launch {
            responseChannel.writeFully(byteArrayOf(1, 2))
            firstChunkDelivered.await()
            responseChannel.writeFully(byteArrayOf(3, 4))
            responseChannel.close()
        }
        val client = client { respond(responseChannel, HttpStatusCode.OK) }
        val gateway = gateway(client)
        val received = mutableListOf<Byte>()
        val ticket = cg.creamgod.boarderless.data.AssetDownloadTicket(
            asset = gateway.prepareTicketForTest().asset.copy(status = AssetStatus.Ready),
            downloadUrl = "https://objects.invalid/content-1",
        )
        try {
            // The mock response arrives on a real dispatcher. Keep this handshake's timeout
            // on that dispatcher too, so runTest cannot advance virtual time past it.
            withContext(Dispatchers.Default) {
                withTimeout(3_000) {
                    gateway.download(ticket) { bytes ->
                        received.addAll(bytes.toList())
                        firstChunkDelivered.complete(Unit)
                    }
                }
            }
            assertEquals(listOf<Byte>(1, 2, 3, 4), received)
        } finally {
            producer.cancelAndJoin()
            gateway.close()
        }
    }

    private fun gateway(client: HttpClient) = BackendAssetTransferGateway(
        baseUrl = "https://api.invalid",
        client = client,
        processingPollMillis = 0,
        maximumProcessingPolls = 3,
    )

    private fun client(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun prepareResponse(upload: String) = """{"asset":${assetJson("pending")},$upload}"""

    private fun assetJson(status: String, thumbnailAssetId: String? = null): String {
        val thumbnail = thumbnailAssetId?.let { ",\"thumbnailAssetId\":\"$it\"" }.orEmpty()
        return """{"id":"asset-1","workspaceId":"workspace-1","ownerId":"user-1","storageKey":"assets/asset-1","mediaType":"image/png","byteSize":4,"checksum":"sha256:test","width":100,"height":80,"durationMs":null,"status":"$status","createdAt":"2026-10-01T00:00:00Z"$thumbnail}"""
    }

    private fun session() = WorkspaceSession(
        userId = "user-1",
        clientId = "client-1",
        role = WorkspaceMemberRole.Owner,
        workspaceVersion = 0,
        lastServerSeq = 0,
        workspace = Workspace(WorkspaceId("workspace-1"), "Media"),
    )

    private fun source() = object : AssetTransferSource {
        override val displayName = "asset.png"
        override val mediaType = "image/png"
        override val byteSize = 4L
        override val checksum = "sha256:test"
        override val width = 100
        override val height = 80
        override val durationMs: Long? = null
        override suspend fun readChunk(offset: Long, maximumBytes: Int) = byteArrayOf(1, 2, 3, 4)
    }
}

private fun BackendAssetTransferGateway.prepareTicketForTest() = cg.creamgod.boarderless.data.AssetUploadTicket(
    asset = cg.creamgod.boarderless.data.WorkspaceAsset(
        id = "asset-1",
        workspaceId = WorkspaceId("workspace-1"),
        ownerId = "user-1",
        mediaType = "image/png",
        byteSize = 4,
        checksum = "sha256:test",
        width = 100,
        height = 80,
        durationMs = null,
        status = AssetStatus.Pending,
        createdAt = "2026-10-01T00:00:00Z",
    ),
    uploadUrl = "https://objects.invalid/upload-1",
)
