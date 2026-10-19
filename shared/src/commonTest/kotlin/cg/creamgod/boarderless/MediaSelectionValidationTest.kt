package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaSelectionValidationTest {
    private val workspace = WorkspaceId("destination")
    private val media =
        listOf("image", "gif", "video").map { kind ->
            ClipboardMedia(kind, 0f, 0f, 320f, 180f, 0f, "asset-$kind", kind, parentOriginalId = "group")
        }
    private val payload =
        ClipboardPayload(
            nodes =
                listOf(
                    ClipboardNode(
                        "text",
                        0f,
                        0f,
                        240f,
                        120f,
                        0f,
                        "Keep all",
                        "paper",
                        parentOriginalId = "group",
                    ),
                ),
            groups = listOf(ClipboardGroup("group", 0f, 0f, 800f, 600f, 0f, "Mixed", "group")),
            media = media,
            relations = listOf(ClipboardRelation("video", "text", "Forward", label = "Keep link")),
        )
    private val assets =
        mapOf("image" to "image/png", "gif" to "image/gif", "video" to "video/mp4")
            .map { (kind, mime) ->
                WorkspaceAsset(
                    "asset-$kind",
                    workspace,
                    "owner",
                    mime,
                    100,
                    "sha256:fixture",
                    320,
                    180,
                    null,
                    AssetStatus.Ready,
                    "2026-10-02",
                )
            }.associateBy { it.id }

    @Test
    fun mixedSelectionIsAcceptedOnlyWhenEveryMediaReferenceIsAvailable() {
        assertTrue(selectionMediaAvailable(payload, workspace, assets))
        for (node in media) {
            assertFalse(selectionMediaAvailable(payload, workspace, assets - node.assetId))
            for (status in AssetStatus.entries.filter { it != AssetStatus.Ready }) {
                assertFalse(
                    selectionMediaAvailable(
                        payload,
                        workspace,
                        assets + (node.assetId to assets.getValue(node.assetId).copy(status = status)),
                    ),
                )
            }
        }
        assertEquals(3, payload.media.size)
        assertEquals("Keep all", payload.nodes.single().text)
        assertEquals("Keep link", payload.relations.single().label)
    }

    @Test
    fun scopeTypeIdentityAndSizeMustMatchBeforeReuse() {
        val source = assets.getValue("asset-image")
        for (invalid in listOf(
            source.copy(workspaceId = WorkspaceId("source")),
            source.copy(mediaType = "video/mp4"),
            source.copy(id = "different"),
            source.copy(byteSize = MaxWorkspaceAssetBytes + 1),
        )) {
            assertFalse(selectionMediaAvailable(payload, workspace, assets + (source.id to invalid)))
        }
        assertFalse(selectionMediaAvailable(payload, null, assets))
    }

    @Test
    fun everyExplicitThumbnailMustAlsoBeAvailableBeforeAtomicReuse() {
        val withPoster = payload.copy(media = media.map { it.copy(thumbnailAssetId = "poster-${it.mediaKind}") })
        val posters =
            media.associate { node ->
                val id = "poster-${node.mediaKind}"
                id to assets.getValue("asset-image").copy(id = id)
            }
        val complete = assets + posters
        assertTrue(selectionMediaAvailable(withPoster, workspace, complete))
        for ((id, poster) in posters) {
            assertFalse(selectionMediaAvailable(withPoster, workspace, complete - id))
            for (invalid in listOf(
                poster.copy(id = "unrelated"),
                poster.copy(workspaceId = WorkspaceId("foreign")),
                poster.copy(mediaType = "video/mp4"),
                poster.copy(mediaType = "application/octet-stream"),
                poster.copy(byteSize = MaxWorkspaceAssetBytes + 1),
            ) + AssetStatus.entries.filter { it != AssetStatus.Ready }.map { poster.copy(status = it) }) {
                assertFalse(selectionMediaAvailable(withPoster, workspace, complete + (id to invalid)))
            }
        }
        // No implicit dependency on a metadata derivative not carried by the Node.
        assertTrue(
            selectionMediaAvailable(
                payload,
                workspace,
                assets.mapValues { (_, asset) -> asset.copy(thumbnailAssetId = "later-derivative") },
            ),
        )
    }

    @Test
    fun textAndGroupSelectionsDoNotRequireAssetMetadataOrSession() {
        assertTrue(selectionMediaAvailable(payload.copy(media = emptyList(), relations = emptyList()), null, emptyMap()))
    }
}
