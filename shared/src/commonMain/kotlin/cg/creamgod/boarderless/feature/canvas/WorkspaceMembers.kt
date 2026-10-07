package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceMemberRole

private val UuidPattern =
    Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )

internal fun normalizedMemberUserId(value: String): String? = value.trim().takeIf(UuidPattern::matches)?.lowercase()

internal val assignableWorkspaceMemberRoles: List<WorkspaceMemberRole> =
    listOf(
        WorkspaceMemberRole.Editor,
        WorkspaceMemberRole.Commenter,
        WorkspaceMemberRole.Viewer,
    )
