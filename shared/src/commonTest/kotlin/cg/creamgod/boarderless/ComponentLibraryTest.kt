package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.ComponentLibraryCategory
import cg.creamgod.boarderless.feature.canvas.ComponentLibraryEntry
import cg.creamgod.boarderless.feature.canvas.filterComponentLibraryEntries
import cg.creamgod.boarderless.feature.canvas.isLibraryDropOnCanvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComponentLibraryTest {
    private val canvasSize = CanvasSize(1200f, 800f)

    @Test fun additionalOwnedShapesAreCategorizedWithoutInventingRelationObjects() {
        for (shape in listOf(NodeShape.Chevron, NodeShape.DoubleArrow)) {
            val entry = ComponentLibraryEntry(shape.token, shape.token, "Owned", "paper", shape)
            assertEquals(setOf(ComponentLibraryCategory.Geometry, ComponentLibraryCategory.Arrows), entry.categories)
        }
        assertEquals(
            setOf(ComponentLibraryCategory.Geometry, ComponentLibraryCategory.Flow),
            ComponentLibraryEntry("manual-input-node", "Input", "Owned", "paper", NodeShape.ManualInput).categories,
        )
        for (shape in listOf(NodeShape.TriangleDown, NodeShape.RightTriangle, NodeShape.Star)) {
            assertEquals(
                setOf(ComponentLibraryCategory.Geometry),
                ComponentLibraryEntry(shape.token, shape.token, "Owned", "paper", shape).categories,
            )
        }
    }

    @Test
    fun acceptsDropInsideUsableCanvas() {
        assertTrue(isLibraryDropOnCanvas(Vec2(600f, 400f), canvasSize, density = 1f))
    }

    @Test
    fun rejectsDropOverLibraryToolbarOrStatus() {
        assertFalse(isLibraryDropOnCanvas(Vec2(200f, 400f), canvasSize, density = 1f))
        assertFalse(isLibraryDropOnCanvas(Vec2(600f, 80f), canvasSize, density = 1f))
        assertFalse(isLibraryDropOnCanvas(Vec2(600f, 760f), canvasSize, density = 1f))
    }

    @Test
    fun respectsDisplayDensityForShellExclusionZones() {
        assertFalse(isLibraryDropOnCanvas(Vec2(500f, 300f), canvasSize, density = 2f))
        assertTrue(isLibraryDropOnCanvas(Vec2(700f, 300f), canvasSize, density = 2f))
    }

    @Test
    fun searchRequiresEveryTokenAcrossComponentTitleAndDescription() {
        val entries =
            listOf(
                ComponentLibraryEntry("process", "Process node", "Standard flowchart action", "lilac", NodeShape.Rectangle),
                ComponentLibraryEntry("database", "Database node", "Persistent architecture storage", "amber", NodeShape.Database),
                ComponentLibraryEntry("tree", "Tree diagram", "Root and branches", "mint", NodeShape.Pill),
            )

        assertEquals(listOf("database"), filterComponentLibraryEntries(entries, "ARCHITECTURE database").map { it.id })
        assertEquals(entries, filterComponentLibraryEntries(entries, "  "))
        assertTrue(filterComponentLibraryEntries(entries, "missing").isEmpty())
    }
}
