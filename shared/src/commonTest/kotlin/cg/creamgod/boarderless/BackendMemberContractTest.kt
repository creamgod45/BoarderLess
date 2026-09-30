package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.remote.BackendContractException
import cg.creamgod.boarderless.data.remote.WorkspaceMemberDto
import cg.creamgod.boarderless.data.remote.toDomain
import cg.creamgod.boarderless.data.remote.toWorkspaceMemberRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendMemberContractTest {
    @Test
    fun memberDtoPreservesIdentityRoleAndJoinTime() {
        val member = WorkspaceMemberDto(
            userId = "00000000-0000-0000-0000-000000000001",
            displayName = "Diagram partner",
            role = "editor",
            joinedAt = "2026-09-29T02:00:00.000Z",
        ).toDomain()

        assertEquals("Diagram partner", member.displayName)
        assertEquals(WorkspaceMemberRole.Editor, member.role)
        assertEquals("2026-09-29T02:00:00.000Z", member.joinedAt)
    }

    @Test
    fun unknownRoleAndBlankRequiredFieldsAreRejected() {
        assertFailsWith<BackendContractException> {
            WorkspaceMemberDto("user", "Partner", "superuser", "today").toDomain()
        }
        assertFailsWith<BackendContractException> {
            WorkspaceMemberDto("user", "", "viewer", "today").toDomain()
        }
        assertEquals(WorkspaceMemberRole.Owner, "owner".toWorkspaceMemberRole())
        assertFailsWith<BackendContractException> { "administrator".toWorkspaceMemberRole() }
    }
}
