package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.applyMarqueeSelection
import cg.creamgod.boarderless.feature.canvas.marqueeIntersectsTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarqueeSelectionTest {
    @Test
    fun marqueeHitsTheVisiblePartOfARotatedNode() {
        val transform = CanvasTransform(
            position = Vec2(100f, 100f),
            size = CanvasSize(200f, 100f),
            rotationDegrees = 90f,
        )

        assertTrue(marqueeIntersectsTransform(transform, Vec2(160f, 60f), Vec2(170f, 70f)))
    }

    @Test
    fun marqueeRejectsEmptyCornersOfTheRotatedBoundingBox() {
        val transform = CanvasTransform(
            position = Vec2.Zero,
            size = CanvasSize(100f, 100f),
            rotationDegrees = 45f,
        )

        assertFalse(marqueeIntersectsTransform(transform, Vec2(-20f, -20f), Vec2(-10f, -10f)))
    }

    @Test
    fun marqueeWorksInEitherDragDirection() {
        val transform = CanvasTransform(Vec2(20f, 20f), CanvasSize(100f, 80f))

        assertTrue(marqueeIntersectsTransform(transform, Vec2(140f, 120f), Vec2(80f, 60f)))
    }

    @Test
    fun areaModeReplacesWhileShiftSelectionAdds() {
        val existing = setOf(CanvasObjectId("existing"))
        val hit = setOf(CanvasObjectId("hit"))

        assertEquals(hit, applyMarqueeSelection(existing, hit, additive = false))
        assertEquals(existing + hit, applyMarqueeSelection(existing, hit, additive = true))
    }
}
