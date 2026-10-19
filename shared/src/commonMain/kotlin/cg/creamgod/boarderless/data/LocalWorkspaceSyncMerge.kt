package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

@Serializable enum class LocalSyncMergeChoice { Local, Remote }

/** A reviewed durable proposal, not an HTTP request, an applied canvas, or an ACK. */
@Serializable data class LocalWorkspaceSyncProposal(
    val id: String,
    val binding: LocalWorkspaceSyncBinding,
    val localDigest: String,
    val checkpointDigest: String,
    val remote: Workspace,
    val remoteVersion: Long,
    val remoteSeq: Long,
    val remoteRole: String,
    val remoteClientId: String,
    val choices: Map<String, LocalSyncMergeChoice>,
    val target: Workspace,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(listOf(localDigest, checkpointDigest).all { it.matches(Regex("[0-9a-f]{64}")) })
        require(remote.id == binding.workspaceId && remote.version == remoteVersion)
        require(remoteVersion in 0..MaximumCanvasVersion && remoteSeq in 0..MaximumCanvasVersion)
        require(WorkspaceMemberRole.fromToken(remoteRole) != null && remoteClientId.isNotBlank())
        require(choices.size <= 40_003 && choices.keys.all { it.isNotBlank() && it.length <= 1_048_576 })
    }
    internal fun remoteSession() = WorkspaceSession(binding.userId, remoteClientId,
        requireNotNull(WorkspaceMemberRole.fromToken(remoteRole)), remoteVersion, remoteSeq, remote)
}

private val SyncMergeJson = Json { encodeDefaults = true; allowStructuredMapKeys = true }

private fun canonicalWorkspace(workspace: Workspace) = workspace.copy(
    objects = workspace.objects.entries.sortedBy { it.key.value }.associate { it.toPair() },
    relations = workspace.relations.entries.sortedBy { it.key.value }.associate { it.toPair() },
    sequenceDiagrams = workspace.sequenceDiagrams.entries.sortedBy { it.key.value }.associate { it.toPair() },
)

private fun digest(value: String) = SHA256().digest(value.encodeToByteArray())
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

internal fun syncWorkspaceDigest(workspace: Workspace) = digest(SyncMergeJson.encodeToString(canonicalWorkspace(workspace)))
internal fun syncCheckpointDigest(checkpoint: LocalWorkspaceSyncCheckpoint) =
    digest(SyncMergeJson.encodeToString(checkpoint.copy(baseline = canonicalWorkspace(checkpoint.baseline))))

internal data class LocalSyncMergeRow(
    val id: String,
    val baseline: String,
    val local: String,
    val remote: String,
    val requiresChoice: Boolean,
    val automatic: LocalSyncMergeChoice?,
)

/** Full document planning. Per-entity choices are deliberate atomic units; sequence metadata
 * also requires valid bindings in the complete resulting graph. No unsupported data is dropped. */
internal class LocalWorkspaceSyncMergePlan(
    val snapshot: LocalWorkspaceSyncSnapshot,
    val remote: WorkspaceSession,
) {
    private val checkpoint = requireNotNull(snapshot.checkpoint)
    private val baseline = checkpoint.baseline
    private val local = snapshot.canonicalLocalWorkspace()
    private val current = remote.workspace.copy(id = local.id)
    val review: WorkspaceDraftReview
    val rows: List<LocalSyncMergeRow>

    init {
        checkpoint.binding.requireRemote(remote)
        require(baseline.id == local.id && snapshot.local.workspaceVersion == local.version)
        require(remote.workspaceVersion >= checkpoint.serverVersion && remote.lastServerSeq >= checkpoint.serverSeq)
        require(listOf(baseline, local, current).all { it.hasValidSequenceBindings() })
        review = WorkspaceDraftReview("sync-merge:${local.id.value}:${local.version}:${remote.workspaceVersion}:${remote.lastServerSeq}",
            checkpoint.serverVersion, checkpoint.serverSeq, remote.workspaceVersion, remote.lastServerSeq,
            baseline, local, current, emptyList(), false, false)
        rows = buildList {
            fun row(id: String, a: String, b: String, c: String) {
                if (b == c) return
                val automatic = when { b == a -> LocalSyncMergeChoice.Remote; c == a -> LocalSyncMergeChoice.Local; else -> null }
                add(LocalSyncMergeRow(id, a, b, c, checkpoint.requiresInitialReview || automatic == null, automatic))
            }
            row("document:title", SyncMergeJson.encodeToString(baseline.title), SyncMergeJson.encodeToString(local.title), SyncMergeJson.encodeToString(current.title))
            row("document:canvasStyle", SyncMergeJson.encodeToString(baseline.canvasStyle), SyncMergeJson.encodeToString(local.canvasStyle), SyncMergeJson.encodeToString(current.canvasStyle))
            row("document:sequenceDiagrams", SyncMergeJson.encodeToString(canonicalWorkspace(baseline).sequenceDiagrams),
                SyncMergeJson.encodeToString(canonicalWorkspace(local).sequenceDiagrams), SyncMergeJson.encodeToString(canonicalWorkspace(current).sequenceDiagrams))
            (baseline.objects.keys + local.objects.keys + current.objects.keys).sortedBy { it.value }.forEach { id ->
                fun encode(value: CanvasObject?) = value?.let { SyncMergeJson.encodeToString(CanvasObject.serializer(), it.withVersion(1)) } ?: "null"
                row("object:${id.value}", encode(baseline.objects[id]), encode(local.objects[id]), encode(current.objects[id]))
            }
            (baseline.relations.keys + local.relations.keys + current.relations.keys).sortedBy { it.value }.forEach { id ->
                fun encode(value: Relation?) = value?.let { SyncMergeJson.encodeToString(it.withVersion(1)) } ?: "null"
                row("relation:${id.value}", encode(baseline.relations[id]), encode(local.relations[id]), encode(current.relations[id]))
            }
        }
    }

    fun resolve(choices: Map<String, LocalSyncMergeChoice>): Workspace {
        require(choices.keys.all { id -> rows.any { it.id == id } }) { "Unknown merge decision" }
        require(rows.filter { it.requiresChoice }.all { it.id in choices }) { "Every conflict needs a decision" }
        val decisions = rows.associate { it.id to (choices[it.id] ?: requireNotNull(it.automatic)) }
        fun <T> select(id: String, a: T, b: T): T = if (decisions[id] == LocalSyncMergeChoice.Remote) b else a
        val objects = (local.objects.keys + current.objects.keys).mapNotNull { id ->
            select("object:${id.value}", local.objects[id], current.objects[id])?.let { id to it }
        }.toMap()
        val relations = (local.relations.keys + current.relations.keys).mapNotNull { id ->
            select("relation:${id.value}", local.relations[id], current.relations[id])?.let { id to it }
        }.toMap()
        val target = local.copy(
            title = select("document:title", local.title, current.title),
            canvasStyle = select("document:canvasStyle", local.canvasStyle, current.canvasStyle),
            sequenceDiagrams = select("document:sequenceDiagrams", local.sequenceDiagrams, current.sequenceDiagrams),
            objects = objects, relations = relations,
        )
        require(target.objects.size <= 10_000 && target.relations.size <= 10_000)
        target.objects.forEach { (id, value) ->
            require(id == value.id)
            val seen = mutableSetOf(id)
            var parent = value.parentId
            while (parent != null) {
                require(seen.size <= 64 && seen.add(parent)) { "Invalid merged hierarchy" }
                parent = (target.objects[parent] as? GroupFrame ?: error("Missing merged group")).parentId
            }
        }
        target.relations.forEach { (id, value) ->
            require(id == value.id && value.sourceObjectId in objects && value.targetObjectId in objects)
            require(value.sourceObjectId != value.targetObjectId || value.geometry != null)
        }
        require(target.hasValidSequenceBindings()) { "Choose a coherent complete sequence diagram" }
        // A proposal cannot silently edit/delete a locked object in either destination.
        listOf(local, current).forEach { source -> source.objects.values.filter { it.locked }.forEach { before ->
            val after = target.objects[before.id]
            require(after?.withVersion(1) == before.withVersion(1) || (after != null && !after.locked)) { "A locked object needs an explicit unlock" }
        } }
        return target
    }

    fun proposal(id: String, choices: Map<String, LocalSyncMergeChoice>) = LocalWorkspaceSyncProposal(
        id, checkpoint.binding, syncWorkspaceDigest(snapshot.local.workspace), syncCheckpointDigest(checkpoint), remote.workspace,
        remote.workspaceVersion, remote.lastServerSeq, remote.role.token, remote.clientId, choices.toMap(), resolve(choices),
    )
}
