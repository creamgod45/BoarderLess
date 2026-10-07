package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*

/** Untrusted historical content only. The saved current snapshot is NOT a fresh ACL or baseline.
 * A caller must obtain fresh authority before preview publication or explicit merge.
 * This reader never changes a journal, history, network connection or workspace.
 */
internal class WorkspaceDraftBackupReader {
    private val json = Json { allowStructuredMapKeys = true }

    fun read(
        content: String,
        expectedWorkspaceId: WorkspaceId,
    ): WorkspaceDraftReview {
        try {
            require(content.length in 1..MaximumBytes)
            require(content.encodeToByteArray().size <= MaximumBytes)
            requireBoundedDraftJsonDepth(content)
            val root = json.parseToJsonElement(content).jsonObject
            require(root.keys == RequiredKeys)
            require(root.getValue("format").jsonPrimitive.content == "boarderless.workspace-draft-review")
            require(root.getValue("schemaVersion").jsonPrimitive.int == 1)
            require(!root.getValue("importSupported").jsonPrimitive.boolean)
            val saved = json.decodeFromJsonElement(DraftReviewBackup.serializer(), root)
            require(saved.workspaceId == expectedWorkspaceId.value && saved.draftId.isNotBlank())
            require(saved.draftId.length <= 256)
            require(
                listOf(saved.baseVersion, saved.baseServerSeq, saved.currentVersion, saved.currentServerSeq)
                    .all { it in 0..MaxSafeSequence },
            )
            require(saved.currentVersion >= saved.baseVersion && saved.currentServerSeq >= saved.baseServerSeq)
            listOf(saved.baseline, saved.proposed, saved.current).forEach { validate(it, expectedWorkspaceId) }
            require(saved.operations.size <= 200)
            require(saved.operations.all { it.operationId.isNotBlank() && it.operationId.length <= 256 })
            require(
                saved.operations
                    .map { it.operationId }
                    .distinct()
                    .size == saved.operations.size,
            )
            val replayed =
                saved.operations.fold(saved.baseline) { workspace, operation ->
                    (operation.applyTo(workspace) as? OperationResult.Applied)?.workspace
                        ?: error("Invalid draft replay")
                }
            require(replayed == saved.proposed)
            return WorkspaceDraftReview(
                saved.draftId,
                saved.baseVersion,
                saved.baseServerSeq,
                saved.currentVersion,
                saved.currentServerSeq,
                saved.baseline,
                saved.proposed,
                saved.current,
                saved.operations,
                saved.quarantined,
                saved.hasUnconfirmedSubmission,
            )
        } catch (_: Exception) {
            // Neither private payload nor decoder diagnostics escape into UI/logs.
            throw IllegalArgumentException("Draft backup is invalid, unsupported or belongs to another workspace")
        }
    }

    private fun validate(
        workspace: Workspace,
        expected: WorkspaceId,
    ) {
        require(workspace.id == expected && workspace.version in 0..MaxSafeSequence)
        require(workspace.objects.size <= 10_000 && workspace.relations.size <= 10_000)
        workspace.objects.forEach { (id, obj) ->
            require(id == obj.id && obj.version in 0..MaxSafeSequence)
            val visited = mutableSetOf(id)
            var parent = obj.parentId
            while (parent != null) {
                require(visited.size <= 64 && visited.add(parent))
                val group = workspace.objects[parent] as? GroupFrame ?: error("Missing group")
                parent = group.parentId
            }
        }
        workspace.relations.forEach { (id, relation) ->
            require(id == relation.id && relation.version in 0..MaxSafeSequence)
            require(relation.sourceObjectId in workspace.objects && relation.targetObjectId in workspace.objects)
        }
    }

    private companion object {
        const val MaximumBytes = 4 * 1024 * 1024
        const val MaxSafeSequence = 9_007_199_254_740_991L
        val RequiredKeys =
            setOf(
                "format",
                "schemaVersion",
                "importSupported",
                "draftId",
                "workspaceId",
                "baseVersion",
                "baseServerSeq",
                "currentVersion",
                "currentServerSeq",
                "quarantined",
                "hasUnconfirmedSubmission",
                "baseline",
                "proposed",
                "current",
                "operations",
            )
    }
}
