package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserAssetMaterializationMappingTest {
    @Test fun interruptedStreamReopensAndReplaysExactPrefixBeforeAppendingSameOriginal() = runTest {
        browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("allow")
        try {
            val png = kotlin.io.encoding.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGMQMgn7DwAClAGcHVtGXwAAAABJRU5ErkJggg==")
            val bytes = png.copyOf(2 * DefaultAssetUploadChunkBytes + 7)
            val local = browserLocalWorkspaceRepository()
            val (saved, server) = materializationTestSetup(local, bytes)
            val mapping = saved.materializations.single()
            server.chunkBytes = DefaultAssetUploadChunkBytes; server.failAfterChunks = 1
            server.afterChunk = {
                var prefix = 0L
                repeat(20) {
                    if (prefix != DefaultAssetUploadChunkBytes.toLong())
                        prefix = BrowserOriginalStore().resumePrefix(mapping.localAssetId, mapping.localAssetId, bytes.size.toLong())
                }
                assertEquals(DefaultAssetUploadChunkBytes.toLong(), prefix)
            }
            assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }) }
            assertEquals(DefaultAssetUploadChunkBytes.toLong(), BrowserOriginalStore().resumePrefix(mapping.localAssetId, mapping.localAssetId, bytes.size.toLong()))
            assertEquals(AssetStatus.Pending, local.getAsset(saved.local, mapping.localAssetId).status)
            assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
            local.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            try {
                val current = reopened.openOrCreateWorkspace()
                server.afterChunk = {}; server.failAfterChunks = Int.MAX_VALUE
                server.bytes = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
                assertFails { materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true }) }
                BrowserOriginalStore().verifyPrefixChunk(mapping.localAssetId, mapping.localAssetId, bytes.size.toLong(), 0,
                    bytes.copyOfRange(0, DefaultAssetUploadChunkBytes))
                server.bytes = bytes
                server.failAfterChunks = Int.MAX_VALUE; server.chunkBytes = 37 * 1024
                val ready = materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true })
                assertEquals(AssetStatus.Ready, ready.status); assertEquals(mapping.localAssetId, ready.id)
                assertEquals(listOf(ready), reopened.listAssets(current))
                assertEquals(saved, reopened.localSyncStore!!.readSyncSnapshot(current))
                assertEquals(ready, materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true }))
                assertEquals(3, server.downloads)
            } finally { reopened.close() }
        } finally { browserOriginalFixtureRestore(); browserRecordFixtureRestore() }
    }
    @Test fun realIndexedDbReopensMappingAndPinsSameLocalCatalogIdentityWithoutChangingCanvas() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repo = browserLocalWorkspaceRepository()
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
            val asset = WorkspaceAsset("remote-image", binding.workspaceId, "creator", "image/png", 100, "sha256:" + "b".repeat(64),
                32, 24, null, AssetStatus.Ready, "2026-10-11T00:00:00Z")
            val reviewed = savedRemoteMediaReview(repo, binding, asset)
            val mapping = newLocalAssetMaterialization(reviewed, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
            val saved = repo.localSyncStore!!.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping)
            assertTrue(repo.listAssets(saved.local).isEmpty())
            repo.close(); browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            try {
                val current = reopened.openOrCreateWorkspace()
                val restored = reopened.localSyncStore!!.readSyncSnapshot(current)
                assertEquals(saved, restored)
                val pinned = assertIs<PinnedLocalAssetGateway>(reopened.localAssetGateway)
                val prepared = pinned.preparePinned(current, restored.materializations.single().pendingAsset())
                assertEquals(mapping.localAssetId, prepared.asset.id); assertEquals(AssetStatus.Pending, prepared.asset.status)
                assertEquals(prepared, pinned.preparePinned(current, mapping.pendingAsset()))
                assertEquals(restored, reopened.localSyncStore!!.readSyncSnapshot(current))
                assertEquals(listOf(prepared.asset), reopened.listAssets(current))
                assertEquals(listOf(mapping), LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), restored.toEvidenceJson()).materializations)
            } finally { reopened.close() }
        } finally { browserRecordFixtureRestore() }
    }
}
