package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class CreateUserRequest(val displayName: String)

@Serializable
internal data class UserDto(
    val id: String,
    val displayName: String,
)

@Serializable
internal data class CreateWorkspaceRequest(val title: String)

@Serializable
internal data class RenameWorkspaceRequest(val title: String)

@Serializable
internal data class WorkspaceDto(
    val id: String,
    val ownerId: String,
    val title: String,
    val currentVersion: Long,
    val lastServerSeq: Long,
    val schemaVersion: Int,
    val role: String,
)

@Serializable
internal data class WorkspaceListDto(val workspaces: List<WorkspaceDto>)

@Serializable
internal data class WorkspaceMemberDto(
    val userId: String,
    val displayName: String,
    val role: String,
    val joinedAt: String,
)

@Serializable
internal data class WorkspaceMemberListDto(val members: List<WorkspaceMemberDto>)

@Serializable
internal data class SetWorkspaceMemberRoleRequest(val role: String)

@Serializable
internal data class AssetDto(
    val id: String,
    val workspaceId: String,
    val ownerId: String,
    val storageKey: String,
    val mediaType: String,
    val byteSize: Long,
    val checksum: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val status: String,
    val createdAt: String,
    val thumbnailAssetId: String? = null,
    val rejectionReason: String? = null,
)

@Serializable
internal data class AssetListDto(val assets: List<AssetDto>)

@Serializable
internal data class CanvasObjectDto(
    val objectId: String,
    val objectType: String,
    val objectVersion: Long,
    val parentId: String? = null,
    val zIndex: Long,
    val locked: Boolean,
    val transform: JsonObject,
    val properties: JsonObject,
)

@Serializable
internal data class RelationDto(
    val relationId: String,
    val relationVersion: Long,
    val sourceObjectId: String,
    val targetObjectId: String,
    val direction: String,
    val intent: String? = null,
    val label: String? = null,
    val style: JsonObject,
)

@Serializable
internal data class WorkspaceStateDto(
    val workspaceId: String,
    val workspaceVersion: Long,
    val throughServerSeq: Long,
    val objects: List<CanvasObjectDto>,
    val relations: List<RelationDto> = emptyList(),
)

@Serializable
internal data class SubmitOperationsRequest(
    val protocolVersion: Int = 1,
    val clientId: String,
    val transactionId: String,
    val baseVersion: Long,
    val operations: List<OperationDto>,
)

@Serializable
internal data class OperationDto(
    val operationId: String,
    val clientSeq: Long,
    val kind: String,
    val expectedObjectVersions: Map<String, Long>? = null,
    val payload: JsonObject,
)

@Serializable
internal data class CommittedOperationDto(
    val serverSeq: Long,
    val actorId: String,
    val clientId: String,
    val workspaceVersion: Long,
    val operationType: String,
    val committedAt: String,
)

@Serializable
internal data class CatchUpOperationsDto(
    val operations: List<CommittedOperationDto>,
    val lastServerSeq: Long,
    val hasMore: Boolean,
)

/** The same REST route, decoded as full records only when replay is required. */
@Serializable
internal data class FullCatchUpOperationsDto(
    val operations: List<CommittedWorkspaceOperationDto>,
    val lastServerSeq: Long,
    val hasMore: Boolean,
)

@Serializable
internal data class AcceptedOperationsDto(
    val status: String,
    val workspaceVersion: Long,
    val fromServerSeq: Long,
    val toServerSeq: Long,
    val operations: List<CommittedWorkspaceOperationDto>,
)

/** Full durable REST record. The activity-only CommittedOperationDto is not a replay record. */
@Serializable
internal data class CommittedWorkspaceOperationDto(
    val serverSeq: Long,
    val operationId: String,
    val transactionId: String,
    val actorId: String,
    val clientId: String,
    val clientSeq: Long,
    val baseVersion: Long,
    val workspaceVersion: Long,
    val operationType: String,
    val payload: JsonObject,
    val schemaVersion: Int,
    val committedAt: String,
)

@Serializable
internal data class ConflictOperationsDto(
    val status: String,
    val workspaceVersion: Long,
    val lastServerSeq: Long,
)
