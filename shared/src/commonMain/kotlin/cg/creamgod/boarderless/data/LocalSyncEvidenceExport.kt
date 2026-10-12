package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val LocalSyncEvidenceJson = Json { encodeDefaults = true; allowStructuredMapKeys = true }

/** User-approved private audit copy. No credentials, transport endpoint or new authority.
 * Deliberately not an import/replay format: old receipt metadata is not a fresh server proof. */
@Serializable internal data class LocalSyncEvidenceBackup(
    val schema: String = "boarderless.local-sync-evidence.v1",
    val purpose: String = "private-audit-copy-not-submission-or-authorization",
    val userId: String,
    val clientId: String,
    val workspace: Workspace,
    val checkpoint: LocalWorkspaceSyncCheckpoint?,
    val proposal: LocalWorkspaceSyncProposal?,
    val applied: AppliedLocalSyncProposal?,
    val outgoing: OutgoingLocalSyncTransaction?,
    val stopped: List<StoppedLocalSyncTransaction>,
    val rebaseProposal: LocalSyncRebaseProposal?,
    val rebases: List<AppliedLocalSyncRebase>,
    val superseded: List<SupersededLocalSyncProposal>,
    val materializations: List<LocalAssetMaterialization> = emptyList(),
    val assetUploadPreparations: List<LocalAssetUploadPreparation> = emptyList(),
)

internal fun LocalWorkspaceSyncSnapshot.toEvidenceJson(): String {
    val content = LocalSyncEvidenceJson.encodeToString(LocalSyncEvidenceBackup.serializer(),
        LocalSyncEvidenceBackup(userId = local.userId, clientId = local.clientId, workspace = local.workspace,
            checkpoint = checkpoint, proposal = proposal, applied = applied, outgoing = outgoing,
            stopped = stopped, rebaseProposal = rebaseProposal, rebases = rebases, superseded = superseded,
            materializations = materializations, assetUploadPreparations = assetUploadPreparations))
    require(content.length <= 16 * 1024 * 1024 && content.encodeToByteArray(throwOnInvalidSequence = true).size <= 16 * 1024 * 1024) {
        "Sync evidence exceeds export limit"
    }
    parseStrictJsonObject(content) // Reject invalid Unicode even if serialized as an escape.
    return content
}

internal suspend fun exportLocalSyncEvidence(runtime: DraftBackupRuntime, repository: CanvasWorkspaceRepository,
    reviewed: LocalWorkspaceSyncSnapshot, canContinue: () -> Boolean): DraftBackupResult {
    require(repository.isLocalOnly)
    return exportLocalJsonBackup(runtime, "boarderless-sync-evidence.json", canContinue) {
        val fresh = requireNotNull(repository.localSyncStore).readSyncSnapshot(reviewed.local)
        require(fresh == reviewed) { "Sync evidence changed; review again" }
        fresh.toEvidenceJson()
    }
}
