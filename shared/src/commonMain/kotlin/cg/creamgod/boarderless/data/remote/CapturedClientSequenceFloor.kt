package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Computes a lower bound from captured sources, NOT proof that the capture is exhaustive.
 * Caller must still verify scope inventory, exclude old writers, and account for existing ledger
 * records before initialization. Never maps a missing legacy counter to zero; no storage writes.
 */
internal object CapturedClientSequenceFloor {
    private const val Maximum = 9_007_199_254_740_991L
    private val json = Json { encodeDefaults = true }

    fun calculate(
        scope: PendingSubmissionScope,
        legacyCounter: Long?,
        legacyPending: List<PendingWorkspaceSubmission>,
        bundles: List<WorkspaceDraftScopeBundle>,
    ): Long {
        WorkspaceDraftScopeBundleCodec.scopeHash(scope) // Validate namespace components.
        var floor = requireNotNull(legacyCounter) { "Missing legacy counter requires explicit recovery" }
        require(floor in 0..Maximum)
        require(legacyPending.size <= 256 && bundles.size <= 256)
        require(legacyPending.map { it.scope }.distinct().size == legacyPending.size)
        require(bundles.map { it.scope }.distinct().size == bundles.size)

        fun include(entry: PendingWorkspaceSubmission) {
            validatePendingWorkspaceSubmission(entry)
            if (entry.scope.apiBase == scope.apiBase && entry.scope.userId == scope.userId &&
                entry.scope.clientId == scope.clientId) {
                floor = maxOf(floor, entry.request.operations.last().clientSeq)
            }
        }
        var wireBytes = 0L
        legacyPending.forEach { entry ->
            val content = json.encodeToString(entry)
            requireBoundedDraftJsonDepth(content)
            val size = content.encodeToByteArray().size
            require(size in 1..1024 * 1024)
            wireBytes += size
            require(wireBytes <= 4L * 1024 * 1024)
            include(json.decodeFromString<PendingWorkspaceSubmission>(content))
        }
        var bytes = 0L
        bundles.forEach { bundle ->
            // Validate even foreign namespaces: corrupt evidence is never silently skipped.
            val encoded = WorkspaceDraftScopeBundleCodec.encode(bundle)
            bytes += encoded.size
            require(bytes <= 12L * 1024 * 1024) { "Captured bundles exceed floor audit limit" }
            val frozen = WorkspaceDraftScopeBundleCodec.decode(encoded, bundle.scope)
            frozen.pending?.let(::include)
            frozen.acknowledged?.submitted?.let(::include)
            frozen.stoppedPending?.let(::include)
        }
        return floor // serverSeq/baseVersion are NOT client sequence numbers.
    }
}
