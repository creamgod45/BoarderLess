package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopAssetMaterializationMappingTest {
    @Test fun actualPngStreamsToOriginalAndReopenReusesVerifiedIdentityWithoutCanvasApply() = runTest {
        val root = Files.createTempDirectory("boarderless-materialization-transfer-")
        try {
            val output = java.io.ByteArrayOutputStream()
            assertTrue(javax.imageio.ImageIO.write(java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", output))
            val local = DesktopFileWorkspaceRepository(root.resolve("documents"))
            val (saved, server) = materializationTestSetup(local, output.toByteArray())
            val mapping = saved.materializations.single()
            val ready = materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true })
            assertEquals(mapping.localAssetId, ready.id); assertEquals(AssetStatus.Ready, ready.status)
            assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
            local.close()
            val reopened = DesktopFileWorkspaceRepository(root.resolve("documents"))
            try {
                val current = reopened.openOrCreateWorkspace()
                assertEquals(ready, materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true }))
                assertEquals(1, server.downloads); assertEquals(4, server.authorizations)
                assertEquals(saved, reopened.localSyncStore!!.readSyncSnapshot(current))
                assertEquals(listOf(ready), reopened.listAssets(current))
            } finally { reopened.close() }
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun realDocumentReopensMappingBeforeAnyLocalAssetPreparation() = runTest {
        val root = Files.createTempDirectory("boarderless-materialization-map-")
        try {
            val repo = DesktopFileWorkspaceRepository(root.resolve("documents"))
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
            val asset = WorkspaceAsset("remote-image", binding.workspaceId, "creator", "image/png", 100, "sha256:" + "b".repeat(64),
                32, 24, null, AssetStatus.Ready, "2026-10-11T00:00:00Z")
            val reviewed = savedRemoteMediaReview(repo, binding, asset)
            val mapping = newLocalAssetMaterialization(reviewed, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
            val saved = repo.localSyncStore!!.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping)
            assertTrue(repo.listAssets(reviewed.local).isEmpty())
            repo.close()
            val reopened = DesktopFileWorkspaceRepository(root.resolve("documents"))
            try {
                val restored = reopened.localSyncStore!!.readSyncSnapshot(reopened.openOrCreateWorkspace())
                assertEquals(saved, restored)
                assertEquals(reviewed.checkpoint, restored.checkpoint)
                assertEquals(reviewed.local.workspace, restored.local.workspace)
                assertEquals(restored, reopened.localSyncStore!!.stageSyncMaterialization(restored.local, restored.checkpoint!!, mapping))
                assertEquals(listOf(mapping), LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), restored.toEvidenceJson()).materializations)
            } finally { reopened.close() }
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
