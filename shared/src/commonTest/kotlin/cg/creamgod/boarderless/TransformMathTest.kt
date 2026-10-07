package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.resizedTransform
import cg.creamgod.boarderless.feature.canvas.rotationDegreesForPointer
import cg.creamgod.boarderless.feature.canvas.calculateAlignmentSnap
import cg.creamgod.boarderless.feature.canvas.descendantObjectIds
import cg.creamgod.boarderless.feature.canvas.rotateSelectionTransforms
import cg.creamgod.boarderless.feature.canvas.scaleSelectionTransforms
import cg.creamgod.boarderless.feature.canvas.gridSnappedSelectionDelta
import cg.creamgod.boarderless.feature.canvas.TransformInspectorValues
import cg.creamgod.boarderless.feature.canvas.formatInspectorNumber
import cg.creamgod.boarderless.feature.canvas.toCanvasTransform
import cg.creamgod.boarderless.feature.canvas.toInspectorValues
import cg.creamgod.boarderless.feature.canvas.accessibilityResizedTransform
import cg.creamgod.boarderless.feature.canvas.accessibilityRotatedTransform
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransformMathTest {
    @Test
    fun accessibilityActionsResizeAndRotateInPredictableSteps() {
        val transform = CanvasTransform(
            position = Vec2(40f, 20f),
            size = CanvasSize(120f, 72f),
            rotationDegrees = 350f,
        )

        assertEquals(
            CanvasSize(152f, 104f),
            accessibilityResizedTransform(transform, step = 32f).size,
        )
        assertEquals(
            5f,
            accessibilityRotatedTransform(transform).rotationDegrees,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun accessibilityTransformActionsIgnoreInvalidSteps() {
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(120f, 72f), rotationDegrees = 20f)

        assertEquals(transform, accessibilityResizedTransform(transform, step = Float.NaN))
        assertEquals(transform, accessibilityResizedTransform(transform, step = 0f))
        assertEquals(transform, accessibilityRotatedTransform(transform, degrees = Float.POSITIVE_INFINITY))
    }

    @Test
    fun inspectorUsesLogicalUnitsAndNormalizesRotation() {
        val transform = TransformInspectorValues(
            x = -12.5f,
            y = 24f,
            width = 80f,
            height = 20f,
            rotationDegrees = 390f,
        ).toCanvasTransform(
            unitScale = 2f,
            minimumSize = CanvasSize(120f, 72f),
        )!!

        assertEquals(Vec2(-25f, 48f), transform.position)
        assertEquals(CanvasSize(160f, 72f), transform.size)
        assertEquals(30f, transform.rotationDegrees)
        assertEquals(
            TransformInspectorValues(-12.5f, 24f, 80f, 36f, 30f),
            transform.toInspectorValues(2f),
        )
    }

    @Test
    fun inspectorRejectsInvalidOrDangerouslyLargeValues() {
        assertNull(
            TransformInspectorValues(0f, 0f, -1f, 20f, 0f)
                .toCanvasTransform(1f, CanvasSize(120f, 72f)),
        )
        assertNull(
            TransformInspectorValues(1_000_001f, 0f, 120f, 72f, 0f)
                .toCanvasTransform(1f, CanvasSize(120f, 72f)),
        )
    }

    @Test
    fun inspectorNumberFormattingKeepsUsefulPrecision() {
        assertEquals("42", formatInspectorNumber(42.001f))
        assertEquals("-12.35", formatInspectorNumber(-12.346f))
    }

    @Test
    fun resizeConvertsScreenDeltaIntoRotatedLocalSpace() {
        val transform = CanvasTransform(
            position = Vec2.Zero,
            size = CanvasSize(200f, 100f),
            rotationDegrees = 90f,
        )

        val resized = resizedTransform(
            transform = transform,
            screenDelta = Vec2(0f, 100f),
            zoom = 2f,
            minimumSize = CanvasSize(120f, 72f),
        )

        assertEquals(250f, resized.size.width, absoluteTolerance = 0.001f)
        assertEquals(100f, resized.size.height, absoluteTolerance = 0.001f)
    }

    @Test
    fun resizeHonorsMinimumSize() {
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(200f, 100f))
        val resized = resizedTransform(
            transform = transform,
            screenDelta = Vec2(-500f, -500f),
            zoom = 1f,
            minimumSize = CanvasSize(120f, 72f),
        )

        assertEquals(CanvasSize(120f, 72f), resized.size)
    }

    @Test
    fun pointerPositionsMapToClockwiseRotationDegrees() {
        val center = Vec2(100f, 100f)

        assertEquals(0f, rotationDegreesForPointer(center, Vec2(100f, 0f)), 0.001f)
        assertEquals(90f, rotationDegreesForPointer(center, Vec2(200f, 100f)), 0.001f)
        assertEquals(180f, rotationDegreesForPointer(center, Vec2(100f, 200f)), 0.001f)
        assertEquals(270f, rotationDegreesForPointer(center, Vec2(0f, 100f)), 0.001f)
    }

    @Test
    fun commonScaleChangesSizesAndSpacingAroundTheSelectionCenter() {
        val firstId = CanvasObjectId("first")
        val secondId = CanvasObjectId("second")
        val transforms = mapOf(
            firstId to CanvasTransform(Vec2(0f, 0f), CanvasSize(100f, 100f)),
            secondId to CanvasTransform(Vec2(300f, 0f), CanvasSize(100f, 100f)),
        )

        val scaled = scaleSelectionTransforms(
            transforms = transforms,
            factor = 0.5f,
            minimumSize = CanvasSize(20f, 20f),
        )

        assertEquals(CanvasTransform(Vec2(100f, 25f), CanvasSize(50f, 50f)), scaled.getValue(firstId))
        assertEquals(CanvasTransform(Vec2(250f, 25f), CanvasSize(50f, 50f)), scaled.getValue(secondId))
    }

    @Test
    fun commonRotationMovesCentersAndRotatesEveryObjectAsOneSelection() {
        val firstId = CanvasObjectId("first")
        val secondId = CanvasObjectId("second")
        val transforms = mapOf(
            firstId to CanvasTransform(Vec2(0f, 0f), CanvasSize(100f, 100f)),
            secondId to CanvasTransform(Vec2(200f, 0f), CanvasSize(100f, 100f)),
        )

        val rotated = rotateSelectionTransforms(transforms, 90f)

        val first = rotated.getValue(firstId)
        val second = rotated.getValue(secondId)
        assertEquals(100f, first.position.x, 0.001f)
        assertEquals(-100f, first.position.y, 0.001f)
        assertEquals(90f, first.rotationDegrees, 0.001f)
        assertEquals(100f, second.position.x, 0.001f)
        assertEquals(100f, second.position.y, 0.001f)
        assertEquals(90f, second.rotationDegrees, 0.001f)
    }

    @Test
    fun commonRotationNormalizesEachObjectAngle() {
        val objectId = CanvasObjectId("node")
        val original = CanvasTransform(
            position = Vec2(40f, 20f),
            size = CanvasSize(120f, 72f),
            rotationDegrees = 350f,
        )

        val rotated = rotateSelectionTransforms(mapOf(objectId to original), 30f)

        assertEquals(20f, rotated.getValue(objectId).rotationDegrees, 0.001f)
    }

    @Test
    fun commonScaleHonorsMinimumNodeSize() {
        val objectId = CanvasObjectId("node")
        val original = CanvasTransform(Vec2(40f, 20f), CanvasSize(120f, 72f))

        val scaled = scaleSelectionTransforms(
            transforms = mapOf(objectId to original),
            factor = 0.5f,
            minimumSize = CanvasSize(120f, 72f),
        )

        assertEquals(original, scaled.getValue(objectId))
    }

    @Test
    fun alignmentSnapUsesNearestEdgesAndCenters() {
        val moving = CanvasTransform(Vec2(10f, 10f), CanvasSize(100f, 80f))
        val target = CanvasTransform(Vec2(200f, 150f), CanvasSize(100f, 80f))

        val snapped = calculateAlignmentSnap(
            movingTransform = moving,
            otherTransforms = listOf(target),
            rawDelta = Vec2(88f, 61f),
            threshold = 4f,
        )

        assertEquals(Vec2(90f, 60f), snapped.delta)
        assertEquals(200f, snapped.verticalWorldX)
        assertEquals(150f, snapped.horizontalWorldY)
    }

    @Test
    fun alignmentSnapLeavesDistantMovementFree() {
        val moving = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f))
        val target = CanvasTransform(Vec2(500f, 500f), CanvasSize(100f, 80f))
        val raw = Vec2(40f, 30f)

        val snapped = calculateAlignmentSnap(moving, listOf(target), raw, threshold = 8f)

        assertEquals(raw, snapped.delta)
        assertEquals(null, snapped.verticalWorldX)
        assertEquals(null, snapped.horizontalWorldY)
    }

    @Test
    fun gridSnapCalculatesOneDeltaFromTheDragAnchor() {
        assertEquals(
            Vec2(27f, 25f),
            gridSnappedSelectionDelta(
                anchorPosition = Vec2(5f, 7f),
                rawDelta = Vec2(20f, 11f),
                gridSize = 32f,
            ),
        )
    }

    @Test
    fun sharedGridDeltaPreservesRelativePositions() {
        val first = Vec2(5f, 7f)
        val second = Vec2(22f, 19f)
        val originalDifference = second - first
        val sharedDelta = gridSnappedSelectionDelta(first, Vec2(20f, 11f), 32f)

        assertEquals(originalDifference, (second + sharedDelta) - (first + sharedDelta))
        assertEquals(Vec2(32f, 32f), first + sharedDelta)
        assertEquals(Vec2(49f, 44f), second + sharedDelta)
    }

    @Test
    fun descendantLookupIncludesNestedGroupsAndNodes() {
        val outerId = CanvasObjectId("outer")
        val innerId = CanvasObjectId("inner")
        val nodeId = CanvasObjectId("node")
        val outer = GroupFrame(outerId, transform = CanvasTransform(Vec2.Zero, CanvasSize(400f, 300f)))
        val inner = GroupFrame(
            innerId,
            parentId = outerId,
            transform = CanvasTransform(Vec2(20f, 20f), CanvasSize(300f, 200f)),
        )
        val node = TextNode(
            nodeId,
            parentId = innerId,
            transform = CanvasTransform(Vec2(40f, 40f), CanvasSize(100f, 80f)),
            text = "Nested",
        )
        val workspace = Workspace(
            id = WorkspaceId("workspace"),
            title = "Nested",
            objects = listOf(outer, inner, node).associateBy { it.id },
        )

        assertEquals(setOf(innerId, nodeId), descendantObjectIds(workspace, setOf(outerId)))
    }
}
