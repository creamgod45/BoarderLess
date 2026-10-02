package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.browserMediaImportRuntime
import cg.creamgod.boarderless.data.AssetUploadTicket
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.domain.model.WorkspaceId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserMediaImportRuntimeTest {
    @Test
    fun gatewayUploadsBrowserFileWithoutBufferingThroughKtor() = runTest {
        installBrowserMediaFixture("upload")
        val source = assertNotNull(browserMediaImportRuntime().selectSource?.invoke())
        val gateway = BackendAssetTransferGateway(client = HttpClient(MockEngine { error("Native File must bypass Ktor buffering") }))
        try {
            val ticket = AssetUploadTicket(
                asset = WorkspaceAsset(
                    id = "asset-1", workspaceId = WorkspaceId("workspace-1"), ownerId = "user-1",
                    mediaType = source.mediaType, byteSize = source.byteSize, checksum = source.checksum,
                    width = null, height = null, durationMs = null, status = AssetStatus.Pending,
                    createdAt = "2026-10-01T00:00:00Z",
                ),
                uploadUrl = "https://objects.invalid/signed",
                requiredHeaders = mapOf("x-storage-token" to "secret"),
            )
            val progress = mutableListOf<Long>()
            gateway.upload(ticket, source) { progress += it }
            assertEquals(listOf(2L, 3L), progress)
            assertTrue(browserMediaFixtureNativeUploadUsed())
        } finally {
            source.release()
            gateway.close()
        }
    }

    @Test
    fun browserFileFlowsThroughPlatformBridgeAndIncrementalHasher() = runTest {
        installBrowserMediaFixture("ready")
        val source = assertNotNull(browserMediaImportRuntime().selectSource?.invoke())
        try {
            assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", source.checksum)
            assertEquals("image/png", source.mediaType)
            assertEquals(3L, source.byteSize)
            assertContentEquals(byteArrayOf(98, 99), source.readChunk(1, 2))
            assertContentEquals(byteArrayOf(), source.readChunk(3, 2))
            assertTrue(browserMediaFixtureInputRemoved())
        } finally {
            source.release()
        }
        assertFailsWith<IllegalStateException> { source.readChunk(0, 1) }
    }

    @Test
    fun nativeCancelReturnsNoSourceAndRemovesPicker() = runTest {
        installBrowserMediaFixture("cancel")
        assertNull(browserMediaImportRuntime().selectSource?.invoke())
        assertTrue(browserMediaFixtureInputRemoved())
    }
}

internal expect fun installBrowserMediaFixture(mode: String)
internal expect fun browserMediaFixtureInputRemoved(): Boolean
internal expect fun browserMediaFixtureNativeUploadUsed(): Boolean
