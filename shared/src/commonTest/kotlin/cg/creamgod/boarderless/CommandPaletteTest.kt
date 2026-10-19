package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.ComponentLibraryEntry
import cg.creamgod.boarderless.feature.canvas.PaletteEntry
import cg.creamgod.boarderless.feature.canvas.connectionTargetPaletteEntries
import cg.creamgod.boarderless.feature.canvas.diagramShapePaletteEntries
import cg.creamgod.boarderless.feature.canvas.diagramTemplatePaletteEntries
import cg.creamgod.boarderless.feature.canvas.filterPaletteEntries
import cg.creamgod.boarderless.feature.canvas.groupNavigationPaletteEntries
import cg.creamgod.boarderless.feature.canvas.relationNavigationPaletteEntries
import cg.creamgod.boarderless.feature.canvas.relationPaletteEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandPaletteTest {
    private val entries =
        listOf(
            PaletteEntry("new", "New thought", "Create a node", "add capture"),
            PaletteEntry("group", "Group selection", "Create a frame", "organize"),
            PaletteEntry("node:a", "Research direction", "Jump to thought", "content node"),
        )

    @Test
    fun emptyQueryKeepsOriginalOrder() {
        assertEquals(entries, filterPaletteEntries(entries, "   "))
    }

    @Test
    fun matchesEveryQueryTokenAcrossMetadata() {
        assertEquals(
            listOf("node:a"),
            filterPaletteEntries(entries, "research node").map(PaletteEntry::id),
        )
    }

    @Test
    fun titlePrefixRanksBeforeOtherMatches() {
        val matches =
            filterPaletteEntries(
                entries + PaletteEntry("other", "Capture later", "New note", "thought"),
                "new",
            )
        assertEquals(listOf("new", "other"), matches.map(PaletteEntry::id))
    }

    @Test
    fun diagramShapeCommandsCoverEveryNonDefaultShapeAndRemainSearchable() {
        val shapes = diagramShapePaletteEntries(enabled = true, disabledReason = "")

        assertEquals(NodeShape.entries.size - 1, shapes.size)
        assertEquals(
            NodeShape.entries.filterNot { it == NodeShape.RoundedRectangle }.map { "new-shape:${it.token}" },
            shapes.map(PaletteEntry::id),
        )
        assertEquals(
            listOf("new-shape:diamond"),
            filterPaletteEntries(shapes, "decision").map(PaletteEntry::id),
        )
        assertTrue(shapes.all(PaletteEntry::enabled))
    }

    @Test
    fun diagramTemplateCommandsExposeEveryStarterWithoutIncludingOrdinaryComponents() {
        val componentIds =
            listOf(
                "thought",
                "flowchart-starter",
                "swimlane-starter",
                "org-chart-starter",
                "architecture-starter",
                "relationship-map",
                "topology-starter",
                "tree-starter",
            )
        val components =
            componentIds.map { id ->
                ComponentLibraryEntry(
                    id = id,
                    title = id.replace('-', ' '),
                    description = "Editable diagram component",
                    colorToken = "paper",
                )
            }

        val templates =
            diagramTemplatePaletteEntries(
                components = components,
                enabled = true,
                disabledReason = "",
            )

        assertEquals(
            componentIds.drop(1).map { "insert-component:$it" },
            templates.map(PaletteEntry::id),
        )
        assertEquals(
            listOf("insert-component:topology-starter"),
            filterPaletteEntries(templates, "topology-starter").map(PaletteEntry::id),
        )
        assertTrue(templates.all(PaletteEntry::enabled))
    }

    @Test
    fun relationCommandsExposeEverySupportedIntentAndRespectAvailability() {
        val enabled = relationPaletteEntries(enabled = true, disabledReason = "")
        val disabled = relationPaletteEntries(enabled = false, disabledReason = "Select two thoughts")

        assertEquals(
            listOf("connect-relates", "connect-supports", "connect-conflicts"),
            enabled.map(PaletteEntry::id),
        )
        assertEquals(
            enabled.mapTo(mutableSetOf(), PaletteEntry::id),
            filterPaletteEntries(enabled, "connect relation").mapTo(mutableSetOf(), PaletteEntry::id),
        )
        assertTrue(enabled.all(PaletteEntry::enabled))
        assertTrue(disabled.none(PaletteEntry::enabled))
        assertTrue(disabled.all { it.disabledReason == "Select two thoughts" })
    }

    @Test
    fun connectionTargetCommandsExcludeSourceAndSearchTargetText() {
        val sourceId = CanvasObjectId("source")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(120f, 72f))
        val nodes =
            listOf(
                TextNode(sourceId, transform = transform, text = "Source"),
                TextNode(CanvasObjectId("research"), transform = transform, text = "Research direction", zIndex = 2),
                TextNode(CanvasObjectId("evidence"), transform = transform, text = "Supporting evidence", zIndex = 1),
            )

        val targets =
            connectionTargetPaletteEntries(
                sourceId = sourceId,
                nodes = nodes,
                enabled = true,
                disabledReason = "",
            )

        assertEquals(listOf("connect-target:research", "connect-target:evidence"), targets.map(PaletteEntry::id))
        assertEquals(
            listOf("connect-target:research"),
            filterPaletteEntries(targets, "connect research").map(PaletteEntry::id),
        )
        assertTrue(targets.none { it.id == "connect-target:source" })
    }

    @Test
    fun relationNavigationCommandsSearchEndpointsIntentAndLabel() {
        val sourceId = CanvasObjectId("source")
        val targetId = CanvasObjectId("target")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(120f, 72f))
        val nodes =
            listOf(
                TextNode(sourceId, transform = transform, text = "Research direction"),
                TextNode(targetId, transform = transform, text = "Supporting evidence"),
            ).associateBy { it.id }
        val relation =
            Relation(
                id = RelationId("relation"),
                sourceObjectId = sourceId,
                targetObjectId = targetId,
                direction = RelationDirection.Forward,
                intent = "supports",
                label = "Primary source",
            )

        val entries = relationNavigationPaletteEntries(listOf(relation), nodes)

        assertEquals(listOf("find-relation:relation"), entries.map(PaletteEntry::id))
        assertEquals(
            listOf("find-relation:relation"),
            filterPaletteEntries(entries, "evidence supports").map(PaletteEntry::id),
        )
        assertEquals(
            listOf("find-relation:relation"),
            filterPaletteEntries(entries, "primary source").map(PaletteEntry::id),
        )
    }

    @Test
    fun groupNavigationCommandsSearchTitlesAndExcludeNoGroups() {
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 160f))
        val groups =
            listOf(
                GroupFrame(CanvasObjectId("lane"), transform = transform, title = "Design swimlane", zIndex = 4),
                GroupFrame(CanvasObjectId("archive"), transform = transform, title = "Archived ideas", zIndex = 1),
            )

        val entries = groupNavigationPaletteEntries(groups)

        assertEquals(listOf("find-group:lane", "find-group:archive"), entries.map(PaletteEntry::id))
        assertEquals(
            listOf("find-group:lane"),
            filterPaletteEntries(entries, "design group").map(PaletteEntry::id),
        )
        assertTrue(groupNavigationPaletteEntries(emptyList()).isEmpty())
    }
}
