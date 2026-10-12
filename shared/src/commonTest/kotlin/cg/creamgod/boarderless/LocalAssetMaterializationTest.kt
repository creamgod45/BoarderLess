package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Auth/transport are controlled fixtures; this saves a review but never calls a server. */
internal suspend fun savedRemoteMediaReview(repo: CanvasWorkspaceRepository, binding: LocalWorkspaceSyncBinding,
    asset: WorkspaceAsset, thumbnailAssetId: String? = null): LocalWorkspaceSyncSnapshot {
    val owner = repo.openOrCreateWorkspace()
    val media = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), assetId = asset.id,
        mediaKind = requireNotNull(mediaKindForAssetMediaType(asset.mediaType)), thumbnailAssetId = thumbnailAssetId)
    val remote = WorkspaceSession(binding.userId, "server-client", WorkspaceMemberRole.Editor, owner.workspaceVersion, 0,
        owner.workspace.copy(id = binding.workspaceId, objects = mapOf(media.id to media)))
    val store = repo.localSyncStore!!
    val bound = store.bindSyncDestination(owner, binding, remote)
    val plan = LocalWorkspaceSyncMergePlan(bound, remote)
    return store.stageSyncProposal(owner, bound.checkpoint!!, null,
        plan.proposal("media-review", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote }))
}

class LocalAssetMaterializationTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null; var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    private val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
    private val asset = WorkspaceAsset("remote-asset", binding.workspaceId, "another-creator", "image/png", 100,
        "sha256:" + "b".repeat(64), 32, 24, null, AssetStatus.Ready, "2026-10-11T00:00:00Z", thumbnailAssetId = "remote-thumbnail")
    private suspend fun review(repo: CanvasWorkspaceRepository) = savedRemoteMediaReview(repo, binding, asset)
    @Test fun unknownMappingAckReopensSameIdentityAndNeverMintsReadyOrChangesCanvas() = runTest {
        val records = Records(); val repo = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }
        val reviewed = review(repo)
        val mapping = newLocalAssetMaterialization(reviewed, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
        assertEquals("another-creator", mapping.source.ownerId) // Shared reader need not own the original.
        assertNull(mapping.pendingAsset().thumbnailAssetId); assertEquals(AssetStatus.Pending, mapping.pendingAsset().status)
        records.failConfirm = true
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }
        val saved = reopened.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping)
        assertEquals(generation, records.value!!.generation)
        assertEquals(listOf(mapping), saved.materializations)
        assertEquals(reviewed.local.workspace, saved.local.workspace); assertEquals(reviewed.checkpoint, saved.checkpoint)
        assertEquals(reviewed.proposal, saved.proposal); assertNull(saved.applied); assertNull(saved.outgoing)
        assertFails { reopened.stageSyncMaterialization(saved.local, saved.checkpoint!!, mapping.copy(localAssetId = "11223344-5566-7788-99aa-bbccddeeff00")) }
        assertFails { reopened.stageSyncMaterialization(saved.local, saved.checkpoint!!, mapping.copy(source = mapping.source.copy(byteSize = 101))) }
        val exported = LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), saved.toEvidenceJson())
        assertEquals(saved.materializations, exported.materializations)
        reopened.retainDraftAwaited(saved.local, saved.local.workspace,
            CreateObjectsOperation("later-local", listOf(TextNode(CanvasObjectId("later"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Later"))))
        val retry = reopened.stageSyncMaterialization(saved.local, saved.checkpoint!!, mapping)
        assertTrue(CanvasObjectId("later") in retry.local.workspace.objects)
        assertEquals(listOf(mapping), retry.materializations)
    }
    @Test fun scopeStaleReviewUnreferencedOriginalAndInvalidMetadataCannotReserve() = runTest {
        val records = Records(); val repo = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }; val reviewed = review(repo)
        val mapping = newLocalAssetMaterialization(reviewed, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
        assertFails { MaterializationSourceDescriptor.fromAsset(asset.copy(status = AssetStatus.Pending)) }
        assertFails { MaterializationSourceDescriptor.fromAsset(asset.copy(checksum = "invalid")) }
        assertFails { MaterializationSourceDescriptor.fromAsset(asset.copy(createdAt = "\uD800")) }
        assertFails { mapping.copy(source = mapping.source.copy(workspaceId = WorkspaceId("other"))) }
        assertFails { repo.stageSyncMaterialization(reviewed.local.copy(role = WorkspaceMemberRole.Viewer), reviewed.checkpoint!!, mapping) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping.copy(userId = "other")) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping.copy(clientId = "other")) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping.copy(binding = binding.copy(serverScope = "c".repeat(64)))) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping.copy(reviewId = "other")) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping.copy(source = mapping.source.copy(id = "not-in-target"))) }
        assertFails { repo.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!.copy(serverSeq = 1), mapping) }
        assertEquals(reviewed, repo.readSyncSnapshot(reviewed.local))
        repo.retainDraftAwaited(reviewed.local, reviewed.local.workspace,
            LocalWorkspaceContentOperation("edit-after-review", reviewed.local.workspace, reviewed.local.workspace.copy(title = "Changed")))
        val latest = repo.readSyncSnapshot(reviewed.local)
        assertFails { repo.stageSyncMaterialization(latest.local, latest.checkpoint!!, mapping) }
        assertTrue(latest.materializations.isEmpty())
    }
}
