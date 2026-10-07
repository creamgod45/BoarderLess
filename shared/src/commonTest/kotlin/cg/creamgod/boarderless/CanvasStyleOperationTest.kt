package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository
import cg.creamgod.boarderless.data.remote.SessionPreferences
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.CanvasStyleDraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class CanvasStyleOperationTest {
    private val base = Workspace(WorkspaceId("canvas"), "Canvas")
    private val custom = CanvasStyle(backgroundToken = "#204060", gridStyle = CanvasGridStyle("dots", "#336699"))
    private val operation = UpdateCanvasStyleOperation("style", 0, base.canvasStyle, custom)

    private fun session(workspace: Workspace = base) =
        WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, workspace.version, 0, workspace)

    @Test fun historyUndoRedoUsesFreshMonotonicVersionsAndPreservesContentAndIdentity() {
        val withNode =
            base.copy(
                objects =
                    mapOf(
                        CanvasObjectId("node") to
                            TextNode(
                                CanvasObjectId("node"),
                                transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(80f, 60f)),
                                text = "內容🙂",
                            ),
                    ),
            )
        val executed = WorkspaceHistory(withNode).execute(operation)
        assertTrue(executed.succeeded)
        assertEquals(custom, executed.history.workspace.canvasStyle)
        assertEquals(1L, executed.history.workspace.canvasStyleVersion)
        val undone = executed.history.undo(remoteRestoration = true)
        assertTrue(undone.succeeded)
        assertEquals(base.canvasStyle, undone.history.workspace.canvasStyle)
        assertEquals(2L, undone.history.workspace.canvasStyleVersion)
        val redone = undone.history.redo(remoteRestoration = true)
        assertTrue(redone.succeeded)
        assertEquals(custom, redone.history.workspace.canvasStyle)
        assertEquals(3L, redone.history.workspace.canvasStyleVersion)
        assertEquals(3L, redone.history.workspace.version)
        assertEquals(withNode.objects, redone.history.workspace.objects)
        assertEquals(withNode.id, redone.history.workspace.id)
        assertEquals(3, listOf(executed, undone, redone).map { it.appliedOperation!!.operationId }.distinct().size)
    }

    @Test fun remoteStyleDoesNotGetOverwrittenByOldUndoAndVersionStateGuardsRemainStrict() {
        val executed = WorkspaceHistory(base).execute(operation).history
        val remote = executed.workspace.copy(canvasStyle = CanvasStyle(backgroundToken = "warm"), canvasStyleVersion = 2, version = 2)
        val conflicted = executed.copy(workspace = remote).undo()
        assertFalse(conflicted.succeeded)
        assertEquals(OperationError.CanvasStyleStateConflict, conflicted.error)
        assertEquals(remote, conflicted.history.workspace)
        assertTrue(operation.applyTo(base.copy(canvasStyleVersion = 1)) is OperationResult.Rejected)
        assertEquals(
            OperationError.CanvasStyleStateConflict,
            (operation.applyTo(base.copy(canvasStyle = custom)) as OperationResult.Rejected).error,
        )
        val max = MaximumCanvasVersion
        val last = operation.copy(expectedCanvasStyleVersion = max - 1)
        val reached = WorkspaceHistory(base.copy(canvasStyleVersion = max - 1)).execute(last)
        assertTrue(reached.succeeded)
        val blocked = reached.history.undo()
        assertFalse(blocked.succeeded)
        assertEquals(OperationError.CanvasStyleVersionLimit, blocked.error)
        assertEquals(OperationError.WorkspaceVersionLimit, (operation.applyTo(base.copy(version = max)) as OperationResult.Rejected).error)
    }

    @Test fun serializableStyleAndOperationAreStrictAndTransactionsRemainAtomic() {
        val json =
            Json {
                encodeDefaults = true
                allowStructuredMapKeys = true
            }
        assertEquals(operation, json.decodeFromString<WorkspaceOperation>(json.encodeToString<WorkspaceOperation>(operation)))
        assertEquals(
            base.copy(canvasStyle = custom),
            json.decodeFromString<Workspace>(json.encodeToString(base.copy(canvasStyle = custom))),
        )
        for (token in listOf("", "#abc", "#AABBCC", "rgba(0,0,0,0)", "https://asset", "unknown")) {
            assertFails {
                CanvasStyle(backgroundToken = token)
            }
        }
        assertFails { CanvasStyle(schemaVersion = 2) }
        assertFails { CanvasGridStyle("shader") }
        assertFails { CanvasGridStyle(colorToken = "default") }
        assertFails { base.copy(canvasStyleVersion = -1) }
        assertFails { operation.copy(expectedCanvasStyleVersion = MaximumCanvasVersion + 1) }
        val failed = TransactionOperation("batch", listOf(operation, operation.copy(operationId = "stale"))).applyTo(base)
        assertTrue(failed is OperationResult.Rejected)
        assertEquals(CanvasStyle(), base.canvasStyle)
        val successful =
            TransactionOperation(
                "batch",
                listOf(operation, UpdateCanvasStyleOperation("second", 1, custom, CanvasStyle(backgroundToken = "cool"))),
            ).applyTo(base) as OperationResult.Applied
        assertEquals(2L, successful.workspace.canvasStyleVersion)
        assertEquals(1L, successful.workspace.version)
    }

    @Test fun pickerPreviewHasNoMutationAndScopeOrStyleChangesRejectApplyButUnrelatedEditsAllowIt() {
        val opened = session()
        val draft = CanvasStyleDraft.capture(opened, base)
        assertEquals(null, draft.operation(opened, base, "default", "no-op"))
        assertEquals(
            operation.copy(after = base.canvasStyle.copy(backgroundToken = "#204060")),
            draft.operation(opened, base, "#204060", "style"),
        )
        assertEquals("#aabbcc", draft.operation(opened, base, "#AABBCC", "uppercase")!!.after.backgroundToken)
        val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(80f, 60f)), text = "Unrelated")
        assertNotNull(draft.operation(opened, base.copy(version = 1, objects = mapOf(node.id to node)), "cool", "other"))
        assertFails { draft.operation(opened.copy(userId = "other"), base, "cool", "wrong") }
        assertFails { draft.operation(opened.copy(clientId = "other"), base, "cool", "wrong") }
        assertFails { draft.operation(opened.copy(role = WorkspaceMemberRole.Viewer), base, "cool", "wrong") }
        assertFails { draft.operation(opened, base.copy(canvasStyleVersion = 1), "cool", "wrong") }
        assertFails { draft.operation(opened, base.copy(canvasStyle = custom), "cool", "wrong") }
        assertEquals(CanvasStyle(), base.canvasStyle)
        val settings = CanvasPreferences(InMemorySettings())
        settings.saveDisplaySettings("canvas", CanvasDisplaySettings(backgroundToken = "warm"))
        settings.saveDisplaySettings(
            "canvas",
            CanvasDisplaySettings(showGrid = false, snapToGrid = true, backgroundToken = "cool"),
            saveBackground = false,
        )
        assertEquals(CanvasDisplaySettings(false, true, "warm"), settings.loadDisplaySettings("canvas"))
    }

    @Test fun backendWithoutCapabilityRejectsStyleBeforeDraftWriteOrHttp() =
        runTest {
            val memory = InMemorySettings()
            val preferences = SessionPreferences(memory)
            val opened = session().copy(clientId = preferences.clientId)
            val before = memory.keys.associateWith(memory::getStringOrNull)
            var calls = 0
            val repository =
                BackendWorkspaceRepository(
                    "http://fixture",
                    preferences,
                    HttpClient(
                        MockEngine {
                            calls++
                            error("No HTTP")
                        },
                    ),
                )
            try {
                assertFalse(repository.supportsCanvasStyle)
                assertFails { repository.retainDraft(opened, base, operation) }
                assertFails {
                    repository.retainDraft(
                        opened,
                        base,
                        TransactionOperation("nested", listOf(TransactionOperation("child", listOf(operation)))),
                    )
                }
                assertFails { repository.submit(opened, operation) }
                assertEquals(0, calls)
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
            } finally {
                repository.close()
            }
        }

    @Test fun threeWayMergeShowsStyleConflictCompilesFreshGuardedOperationAndReportsMissingBackendCapability() {
        val remote = base.copy(canvasStyle = CanvasStyle(backgroundToken = "warm"), canvasStyleVersion = 3, version = 5)
        val proposed = (operation.applyTo(base) as OperationResult.Applied).workspace
        val plan =
            WorkspaceDraftMergePlan(WorkspaceDraftReview("draft", 0, 0, 5, 6, base, proposed, remote, listOf(operation), false, false))
        val row = plan.fields.single()
        assertTrue(row.id.workspaceStyle && row.conflicts)
        assertEquals(remote, plan.resolve(mapOf(row.id to DraftMergeChoice.KeepRemote)))
        val target = plan.resolve(mapOf(row.id to DraftMergeChoice.UseDraft))
        assertEquals(custom, target.canvasStyle)
        var index = 0
        val merged =
            assertNotNull(buildDraftMergeOperation(plan, mapOf(row.id to DraftMergeChoice.UseDraft), remote) { "merge-${++index}" })
        val command = merged.operations.single() as UpdateCanvasStyleOperation
        assertEquals(remote.canvasStyle, command.before)
        assertEquals(3L, command.expectedCanvasStyleVersion)
        val applied = (merged.applyTo(remote) as OperationResult.Applied).workspace
        assertEquals(custom, applied.canvasStyle)
        assertEquals(4L, applied.canvasStyleVersion)
        assertEquals(setOf(DraftMergeContractGap.CanvasStyle), draftMergeContractGaps(remote, merged))
    }
}
