package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class SequenceSceneGeometryTest {
    private val draft = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse(sequenceCreationExample)).draft

    @Test fun measuredRowsAndBranchBandsExpandWithoutChangingSemanticIdentitiesOrDroppingText() {
        val original = sequenceDiagramLayout(draft)
        val id = draft.messages().first().id
        val block = original.blocks.first()
        val larger = sequenceDiagramLayout(draft, mapOf(id to 4), 96f, mapOf((block.blockId to 0) to 3))
        assertEquals(original.messages.map { it.message }, larger.messages.map { it.message })
        assertEquals(original.blocks.map { it.blockId }.toSet(), larger.blocks.map { it.blockId }.toSet())
        assertEquals(
            original.messages
                .first()
                .points
                .first()
                .y + 40f + 192f,
            larger.messages
                .first()
                .points
                .first()
                .y,
        )
        assertTrue(larger.size.height > original.size.height + 192f)
        assertEquals(
            124f,
            larger.participants
                .first()
                .lifelineStart.y,
        )
        assertTrue(larger.messages.zipWithNext().all { (a, b) -> a.points.last().y < b.points.first().y })
        assertFails { sequenceDiagramLayout(draft, mapOf(id to 0)) }
        assertFails { sequenceDiagramLayout(draft, headerHeight = Float.NaN) }
    }

    @Test fun wholeSceneProjectionUsesContainerScaleRotationAndViewportRatherThanChildPositions() {
        val size = CanvasSize(200f, 100f)
        val transform = CanvasTransform(Vec2(300f, 400f), CanvasSize(400f, 300f), 90f)
        val world = sequenceSceneWorldPoint(transform, size, Vec2.Zero)
        assertEquals(650f, world.x, .001f)
        assertEquals(350f, world.y, .001f)
        assertEquals(Vec2(500f, 550f), sequenceSceneWorldPoint(transform, size, Vec2(100f, 50f)))
        val viewport = Viewport(Vec2(10f, -20f), .5f)
        assertEquals(Vec2(335f, 155f), viewport.worldToScreen(world))
    }

    @Test fun membershipHidesOrdinaryDuplicatesKeepsRootAndExternalObjectsAndBlocksPartialMutations() {
        val w = Workspace(WorkspaceId("w"), "Board")
        val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 0, 0, w)
        var id = 0
        val op = SequenceCanvasCreation(owner, w, Vec2.Zero).operation(draft, "Sequence") { "id_${++id}" }
        val published = (op.applyTo(w) as OperationResult.Applied).workspace
        val diagram = published.sequenceDiagrams.values.single()
        val outside = TextNode(CanvasObjectId("outside"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 100f)), text = "Outside")
        val after = published.copy(objects = published.objects + (outside.id to outside))
        val m = SequenceCanvasMembership(after)
        assertEquals(listOf(diagram.containerId), m.visibleGroups(after).map { it.id })
        assertEquals(listOf(outside), m.visibleNodes(after))
        assertTrue(m.visibleRelations(after).isEmpty())
        assertFalse(m.blocksMutation(setOf(diagram.containerId), null))
        assertTrue(m.blocksMutation(setOf(diagram.participants.first().objectId), null))
        assertTrue(m.blocksMutation(emptySet(), diagram.messages.first().relationId))
        assertEquals(published.sequenceDiagrams, after.sequenceDiagrams)
    }
}
