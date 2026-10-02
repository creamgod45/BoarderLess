package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.remote.AssetDto
import cg.creamgod.boarderless.data.remote.BackendContractException
import cg.creamgod.boarderless.data.remote.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetContractTest {
    @Test fun derivativeReferenceSurvivesMetadataMappingWithoutStorageUrl() {
        assertEquals("poster", dto(status = "ready").copy(thumbnailAssetId = "poster").toDomain().thumbnailAssetId)
    }

    @Test fun blankAndSelfReferencingThumbnailsFailClosed() {
        assertFailsWith<BackendContractException> { dto(status = "ready").copy(thumbnailAssetId = " ").toDomain() }
        assertFailsWith<BackendContractException> { dto(status = "ready").copy(thumbnailAssetId = "asset-1").toDomain() }
    }

    @Test
    fun backendMetadataMapsToValidatedDomainAsset() {
        val asset = dto(status = "ready").toDomain()

        assertEquals("asset-1", asset.id)
        assertEquals(AssetStatus.Ready, asset.status)
        assertEquals(1920, asset.width)
        assertEquals(1080, asset.height)
        assertEquals(12_345L, asset.durationMs)
    }

    @Test
    fun unsupportedStatusAndInvalidMetadataFailClosed() {
        assertFailsWith<BackendContractException> { dto(status = "transcoding").toDomain() }
        assertFailsWith<BackendContractException> { dto(status = "ready", byteSize = 0).toDomain() }
        assertFailsWith<BackendContractException> { dto(status = "ready", storageKey = "").toDomain() }
    }

    private fun dto(
        status: String,
        byteSize: Long = 42,
        storageKey: String = "workspaces/workspace-1/assets/asset-1.mp4",
    ) = AssetDto(
        id = "asset-1",
        workspaceId = "workspace-1",
        ownerId = "owner-1",
        storageKey = storageKey,
        mediaType = "video/mp4",
        byteSize = byteSize,
        checksum = "sha256:abc",
        width = 1920,
        height = 1080,
        durationMs = 12_345,
        status = status,
        createdAt = "2026-09-30T00:00:00.000Z",
    )
}
