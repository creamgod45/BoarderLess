package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.readCanvasCapabilities
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.nodeShapes
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.ComponentLibraryCategory
import cg.creamgod.boarderless.feature.canvas.ComponentLibraryEntry
import cg.creamgod.boarderless.feature.canvas.art
import cg.creamgod.boarderless.feature.canvas.containsLocalPoint
import cg.creamgod.boarderless.feature.canvas.containsWorldPoint
import cg.creamgod.boarderless.feature.canvas.nodeShapeKeywords
import cg.creamgod.boarderless.feature.canvas.nodeShapeLabel
import cg.creamgod.boarderless.feature.canvas.offersTextEditing
import cg.creamgod.boarderless.feature.canvas.polygonVertices
import cg.creamgod.boarderless.feature.canvas.shapeBoundaryWorldPoint
import cg.creamgod.boarderless.feature.canvas.shapeConnectionOriginWorldPoint
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpansionShapesTest {
    private val expansion = NodeShape.entries.drop(25)
    private val sizes = listOf(CanvasSize(200f, 200f), CanvasSize(240f, 120f), CanvasSize(120f, 240f))

    private fun transform(size: CanvasSize) = CanvasTransform(Vec2(1000f, -500f), size)
    private fun worldPoint(size: CanvasSize, x: Float, y: Float) = Vec2(1000f + x * size.width, -500f + y * size.height)
    private fun inside(shape: NodeShape, size: CanvasSize, x: Float, y: Float) =
        transform(size).containsWorldPoint(worldPoint(size, x, y), shape)

    @Test fun tokensAreUniqueKebabCaseAndTheOriginal25KeepTheirPlaceAndOrder() {
        val tokens = NodeShape.entries.map { it.token }
        assertEquals(49, tokens.size)
        assertEquals(tokens.size, tokens.toSet().size)
        tokens.forEach { assertTrue(Regex("[a-z]+(-[a-z]+)*").matches(it), it) }
        assertEquals(
            listOf(
                "rounded", "rectangle", "ellipse", "diamond", "pill", "parallelogram", "hexagon", "document", "database",
                "plain-text", "triangle", "pentagon", "octagon", "trapezoid", "plus", "arrow-right", "arrow-left", "arrow-up",
                "arrow-down", "triangle-down", "right-triangle", "chevron", "double-arrow", "star", "manual-input",
            ),
            tokens.take(25),
        )
        NodeShape.entries.forEach { assertEquals(it, NodeShape.fromToken(it.token)) }
        assertEquals(NodeShape.entries.take(25).toSet(), NodeShape.original)
    }

    @Test fun everyExpansionShapeHasArtInsideTheNodeBoundsAndALabelWithKeywords() {
        assertEquals(24, expansion.size)
        for (shape in expansion) {
            val art = assertNotNull(shape.art(), shape.token)
            val outlines = listOfNotNull(art.body) + art.holes + art.shaded + art.lines
            assertTrue(outlines.isNotEmpty(), shape.token)
            outlines.flatten().forEach {
                assertTrue(it.x in -0.001f..1.001f && it.y in -0.001f..1.001f, "${shape.token} point $it outside the node")
            }
            assertTrue(nodeShapeLabel(shape).isNotBlank(), shape.token)
            assertTrue(nodeShapeKeywords(shape).split(' ').size >= 3, shape.token)
        }
    }

    @Test fun theTextAreaLiesEntirelyOnTheVisibleOutline() {
        for (shape in expansion) {
            val rect = shape.art()!!.textRect ?: continue
            for (size in sizes) {
                val probes = listOf(
                    rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom,
                    (rect.left + rect.right) / 2 to (rect.top + rect.bottom) / 2,
                )
                for ((x, y) in probes) assertTrue(inside(shape, size, x, y), "${shape.token} text probe ($x,$y) $size")
            }
            assertTrue(shape.offersTextEditing, shape.token)
        }
        for (shape in listOf(NodeShape.Ring, NodeShape.Lightning, NodeShape.OrJunction, NodeShape.SummingJunction, NodeShape.BraceLeft, NodeShape.BraceRight, NodeShape.Person)) {
            assertNull(shape.art()!!.textRect, shape.token)
            assertFalse(shape.offersTextEditing, shape.token)
        }
    }

    @Test fun transparentAreasAreNeverHit() {
        val size = CanvasSize(200f, 200f)
        assertFalse(inside(NodeShape.Ring, size, .5f, .5f), "NodeShape.Ring .5f, .5f") // the hole
        assertTrue(inside(NodeShape.Ring, size, .5f, .1f)) // the band
        assertFalse(inside(NodeShape.Crescent, size, .75f, .5f), "NodeShape.Crescent .75f, .5f") // carved out
        assertFalse(inside(NodeShape.Crescent, size, .5f, .5f), "NodeShape.Crescent .5f, .5f")
        assertTrue(inside(NodeShape.Crescent, size, .16f, .5f))
        assertFalse(inside(NodeShape.Heart, size, .05f, .05f), "NodeShape.Heart .05f, .05f")
        assertFalse(inside(NodeShape.Cloud, size, .5f, .02f), "NodeShape.Cloud .5f, .02f") // above the cloud, inside the box
        assertFalse(inside(NodeShape.CalloutRound, size, .85f, .95f), "NodeShape.CalloutRound .85f, .95f") // below the body, away from the tail
        assertFalse(inside(NodeShape.Folder, size, .8f, .05f), "NodeShape.Folder .8f, .05f") // right of the folder tab
        assertFalse(inside(NodeShape.Lightning, size, .05f, .05f), "NodeShape.Lightning .05f, .05f")
        assertFalse(inside(NodeShape.Semicircle, size, .05f, .3f), "NodeShape.Semicircle .05f, .3f")
    }

    @Test fun braceIsHitOnItsStrokeNotItsBoundingBox() {
        val size = CanvasSize(40f, 160f)
        for (shape in listOf(NodeShape.BraceLeft, NodeShape.BraceRight)) {
            val line = shape.art()!!.lines.single()
            val onStroke = line[line.size / 3]
            assertTrue(inside(shape, size, onStroke.x, onStroke.y), shape.token)
            val far = if (shape == NodeShape.BraceLeft) .12f else .88f
            assertFalse(inside(shape, size, far, .3f), "${shape.token} bounding-box point must not hit")
            assertNull(shape.polygonVertices())
        }
    }

    @Test fun relationBoundariesLandOnTheVisibleOutlineForEveryAspectRatio() {
        for (shape in expansion) {
            for (size in sizes) {
                val box = transform(size)
                val origin = shapeConnectionOriginWorldPoint(box, shape)
                // The origin itself is a visible point (stroke-only shapes connect at their tip).
                val directions = listOf(Vec2(1f, 0f), Vec2(-1f, 0f), Vec2(0f, 1f), Vec2(0f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f))
                for (direction in directions) {
                    val boundary = shapeBoundaryWorldPoint(box, shape, origin + direction * 1000f)
                    if (shape.art()!!.strokeOnly) {
                        assertEquals(origin, boundary, "${shape.token} connects at its tip")
                        continue
                    }
                    val length = hypot(boundary.x - origin.x, boundary.y - origin.y)
                    // Just inside the reported boundary is on the shape, just outside is not.
                    val unit = (boundary - origin) / maxOf(length, 0.001f)
                    assertTrue(box.containsWorldPoint(boundary - unit * 3f, shape) || length < 6f, "${shape.token} $size inside $direction")
                    assertFalse(box.containsWorldPoint(boundary + unit * 3f, shape), "${shape.token} $size outside $direction")
                }
            }
        }
    }

    @Test fun connectionOriginIsAVisiblePoint() {
        for (shape in expansion) {
            val art = shape.art()!!
            for (size in sizes) {
                val box = transform(size)
                val origin = shapeConnectionOriginWorldPoint(box, shape)
                if (shape == NodeShape.Ring) continue // Centre is the hole by design; relations use the outer circle.
                if (art.strokeOnly) {
                    assertTrue(box.containsWorldPoint(origin, shape), "${shape.token} tip must be on the stroke")
                } else {
                    assertTrue(box.containsWorldPoint(origin, shape), "${shape.token} origin $origin must be on the shape")
                }
            }
        }
    }

    @Test fun cropToTheRingWithoutTheHoleIsNeverNeeded() {
        // The ring connects on its outer circle even though its centre is empty.
        val box = transform(CanvasSize(200f, 200f))
        val origin = shapeConnectionOriginWorldPoint(box, NodeShape.Ring)
        val boundary = shapeBoundaryWorldPoint(box, NodeShape.Ring, origin + Vec2(1000f, 0f))
        assertEquals(1000f + 200f, boundary.x, 1.5f)
    }

    @Test fun categoriesKeepTheLibraryOrganized() {
        fun categories(shape: NodeShape) = ComponentLibraryEntry("${shape.token}-node", shape.token, "d", "paper", shape).categories
        for (shape in listOf(NodeShape.PredefinedProcess, NodeShape.Delay, NodeShape.MultiDocument)) {
            assertTrue(ComponentLibraryCategory.Flow in categories(shape), shape.token)
        }
        for (shape in listOf(NodeShape.CalloutRound, NodeShape.Note, NodeShape.BraceLeft)) {
            assertTrue(ComponentLibraryCategory.Annotations in categories(shape), shape.token)
        }
        for (shape in listOf(NodeShape.Folder, NodeShape.Cloud, NodeShape.Server, NodeShape.Person)) {
            assertTrue(ComponentLibraryCategory.Containers in categories(shape) && ComponentLibraryCategory.Diagrams in categories(shape), shape.token)
        }
        assertEquals(ComponentLibraryCategory.Annotations, ComponentLibraryCategory.fromToken("annotations"))
    }

    @Test fun capabilitiesAnnounceTheAcceptedShapesAndAMissingListMeansTheOriginal25() {
        val withShapes = buildJsonObject {
            put("nodeShapes", buildJsonArray { add("rounded"); add("cloud"); add("future-shape") })
        }
        assertEquals(setOf("rounded", "cloud", "future-shape"), readCanvasCapabilities(withShapes).nodeShapes)
        assertTrue(readCanvasCapabilities(buildJsonObject { }).nodeShapes.isEmpty())
    }

    @Test fun draftsAreGatedByTheShapesAnOperationWouldWrite() {
        fun node(shape: NodeShape) = TextNode(CanvasObjectId("n"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 100f)), text = "x", shape = shape)
        assertEquals(listOf(NodeShape.Cloud), CreateObjectsOperation("c", listOf(node(NodeShape.Cloud))).nodeShapes())
    }
}
