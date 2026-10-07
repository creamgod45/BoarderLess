package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.feature.canvas.assignableWorkspaceMemberRoles
import cg.creamgod.boarderless.feature.canvas.normalizedMemberUserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceMembersTest {
    @Test
    fun normalizesValidUserIdsAndRejectsMalformedInput() {
        assertEquals(
            "abcdefab-cdef-abcd-efab-cdefabcdefab",
            normalizedMemberUserId("  ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB  "),
        )
        assertNull(normalizedMemberUserId("user-123"))
        assertNull(normalizedMemberUserId(""))
    }

    @Test
    fun ownerIsNeverAnAssignableRole() {
        assertEquals(
            listOf(WorkspaceMemberRole.Editor, WorkspaceMemberRole.Commenter, WorkspaceMemberRole.Viewer),
            assignableWorkspaceMemberRoles,
        )
    }
}
