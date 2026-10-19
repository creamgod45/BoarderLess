package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopBrushPersistenceTest {
    @Test fun savedStrokeReopensAsOneObjectAndActualUndoRedoPreserveItsIdentity() = runTest {
        val root = Files.createTempDirectory("boarderless-brush")
        try {
            val repo = DesktopFileWorkspaceRepository(root)
            var session = repo.openOrCreateWorkspace()
            var history = WorkspaceHistory(session.workspace)
            val draft = prepareBrushStroke(session, history.workspace,
                listOf(Vec2(-40f, 70f), Vec2(20f, 100f), Vec2(80f, 70f)), 8f, "#1976d2", .5f, "stroke", "brush-op")
            fun save(result: HistoryResult) {
                assertTrue(result.succeeded)
                repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                history = result.history
                session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
            }
            save(history.execute(draft.operation))
            val stored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace
            assertEquals(history.workspace, stored)
            assertEquals(setOf(draft.objectId), stored.objects.keys)
            val stroke = stored.objects.values.single() as TextNode
            assertEquals(draft.operation.objects.single(), stroke)
            save(history.undo())
            assertTrue(DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace.objects.isEmpty())
            save(history.redo())
            assertEquals(stroke.vectorPath, (DesktopFileWorkspaceRepository(root).openOrCreateWorkspace().workspace.objects.values.single() as TextNode).vectorPath)
            assertEquals(draft.objectId, history.workspace.objects.values.single().id)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
