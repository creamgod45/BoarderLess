package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.BackendHttpException
import cg.creamgod.boarderless.data.remote.isWorkspaceAccessLoss
import cg.creamgod.boarderless.data.remote.requiresWorkspaceReconnectAfterFailedSubmission
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendAccessPolicyTest {
    @Test
    fun unauthorizedForbiddenAndMissingWorkspacesAreTreatedAsAccessLoss() {
        assertTrue(BackendHttpException(HttpStatusCode.Unauthorized, "").isWorkspaceAccessLoss)
        assertTrue(BackendHttpException(HttpStatusCode.Forbidden, "").isWorkspaceAccessLoss)
        assertTrue(BackendHttpException(HttpStatusCode.NotFound, "").isWorkspaceAccessLoss)
    }

    @Test
    fun transientAndServerFailuresRemainRetryable() {
        assertFalse(BackendHttpException(HttpStatusCode.Conflict, "").isWorkspaceAccessLoss)
        assertFalse(BackendHttpException(HttpStatusCode.ServiceUnavailable, "").isWorkspaceAccessLoss)
    }

    @Test
    fun denialDuringSubmitOrReconciliationBlocksStaleWritePermissions() {
        val transient = BackendHttpException(HttpStatusCode.ServiceUnavailable, "")
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound)) {
            val denied = BackendHttpException(status, "")
            assertTrue(requiresWorkspaceReconnectAfterFailedSubmission(denied, transient))
            assertTrue(requiresWorkspaceReconnectAfterFailedSubmission(transient, denied))
        }
        assertTrue(requiresWorkspaceReconnectAfterFailedSubmission(IllegalStateException("offline"), transient))
        assertFalse(requiresWorkspaceReconnectAfterFailedSubmission(
            BackendHttpException(HttpStatusCode.UnprocessableEntity, ""), transient))
        assertFalse(requiresWorkspaceReconnectAfterFailedSubmission(transient, transient))
    }
}
