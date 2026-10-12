package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PinnedLocalAssetTest {
    private val workspace = Workspace(WorkspaceId("local"), "Local")
    private val owner = WorkspaceSession("owner", "client", WorkspaceMemberRole.Owner, 0, 0, workspace)
    private val pending = WorkspaceAsset("00112233-4455-6677-8899-aabbccddeeff", workspace.id, owner.userId,
        "image/png", 10, "sha256:" + "a".repeat(64), 32, 24, null, AssetStatus.Pending, "2026-10-11T00:00:00Z")
    @Test fun pinnedDescriptorRequiresExactEditableLocalScopeAndValidOriginalMetadata() {
        validatePinnedLocalAsset(owner, pending)
        assertFails { validatePinnedLocalAsset(owner.copy(role = WorkspaceMemberRole.Viewer), pending) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(ownerId = "other")) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(workspaceId = WorkspaceId("remote"))) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(id = "remote-id")) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(status = AssetStatus.Ready)) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(checksum = "bad")) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(byteSize = MaxWorkspaceAssetBytes + 1)) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(mediaType = "IMAGE/PNG")) }
        assertFails { validatePinnedLocalAsset(owner, pending.copy(thumbnailAssetId = "thumbnail")) }
    }
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null; var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    @Test fun unknownCatalogAckReopensSamePendingIdentityWithoutRewritingReady() = runTest {
        val records = Records().apply { failConfirm = true }
        assertFails { AwaitedLocalAssetCatalog(records).ensurePinned(pending) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = AwaitedLocalAssetCatalog(records)
        assertEquals(pending, reopened.ensurePinned(pending))
        assertEquals(generation, records.value!!.generation)
        assertFails { reopened.ensurePinned(pending.copy(width = 33)) }
        val ready = reopened.ready(pending)
        val readyGeneration = records.value!!.generation
        assertEquals(ready, AwaitedLocalAssetCatalog(records).ensurePinned(pending))
        assertEquals(readyGeneration, records.value!!.generation)
        assertEquals(listOf(ready), reopened.list(owner))
    }
}
