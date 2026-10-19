package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopSequenceClipboardTest {
    @Test fun exactPasteRetriesEveryAtomicCheckpointWithoutDuplicateReceipt() = runTest {
        val source = sequenceClipboardFixture()
        val payload = assertNotNull(workspaceClipboardPayload(source, source.sequenceDiagrams.keys))
        for (stage in AtomicDraftStage.entries) {
            val root = Files.createTempDirectory("boarderless-paste-fault")
            try {
                var armed = false
                val repo = DesktopFileWorkspaceRepository(DesktopAtomicDraftStore(root) {
                    if (armed && it == stage) error("Checkpoint")
                })
                val session = repo.openOrCreateWorkspace()
                val prepared = prepareClipboardInsertion(payload, session.workspace)
                armed = true
                assertFails { repo.retainDraft(session, session.workspace, prepared.operation) }
                armed = false
                val reopened = DesktopFileWorkspaceRepository(root)
                reopened.retainDraft(session, session.workspace, prepared.operation)
                val after = reopened.refresh(session)
                assertEquals(assertIs<OperationResult.Applied>(prepared.operation.applyTo(session.workspace)).workspace, after.workspace)
                assertEquals(1, reopened.listRecentActivity(after).size)
                assertEquals(1, after.workspace.sequenceDiagrams.size)
                assertEquals(prepared.selectedIds, after.workspace.sequenceDiagrams.keys)
            } finally {
                Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }

    @Test fun nativeSchemeReopenExportImportAndPasteHistoryKeepEveryBinding() = runTest {
        val root = Files.createTempDirectory("boarderless-sequence-clipboard")
        try {
            val source = sequenceClipboardFixture()
            val payload = assertNotNull(workspaceClipboardPayload(source, source.sequenceDiagrams.keys))
            val library = DesktopFileQuickSchemeStore(root.resolve("library"))
            val saved = library.save(ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), "時序🙂", 7, "source")
            val reopened = DesktopFileQuickSchemeStore(root.resolve("library")).list().single()
            assertEquals(saved, reopened)
            val received = QuickSchemeTransferCodec.decode(QuickSchemeTransferCodec.encode(reopened))
            val destination = DesktopFileWorkspaceRepository(root.resolve("destination"))
            var session = destination.openOrCreateWorkspace()
            var history = WorkspaceHistory(session.workspace)
            val files = Files.list(root.resolve("destination")).use { it.filter(Files::isRegularFile).toList() }
            val before = files.associateWith { Files.readAllBytes(it) }
            val insertion = prepareClipboardInsertion(assertNotNull(decodeQuickSchemePayload(received)), history.workspace, Vec2(200f, 300f))
            before.forEach { (file, bytes) -> assertContentEquals(bytes, Files.readAllBytes(file)) }
            suspend fun persist(result: HistoryResult) {
                assertTrue(result.succeeded)
                destination.retainDraft(session, history.workspace, assertNotNull(result.appliedOperation))
                history = result.history
                session = session.copy(workspace = history.workspace, workspaceVersion = history.workspace.version)
                assertEquals(history.workspace, DesktopFileWorkspaceRepository(root.resolve("destination")).openOrCreateWorkspace().workspace)
            }
            persist(history.execute(insertion.operation))
            val copied = history.workspace.sequenceDiagrams.values.single()
            assertEquals(source.sequenceDiagrams.values.single().toDraft(), copied.toDraft())
            assertEquals(Vec2(200f, 300f), history.workspace.objects.getValue(copied.containerId).transform.position)
            persist(history.undo(remoteRestoration = true))
            assertTrue(history.workspace.sequenceDiagrams.isEmpty())
            persist(history.redo(remoteRestoration = true))
            assertEquals(copied, history.workspace.sequenceDiagrams.values.single())
            assertTrue(history.workspace.hasValidSequenceBindings())
            assertEquals(saved, DesktopFileQuickSchemeStore(root.resolve("library")).list().single())
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
