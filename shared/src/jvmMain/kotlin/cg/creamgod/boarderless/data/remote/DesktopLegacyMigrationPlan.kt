package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class PlannedLegacyScope(
    val scope: PendingSubmissionScope,
    val draft: WorkspaceDraftScopeBundle?,
    val safety: RecoverySafetyBundle,
    val minimumClientSequence: Long,
    val retryQuarantined: Boolean,
)

internal data class DesktopLegacyMigrationPlan(
    val sourceDigest: String,
    val scopes: List<PlannedLegacyScope>,
)

@Serializable
private data class ScopedLegacyMigrationSource(
    val scope: PendingSubmissionScope,
    val journal: WorkspaceDraftJournal?,
    val pending: PendingWorkspaceSubmission?,
    val stopped: List<PendingWorkspaceSubmission>,
    val fences: List<PendingFenceAttemptRecord>,
    val deletions: List<CommittedDeletionEvidence>,
)

/** Pure initial-migration planner. No writes, initialization, authority lookup or activation.
 * Caller must still prove catalog completeness, exclude old writers, inspect existing native
 * records/ledgers and commit/recheck an activation receipt before switching the runtime writer.
 * Existing native bundles are NOT overwritten or silently treated as equivalent legacy state.
 */
internal object DesktopLegacyMigrationPlanner {
    private val json =
        Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
        }

    private fun digest(bytes: ByteArray) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    fun prepare(
        target: PendingSubmissionScope,
        capture: LegacySequenceCapture,
        existingNativeBundles: List<WorkspaceDraftScopeBundle>,
    ): DesktopLegacyMigrationPlan {
        require(
            existingNativeBundles.isEmpty(),
        ) { "Existing native state requires activation-receipt reconciliation, not legacy replacement" }
        require(
            capture.journals.size <= 256 && capture.journals
                .map { it.scope }
                .distinct()
                .size == capture.journals.size,
        )
        require(capture.fenceAttempts.size <= 256 && capture.deletionEvidence.size <= 4096)
        require(capture.unboundFenceAttempts.isEmpty()) { "Unbound fence attempts require explicit recovery" }
        require(
            capture.floor ==
                CapturedClientSequenceFloor.calculate(
                    target,
                    capture.counter,
                    capture.pending,
                    existingNativeBundles,
                    capture.stopped,
                ),
        ) { "Captured sequence sources changed" }
        val wires = capture.pending + capture.stopped
        capture.fenceAttempts.forEach { marker ->
            val matches = wires.filter { it.scope == marker.scope && it.request.transactionId == marker.transactionId }
            require(matches.isNotEmpty()) { "A fence digest is not a replacement for its missing wire" }
            matches.forEach { require(marker.wireDigest == digest(json.encodeToString(it.request).encodeToByteArray())) }
        }
        val scopes =
            (
                listOf(target) + capture.journals.map { it.scope } + wires.map { it.scope } +
                    capture.fenceAttempts.map { it.scope } + capture.deletionEvidence.map { it.scope }
            ).distinct()
                .sortedBy(WorkspaceDraftScopeBundleCodec::scopeHash)
        require(scopes.size <= 256)
        val audit = MessageDigest.getInstance("SHA-256")
        audit.update(
            json
                .encodeToString(
                    listOf(
                        "BoarderLess.initial-migration.v1",
                        WorkspaceDraftScopeBundleCodec.scopeHash(target),
                        capture.counter.toString(),
                        capture.floor.toString(),
                    ),
                ).encodeToByteArray(),
        )
        var totalBytes = 0L
        val planned =
            scopes.map { scope ->
                val rawSource =
                    ScopedLegacyMigrationSource(
                        scope,
                        capture.journals.singleOrNull { it.scope == scope },
                        capture.pending.singleOrNull { it.scope == scope },
                        capture.stopped.filter { it.scope == scope }.sortedBy { it.request.transactionId },
                        capture.fenceAttempts.filter { it.scope == scope }.sortedBy { it.transactionId },
                        capture.deletionEvidence.filter { it.scope == scope }.sortedBy { it.entity },
                    )
                val raw = json.encodeToString(rawSource)
                requireBoundedDraftJsonDepth(raw)
                val bytes = raw.encodeToByteArray()
                require(bytes.size in 1..16 * 1024 * 1024)
                totalBytes += bytes.size
                require(totalBytes <= 40L * 1024 * 1024)
                val source = json.decodeFromString<ScopedLegacyMigrationSource>(raw) // Freeze caller-owned collections.
                audit.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                audit.update(bytes) // Bind ORIGINAL state, not just the normalized output.
                val safety = RecoverySafetyBundleCodec.prepareLegacy(scope, source.fences, source.deletions)
                val primary =
                    when {
                        source.pending != null &&
                            source.stopped.any {
                                it.request.transactionId == source.pending.request.transactionId
                            }
                        -> {
                            source.pending
                        }

                        source.journal?.headTransactionId != null && source.pending == null -> {
                            source.stopped.singleOrNull {
                                it.request.transactionId == source.journal.headTransactionId
                            } ?: error("Journal head is missing its exact wire")
                        }

                        else -> {
                            null
                        }
                    }
                source.journal?.headTransactionId?.let { head ->
                    require((source.pending ?: primary)?.request?.transactionId == head) { "Journal and wire heads conflict" }
                }
                var draft =
                    if (primary != null) {
                        source.pending?.let { require(it == primary) }
                        WorkspaceDraftScopeBundle(
                            scope = scope,
                            stoppedPending = primary,
                            journal = source.journal?.copy(quarantined = true, headTransactionId = null),
                            legacyImportDigest =
                                digest(
                                    json
                                        .encodeToString(
                                            WorkspaceDraftLegacySnapshot(scope, source.journal, source.pending),
                                        ).encodeToByteArray(),
                                ),
                        ).let { WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(it), scope) }
                    } else if (source.journal != null || source.pending != null) {
                        WorkspaceDraftLegacyMigration.prepare(WorkspaceDraftLegacySnapshot(scope, source.journal, source.pending))
                    } else {
                        null
                    }
                val historical = source.stopped.filter { it.request.transactionId != primary?.request?.transactionId }
                if (historical.isNotEmpty()) draft = WorkspaceDraftBundleTransitions.retainStopped(draft, scope, historical)
                val floor = CapturedClientSequenceFloor.calculate(scope, capture.counter, capture.pending, emptyList(), capture.stopped)
                PlannedLegacyScope(
                    scope,
                    draft,
                    safety,
                    floor,
                    primary != null ||
                        (source.pending != null && source.fences.any { it.transactionId == source.pending.request.transactionId }),
                )
            }
        return DesktopLegacyMigrationPlan(audit.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }, planned)
    }
}
