package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.canvas.WorkspaceChangePolicy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceChangePolicyTest {
    private val ready = WorkspaceChangePolicy(sessionAvailable = true)

    @Test
    fun readyWorkspaceCanBeChanged() {
        assertTrue(ready.allowed)
    }

    @Test
    fun unavailableOrUnhealthyWorkspaceCannotBeChanged() {
        assertFalse(ready.copy(sessionAvailable = false).allowed)
        assertFalse(ready.copy(connectionFailed = true).allowed)
        assertFalse(ready.copy(switchInProgress = true).allowed)
    }

    @Test
    fun pendingOrActiveSaveCannotBeInterrupted() {
        assertFalse(ready.copy(mediaImportActive = true).allowed)
        assertFalse(ready.copy(syncInProgress = true).allowed)
        assertFalse(ready.copy(pendingSaveCount = 1).allowed)
        assertFailsWith<IllegalArgumentException> {
            ready.copy(pendingSaveCount = -1)
        }
    }

    @Test
    fun unfinishedTextOrNodeDraftCannotBeDiscardedByWorkspaceChange() {
        assertFalse(ready.copy(nodeEditing = true).allowed)
        assertFalse(ready.copy(contentInspectorEditing = true).allowed)
        assertFalse(ready.copy(pendingNodeDraft = true).allowed)
    }

    @Test
    fun activeCanvasGesturesCannotBeInterruptedByWorkspaceChange() {
        assertFalse(ready.copy(objectDragActive = true).allowed)
        assertFalse(ready.copy(transformActive = true).allowed)
        assertFalse(ready.copy(connectionDragActive = true).allowed)
        assertFalse(ready.copy(marqueeActive = true).allowed)
        assertFalse(ready.copy(libraryDragActive = true).allowed)
    }
}
