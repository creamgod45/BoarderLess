package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MaterializationReviewActionsTest {
    @Test fun thumbnailHasIndependentInspectionAndReservationAndMustBeAnImage() = runTest {
        val local = AwaitedLocalWorkspaceRepository(Records()) { "2026-10-11" }
        val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
        val thumbnail = WorkspaceAsset("thumbnail", binding.workspaceId, "creator", "image/png", 3,
            "sha256:" + "b".repeat(64), 1, 1, null, AssetStatus.Ready, "2026-10-11")
        val reviewed = savedRemoteMediaReview(local, binding, thumbnail.copy(id = "video", mediaType = "video/mp4"), thumbnail.id)
        val fixture = MaterializationTestServer(local, reviewed.proposal!!.remoteSession(), thumbnail, byteArrayOf(1, 2, 3))
        val server = object : CanvasWorkspaceRepository by fixture {
            override suspend fun getAsset(session: WorkspaceSession, assetId: String) = fixture.asset
        }
        assertEquals(setOf(MediaKind.Image), reviewed.proposal!!.target.materializationReferenceKinds(thumbnail.id))
        assertEquals(thumbnail, inspectMaterializationSource(local, reviewed, server, fixture.current, thumbnail.id, { true }))
        val mapping = newLocalAssetMaterialization(reviewed, thumbnail, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11")
        val saved = saveReviewedMaterialization(local, reviewed, server, fixture.current, mapping, { true })
        assertEquals(listOf(mapping), saved.materializations); assertEquals(0, fixture.downloads)
        fixture.asset = thumbnail.copy(mediaType = "video/mp4")
        assertFails { inspectMaterializationSource(local, saved, server, fixture.current, thumbnail.id, { true }) }
        assertEquals(saved, local.readSyncSnapshot(saved.local))
    }
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null; var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    @Test fun metadataInspectionWritesNothingAndApprovalRecoversExactUnknownSave() = runTest {
        val records = Records(); val local = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }
        val (saved, fixture) = materializationTestSetup(local, byteArrayOf(1, 2, 3))
        val reviewed = saved.copy(materializations = emptyList())
        // A separate review without reservation for the inspect/save workflow.
        val otherRecords = Records(); val other = AwaitedLocalWorkspaceRepository(otherRecords) { "2026-10-11" }
        val freshReview = savedRemoteMediaReview(other, reviewed.checkpoint!!.binding, fixture.asset)
        val server = object : CanvasWorkspaceRepository by fixture {
            override suspend fun getAsset(session: WorkspaceSession, assetId: String) = fixture.asset
        }
        fixture.current = freshReview.proposal!!.remoteSession()
        val generation = otherRecords.value!!.generation
        val asset = inspectMaterializationSource(other, freshReview, server, fixture.current, fixture.asset.id, { true })
        assertEquals(fixture.asset, asset); assertEquals(generation, otherRecords.value!!.generation)
        assertTrue(other.readSyncSnapshot(freshReview.local).materializations.isEmpty()); assertEquals(0, fixture.downloads)
        val mapping = newLocalAssetMaterialization(freshReview, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
        otherRecords.failConfirm = true
        assertFails { saveReviewedMaterialization(other, freshReview, server, fixture.current, mapping, { true }) }
        val published = otherRecords.value!!.generation; otherRecords.failConfirm = false
        val restored = AwaitedLocalWorkspaceRepository(otherRecords) { "2026-10-11" }
        val result = saveReviewedMaterialization(restored, freshReview, server, fixture.current, mapping, { true })
        assertEquals(published, otherRecords.value!!.generation); assertEquals(listOf(mapping), result.materializations)
        assertEquals(freshReview.local.workspace, result.local.workspace); assertEquals(0, fixture.downloads)
    }
    @Test fun changedMetadataHeadClosedScopeAndUnreviewedSourceNeverSave() = runTest {
        val local = AwaitedLocalWorkspaceRepository(Records()) { "2026-10-11" }
        val (saved, fixture) = materializationTestSetup(local, byteArrayOf(1, 2, 3))
        val server = object : CanvasWorkspaceRepository by fixture {
            override suspend fun getAsset(session: WorkspaceSession, assetId: String) = fixture.asset
        }
        val mapping = saved.materializations.single()
        assertFails { inspectMaterializationSource(local, saved, server, fixture.current, "unreviewed", { true }) }
        assertFails { inspectMaterializationSource(local, saved, server, fixture.current, fixture.asset.id, { false }) }
        fixture.asset = fixture.asset.copy(checksum = "sha256:" + "b".repeat(64))
        assertFails { saveReviewedMaterialization(local, saved, server, fixture.current, mapping, { true }) }
        fixture.current = fixture.current.copy(lastServerSeq = 1)
        assertFails { inspectMaterializationSource(local, saved, server, fixture.current, fixture.asset.id, { true }) }
        assertEquals(saved, local.readSyncSnapshot(saved.local))
    }
    @Test fun imageMimeIsClassifiedAsImageWhileWrongKindIsRejected() = runTest {
        val documents = AwaitedLocalWorkspaceRepository(Records()) { "2026-10-11" }; val owner = documents.openOrCreateWorkspace()
        val node = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), assetId = "asset", mediaKind = MediaKind.Image)
        val session = owner.copy(workspace = owner.workspace.copy(objects = mapOf(node.id to node)))
        val asset = WorkspaceAsset(node.assetId, owner.workspace.id, owner.userId, "image/png", 3, "sha256:" + "a".repeat(64), 1, 1, null,
            AssetStatus.Ready, "2026-10-11")
        val repo = object : CanvasWorkspaceRepository by documents {
            override suspend fun getAsset(session: WorkspaceSession, assetId: String) = asset
        }
        validateLocalSyncTargetAssets(repo, session, session.workspace)
        assertFails { validateLocalSyncTargetAssets(repo, session, session.workspace.copy(objects = mapOf(node.id to node.copy(mediaKind = MediaKind.Video)))) }
    }
}
