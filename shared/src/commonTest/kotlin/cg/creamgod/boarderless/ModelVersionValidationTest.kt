package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ModelVersionValidationTest {
    private val transform = CanvasTransform(Vec2.Zero, CanvasSize(200f, 100f))

    @Test
    fun canvasObjectsRequirePositiveVersions() {
        assertFailsWith<IllegalArgumentException> {
            TextNode(CanvasObjectId("text"), version = 0, transform = transform, text = "Invalid")
        }
        assertFailsWith<IllegalArgumentException> {
            GroupFrame(CanvasObjectId("group"), version = -1, transform = transform)
        }
    }

    @Test
    fun relationsRequirePositiveVersions() {
        assertFailsWith<IllegalArgumentException> {
            Relation(
                id = RelationId("relation"),
                version = 0,
                sourceObjectId = CanvasObjectId("source"),
                targetObjectId = CanvasObjectId("target"),
            )
        }
    }

    @Test
    fun workspacesAllowInitialZeroButRejectNegativeVersions() {
        Workspace(WorkspaceId("initial"), "Initial", version = 0)
        assertFailsWith<IllegalArgumentException> {
            Workspace(WorkspaceId("invalid"), "Invalid", version = -1)
        }
    }
}
