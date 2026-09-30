package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.BackendHttpException
import cg.creamgod.boarderless.data.remote.isWorkspaceAccessLoss
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendAccessPolicyTest {
    @Test
    fun unauthorizedAndMissingWorkspacesAreTreatedAsAccessLoss() {
        assertTrue(BackendHttpException(HttpStatusCode.Unauthorized, "").isWorkspaceAccessLoss)
        assertTrue(BackendHttpException(HttpStatusCode.NotFound, "").isWorkspaceAccessLoss)
    }

    @Test
    fun transientAndServerFailuresRemainRetryable() {
        assertFalse(BackendHttpException(HttpStatusCode.Conflict, "").isWorkspaceAccessLoss)
        assertFalse(BackendHttpException(HttpStatusCode.ServiceUnavailable, "").isWorkspaceAccessLoss)
    }
}
