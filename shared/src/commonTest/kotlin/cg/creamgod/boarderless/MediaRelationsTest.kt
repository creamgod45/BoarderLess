package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaRelationsTest {
    private fun media(
        kind: MediaKind,
        x: Float = 0f,
    ) = MediaNode(
        id = CanvasObjectId(kind.token),
        assetId = "asset-${kind.token}",
        mediaKind = kind,
        transform = CanvasTransform(Vec2(x, 0f), CanvasSize(100f, 80f)),
        altText = "My ${kind.token}",
    )

    @Test fun everyMediaKindCanBeATargetAndCannotTargetItself() {
        for (kind in MediaKind.entries) {
            val source = media(kind, -200f)
            val target = media(MediaKind.Image).copy(id = CanvasObjectId("target"), zIndex = 10)
            val group = GroupFrame(CanvasObjectId("group"), zIndex = 100, transform = target.transform)
            assertEquals(target.id, connectorDropTargetId(listOf(source, target, group), source.id, Vec2(50f, 40f)))
            assertEquals(null, connectorDropTargetId(listOf(source), source.id, Vec2(-150f, 40f)))
        }
    }

    @Test fun mediaRoutesUseRectangleEdgesInBothDirections() {
        val source = media(MediaKind.Video)
        val target = TextNode(CanvasObjectId("text"), transform = CanvasTransform(Vec2(300f, 0f), CanvasSize(100f, 80f)), text = "Note")
        val route = orthogonalRelationRoute(source, source.transform, target, target.transform)
        assertEquals(100f, route.first().x, 0.00001f)
        assertEquals(300f, route.last().x, 0.00001f)
        val reverse = orthogonalRelationRoute(target, target.transform, source, source.transform)
        assertEquals(100f, reverse.last().x, 0.00001f)
        assertTrue(route.zipWithNext().all { (a, b) -> a.x == b.x || a.y == b.y })
    }

    @Test fun paletteIncludesMediaWithoutLeakingAssetIdentifiers() {
        val source = media(MediaKind.Video)
        val target = media(MediaKind.Gif, 300f)
        val entries = connectionTargetPaletteEntries(source.id, listOf(source, target), true, "")
        assertEquals(listOf("connect-target:gif"), entries.map { it.id })
        assertTrue(entries.single().title.contains("My gif"))
        assertTrue(!entries.single().keywords.contains(target.assetId))
    }
}
