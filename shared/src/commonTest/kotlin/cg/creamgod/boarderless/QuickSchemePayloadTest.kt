package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickSchemePayloadTest {
    private val node = ClipboardNode("text", 0f, 0f, 240f, 120f, 0f, "Keep me", "paper")
    private fun scheme(payload: ClipboardPayload, version: Int = payload.version) = QuickScheme(
        1, "Fixture", ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), version,
    )

    @Test
    fun readsEverySupportedVersionWithoutRewritingStoredData() {
        for (version in 1..4) {
            val source = scheme(ClipboardPayload(version = version, nodes = listOf(node)))
            val original = source.payload
            assertEquals(version, decodeQuickSchemePayload(source)?.version)
            assertEquals("Keep me", decodeQuickSchemePayload(source)?.nodes?.single()?.text)
            assertEquals(original, source.payload)
        }
    }

    @Test
    fun missingJsonVersionUsesStoredVersionNotLatestConstructorDefault() {
        val source = scheme(ClipboardPayload(nodes = listOf(node)))
        val document = ClipboardJson.parseToJsonElement(source.payload) as JsonObject
        val legacyJson = ClipboardJson.encodeToString(JsonObject.serializer(), JsonObject(document - "version"))
        assertEquals(1, decodeQuickSchemePayload(source.copy(payload = legacyJson, schemaVersion = 1))?.version)
        assertEquals(2, decodeQuickSchemePayload(source.copy(payload = legacyJson, schemaVersion = 2))?.version)
    }

    @Test
    fun legacyMetadataAcceptsExplicitNewerJsonButContradictoryModernMetadataDoesNot() {
        val source = scheme(ClipboardPayload(version = 2, nodes = listOf(node)))
        assertEquals(2, decodeQuickSchemePayload(source.copy(schemaVersion = 1))?.version)
        assertNull(decodeQuickSchemePayload(source.copy(schemaVersion = 3)))
        assertNull(decodeQuickSchemePayload(source.copy(schemaVersion = 5)))
        assertNull(decodeQuickSchemePayload(scheme(ClipboardPayload(version = 5, nodes = listOf(node)), 1)))
    }

    @Test
    fun validMediaOnlySchemeRetainsAssetReferencesAndRelations() {
        val image = ClipboardMedia("image", 0f, 0f, 320f, 180f, 30f, "image-asset", "image")
        val video = image.copy(originalId = "video", assetId = "video-asset", mediaKind = "video",
            thumbnailAssetId = "poster-asset")
        val payload = ClipboardPayload(nodes = emptyList(), media = listOf(image, video),
            relations = listOf(ClipboardRelation("image", "video", "Forward", label = "Reference")))
        assertEquals(payload, decodeQuickSchemePayload(scheme(payload)))
    }

    @Test
    fun invalidContentCannotReachPreviewOrInsertion() {
        val source = scheme(ClipboardPayload(nodes = listOf(node)))
        assertNull(decodeQuickSchemePayload(source.copy(payload = "not json")))
        assertNull(decodeQuickSchemePayload(source.copy(payload = "[]")))
        assertNull(decodeQuickSchemePayload(scheme(ClipboardPayload(nodes = emptyList()))))
        assertNull(decodeQuickSchemePayload(scheme(ClipboardPayload(nodes = listOf(node.copy(width = -1f))))))
        assertNull(decodeQuickSchemePayload(scheme(ClipboardPayload(nodes = listOf(node),
            relations = listOf(ClipboardRelation("text", "missing", "Forward"))))))
    }

    @Test
    fun mediaRequiresVerifiedReadyDestinationAssetEvenForLegacySchemes() {
        val workspace = WorkspaceId("source")
        val media = ClipboardMedia("photo", 0f, 0f, 320f, 180f, 0f, "asset", "image")
        val payload = ClipboardPayload(nodes = emptyList(), media = listOf(media))
        val legacy = scheme(payload)
        val asset = WorkspaceAsset("asset", workspace, "owner", "image/png", 123,
            "sha256:fixture", 640, 480, null, AssetStatus.Ready, "2026-10-02")
        fun available(candidate: WorkspaceAsset) = quickSchemeMediaAvailable(legacy, payload, workspace,
            mapOf("asset" to candidate))
        assertEquals(true, available(asset))
        assertEquals(false, available(asset.copy(status = AssetStatus.Pending)))
        assertEquals(false, available(asset.copy(mediaType = "video/mp4")))
        assertEquals(false, available(asset.copy(workspaceId = WorkspaceId("other"))))
        assertEquals(false, available(asset.copy(id = "different")))
        assertEquals(false, quickSchemeMediaAvailable(legacy, payload, workspace, emptyMap()))
        assertEquals(false, quickSchemeMediaAvailable(legacy, payload, null, mapOf("asset" to asset)))
    }

    @Test
    fun recordedSourceCannotBeReassignedByOpeningAnotherCanvas() {
        val destination = WorkspaceId("destination")
        val media = ClipboardMedia("photo", 0f, 0f, 320f, 180f, 0f, "asset", "image")
        val payload = ClipboardPayload(nodes = emptyList(), media = listOf(media))
        val source = scheme(payload).copy(sourceWorkspaceId = "source")
        val asset = WorkspaceAsset("asset", destination, "owner", "image/png", 123,
            "sha256:fixture", 640, 480, null, AssetStatus.Ready, "2026-10-02")
        assertEquals(false, quickSchemeMediaAvailable(source, payload, destination, mapOf("asset" to asset)))
        assertEquals("source", source.sourceWorkspaceId)
    }

    @Test
    fun textOnlySchemesRemainPortableWithoutAssetMetadata() {
        val payload = ClipboardPayload(nodes = listOf(node))
        assertEquals(true, quickSchemeMediaAvailable(scheme(payload).copy(sourceWorkspaceId = "old"),
            payload, null, emptyMap()))
    }
}
