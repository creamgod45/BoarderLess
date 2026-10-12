package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class PreparedAssetUploadTest {
    private val requestId = "00112233-4455-6677-8899-aabbccddeeff"
    private val localId = "11223344-5566-7788-99aa-bbccddeeff00"
    private val remoteId = "22334455-6677-8899-aabb-ccddeeff0011"
    private val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-owner", WorkspaceId("remote-workspace"))
    private val asset = WorkspaceAsset(localId, WorkspaceId("private-workspace"), "private-owner", "image/png", 3,
        "sha256:" + "B".repeat(64), 1, 1, null, AssetStatus.Ready, "2026-10-11")
    private val media = MediaNode(CanvasObjectId("private-node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)),
        assetId = asset.id, mediaKind = MediaKind.Image)
    private fun review(): LocalWorkspaceSyncSnapshot {
        val workspace = Workspace(asset.workspaceId, "private-title", objects = mapOf(media.id to media))
        val local = WorkspaceSession(asset.ownerId, "private-client", WorkspaceMemberRole.Owner, 0, 0, workspace)
        val baseline = workspace.copy(objects = emptyMap())
        val remote = local.copy(userId = binding.userId, clientId = "remote-client", workspace = baseline.copy(id = binding.workspaceId))
        val snapshot = LocalWorkspaceSyncSnapshot(local, LocalWorkspaceSyncCheckpoint(binding, baseline, 0, 0))
        val plan = LocalWorkspaceSyncMergePlan(snapshot, remote)
        return snapshot.copy(proposal = plan.proposal("private-review", plan.rows.associate { it.id to LocalSyncMergeChoice.Local }))
    }

    @Test fun deterministicBodyAndDigestExcludePrivateSourceIdentityAndNeverContainTickets() {
        val request = prepareLocalAssetUploadRequest(review(), asset, requestId)
        val body = request.requestJson()
        // Independently computed with Ruby JSON.generate + Digest::SHA256 for the exact metadata bytes.
        assertEquals("32d5f0d51e3a30dc0c7057664dc28914132cf09fe6e109098725c05d26f602ee", request.requestDigest)
        assertEquals("{\"schema\":\"boarderless.asset-prepare.v1\",\"requestId\":\"$requestId\",\"metadata\":{\"byteSize\":3,\"checksum\":\"sha256:${"b".repeat(64)}\",\"durationMs\":null,\"height\":1,\"mediaType\":\"image/png\",\"width\":1}}", body)
        for (private in listOf(localId, "private-owner", "private-workspace", "private-review", "private-title", "private-client")) assertFalse(body.contains(private))
        assertEquals(request.requestDigest, prepareLocalAssetUploadRequest(review(), asset.copy(checksum = asset.checksum.lowercase()), requestId).requestDigest)
        assertEquals(request.requestDigest, prepareLocalAssetUploadRequest(review(), asset, remoteId).requestDigest)
        assertEquals(request, Json.decodeFromString<PreparedAssetUploadRequest>(Json.encodeToString(request)))
        assertEquals(setOf("schema", "requestId", "metadata"), Json.parseToJsonElement(body).jsonObject.keys)
        assertFails { request.copy(requestDigest = "0".repeat(64)) }
    }

    @Test fun sourceMustBeReadyOwnedReferencedAndWithinCurrentReviewScope() {
        val snapshot = review()
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(status = AssetStatus.Pending), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(ownerId = "other"), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(workspaceId = WorkspaceId("other")), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(id = "unreferenced"), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(mediaType = "video/mp4"), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset.copy(durationMs = Long.MAX_VALUE), requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot.copy(local = snapshot.local.copy(role = WorkspaceMemberRole.Viewer)), asset, requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot.copy(local = snapshot.local.copy(workspace = snapshot.local.workspace.copy(title = "Changed"))), asset, requestId) }
        assertFails { prepareLocalAssetUploadRequest(snapshot, asset, "not-uuid") }
    }

    @Test fun receiptIdentityIsImmutableAndCannotBeMistakenForReadyOrGraphAuthorization() {
        val request = prepareLocalAssetUploadRequest(review(), asset, requestId)
        val remote = asset.copy(id = remoteId, workspaceId = binding.workspaceId, ownerId = binding.userId, checksum = asset.checksum.lowercase(), status = AssetStatus.Pending)
        val empty = LocalAssetUploadPreparation(request)
        val saved = empty.bindReceipt(requestId, request.requestDigest, remote)
        assertEquals(saved, saved.bindReceipt(requestId, request.requestDigest, remote.copy(status = AssetStatus.Ready)))
        assertEquals(remoteId, saved.remoteIdentity!!.id)
        assertFails { saved.bindReceipt(requestId, request.requestDigest, remote.copy(id = requestId)) }
        assertFails { saved.bindReceipt(requestId, request.requestDigest, remote.copy(width = 2)) }
        assertFails { empty.bindReceipt(remoteId, request.requestDigest, remote) }
        assertFails { empty.bindReceipt(requestId, "0".repeat(64), remote) }
        assertFails { empty.bindReceipt(requestId, request.requestDigest, remote.copy(ownerId = "other")) }
        assertFails { empty.bindReceipt(requestId, request.requestDigest, remote.copy(status = AssetStatus.Rejected)) }
        val collision = empty.bindReceipt(requestId, request.requestDigest, remote.copy(id = localId))
        assertEquals(localId, collision.remoteIdentity!!.id) // Retain known receipt; do not invent another request.
        assertFails { LocalAssetMaterialization(binding, request.userId, request.clientId, request.workspaceId,
            request.reviewId, collision.remoteIdentity!!, localId, "2026-10-11") }
        assertEquals(saved, Json.decodeFromString<LocalAssetUploadPreparation>(Json.encodeToString(saved)))
    }
}
