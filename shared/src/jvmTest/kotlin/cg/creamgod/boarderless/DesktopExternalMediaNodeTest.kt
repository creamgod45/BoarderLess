package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.media.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopExternalMediaNodeTest {
    @Test fun providerIdentityMovementAndAttributesSurviveHistoryAndDurableReopenWithoutAssets() = runTest {
        val root = Files.createTempDirectory("boarderless-external-node")
        try {
            val repo = DesktopFileWorkspaceRepository(root)
            var session = repo.openOrCreateWorkspace()
            var history = WorkspaceHistory(session.workspace)
            val reference = ExternalMediaReference(ExternalMediaProvider.Giphy, "gif-id")
            val node = ExternalMediaNode(CanvasObjectId("giphy"), transform = CanvasTransform(Vec2(10f, 20f), CanvasSize(240f, 360f)),
                reference = reference, altText = "中文🙂")
            suspend fun save(result: HistoryResult) {
                assertTrue(result.succeeded)
                repo.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                history = result.history
                session = repo.refresh(session)
                assertEquals(history.workspace, session.workspace)
            }
            assertTrue(repo.supportsExternalMedia)
            save(history.execute(CreateObjectsOperation("create-reference", listOf(node))))
            val moved = node.transform.copy(position = Vec2(130f, 210f), rotationDegrees = 35f)
            save(history.execute(TransformObjectsOperation("move-reference", listOf(TransformChange(node.id, 1, node.transform, moved)))))
            save(history.undo())
            assertEquals(node.transform, history.workspace.objects.getValue(node.id).transform)
            save(history.redo())
            assertEquals(moved, history.workspace.objects.getValue(node.id).transform)
            val current = history.workspace.objects.getValue(node.id) as ExternalMediaNode
            val attrs = MediaNodeAttributes(current.zIndex, current.locked, current.altText)
            save(history.execute(UpdateMediaNodeAttributesOperation("label-reference", listOf(
                MediaNodeAttributesChange(current.id, current.version, attrs, attrs.copy(altText = "重新命名", zIndex = 9, locked = true))))))
            save(history.undo()); save(history.redo())
            val restored = DesktopFileWorkspaceRepository(root).openOrCreateWorkspace()
            val persisted = assertIs<ExternalMediaNode>(restored.workspace.objects.getValue(node.id))
            assertEquals(history.workspace, restored.workspace)
            assertEquals(reference, persisted.reference)
            assertEquals("重新命名", persisted.altText)
            assertTrue(persisted.locked)
            assertEquals(9L, persisted.zIndex)
            assertEquals(moved, persisted.transform)
            assertTrue(repo.listAssets(restored).isEmpty())
            val content = kotlinx.serialization.json.Json { allowStructuredMapKeys = true }
                .encodeToString(Workspace.serializer(), restored.workspace)
            assertTrue(content.contains("giphy"))
            assertFalse(content.contains("https://"))
            assertFalse(content.contains("api_key"))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
