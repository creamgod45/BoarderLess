package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A read-only review, never a submission request or a permission grant. */
data class WorkspaceDraftReview(
    val draftId: String,
    val baseVersion: Long,
    val baseServerSeq: Long,
    val currentVersion: Long,
    val currentServerSeq: Long,
    val baseline: Workspace,
    val proposed: Workspace,
    val current: Workspace,
    val operations: List<WorkspaceOperation>,
    val quarantined: Boolean,
    val hasUnconfirmedSubmission: Boolean,
) {
    val baselineMatchesCurrent: Boolean get() = baseVersion == currentVersion && baseServerSeq == currentServerSeq &&
        baseline.copy(version = 0, title = "") == current.copy(version = 0, title = "")
}

enum class DraftRemoteComparison { Unchanged, MatchesDraft, Changed }

/** UI publication guard only, not authentication. Epoch also rejects leave-and-return races. */
internal fun canPublishDraftReview(requested: WorkspaceSession, current: WorkspaceSession?,
    requestedEpoch: String, currentEpoch: String, requestedDraftId: String, currentDraftId: String?): Boolean =
    current != null && requestedEpoch == currentEpoch && requestedEpoch.isNotBlank() &&
        requestedDraftId == currentDraftId && requestedDraftId.isNotBlank() &&
        requested.userId == current.userId && requested.clientId == current.clientId &&
        requested.workspace.id == current.workspace.id

sealed interface WorkspaceDraftDifference {
    val id: String
    val remoteComparison: DraftRemoteComparison

    data class ObjectChange(val objectId: CanvasObjectId, val before: CanvasObject?, val draft: CanvasObject?, val remote: CanvasObject?) : WorkspaceDraftDifference {
        override val id: String get() = "object:${objectId.value}"
        override val remoteComparison get() = compareDraft(before, draft, remote)
    }
    data class RelationChange(val relationId: RelationId, val before: Relation?, val draft: Relation?, val remote: Relation?) : WorkspaceDraftDifference {
        override val id: String get() = "relation:${relationId.value}"
        override val remoteComparison get() = compareDraft(before, draft, remote)
    }
}

private fun <T> compareDraft(before: T?, draft: T?, remote: T?): DraftRemoteComparison = when (remote) {
    before -> DraftRemoteComparison.Unchanged
    draft -> DraftRemoteComparison.MatchesDraft
    else -> DraftRemoteComparison.Changed
}

/** Include only local draft effects. Unrelated remote changes still invalidate the global baseline gate. */
fun WorkspaceDraftReview.differences(): List<WorkspaceDraftDifference> = buildList {
    (baseline.objects.keys + proposed.objects.keys).sortedBy { it.value }.forEach { id ->
        val before = baseline.objects[id]
        val after = proposed.objects[id]
        if (before != after) add(WorkspaceDraftDifference.ObjectChange(id, before, after, current.objects[id]))
    }
    (baseline.relations.keys + proposed.relations.keys).sortedBy { it.value }.forEach { id ->
        val before = baseline.relations[id]
        val after = proposed.relations[id]
        if (before != after) add(WorkspaceDraftDifference.RelationChange(id, before, after, current.relations[id]))
    }
}

private val DraftReviewJson = Json { encodeDefaults = true; prettyPrint = true; allowStructuredMapKeys = true }

/** Explicit local backup. No server URL, user/client identity, wire request, ticket or Settings export.
 * Contains private canvas content; it is NOT a REST payload and automatic import is unsupported.
 */
fun WorkspaceDraftReview.toBackupJson(): String = DraftReviewJson.encodeToString(
    DraftReviewBackup(draftId = draftId, workspaceId = baseline.id.value,
        baseVersion = baseVersion, baseServerSeq = baseServerSeq, currentVersion = currentVersion,
        currentServerSeq = currentServerSeq, quarantined = quarantined,
        hasUnconfirmedSubmission = hasUnconfirmedSubmission, baseline = baseline, proposed = proposed,
        current = current, operations = operations))

@Serializable internal data class DraftReviewBackup(
    val format: String = "boarderless.workspace-draft-review",
    val schemaVersion: Int = 1,
    val importSupported: Boolean = false,
    val draftId: String,
    val workspaceId: String,
    val baseVersion: Long,
    val baseServerSeq: Long,
    val currentVersion: Long,
    val currentServerSeq: Long,
    val quarantined: Boolean,
    val hasUnconfirmedSubmission: Boolean,
    val baseline: Workspace,
    val proposed: Workspace,
    val current: Workspace,
    val operations: List<WorkspaceOperation>,
)

internal fun draftObjectJson(value: CanvasObject): String = DraftReviewJson.encodeToString(CanvasObject.serializer(), value)
internal fun draftRelationJson(value: Relation): String = DraftReviewJson.encodeToString(value)
