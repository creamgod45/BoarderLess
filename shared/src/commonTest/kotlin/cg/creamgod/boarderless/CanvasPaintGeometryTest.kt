package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class CanvasPaintGeometryTest {
    @Test fun outsideLabelRotatesAroundOwnerRatherThanItsOwnCentre() {
        val transform = CanvasTransform(Vec2(100f, 200f), CanvasSize(100f, 50f), 90f)
        val bounds = objectLocalPaintBounds(transform, CanvasPaintBounds(-50f, -20f, 150f, 0f))
        assertEquals(175f, bounds.left, .01f); assertEquals(125f, bounds.top, .01f)
        assertEquals(195f, bounds.right, .01f); assertEquals(325f, bounds.bottom, .01f)
    }
    @Test fun bezierControlHullAndStrokeOutsideViewboxAreIncluded() {
        val transform = CanvasTransform(Vec2(100f, 200f), CanvasSize(200f, 100f))
        val path = VectorPath(CanvasSize(100f, 100f), listOf(VectorPathCommand.Move(Vec2(-10f, 10f)),
            VectorPathCommand.Cubic(Vec2(-30f, -40f), Vec2(140f, 130f), Vec2(110f, 90f))),
            VectorPathStyle(fillColorToken = null, strokeColorToken = "#000000", strokeWidth = 10f))
        val bounds = vectorPaintBounds(transform, path)
        assertEquals(CanvasPaintBounds(30f, 150f, 390f, 340f), bounds)
        val plan = canvasExportLayout(listOf(bounds), margin = 0f)
        assertEquals(360, plan.pixelWidth); assertEquals(190, plan.pixelHeight)
    }
}
