package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

internal data class LegacyMigrationScopeSummary(val scope: PendingSubmissionScope, val scopeHash: String,
    val draftOperationCount: Int, val hasPendingWire: Boolean, val stoppedWireCount: Int,
    val fenceCount: Int, val deletionEvidenceCount: Int, val minimumClientSequence: Long, val retryQuarantined: Boolean)

/** Frozen local review packet. Summaries are NOT proof of exhaustive capture or exclusion
 * of other writers, nor permission to resend or switch the active repository. Actual
 * publication requires an explicit caller confirmation and a separate activation receipt. */
internal class LegacySubmissionMigrationReview(plan: LegacySubmissionMigrationPlan) {
    private val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
    private val serialized: String = json.encodeToString(LegacySubmissionMigrationPlan.serializer(), plan)
    private val frozen: LegacySubmissionMigrationPlan
    val sourceDigest: String
    val planDigest: String
    val scopes: List<LegacyMigrationScopeSummary>
    init {
        val bytes = serialized.encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..64 * 1024 * 1024)
        frozen = json.decodeFromJsonElement(LegacySubmissionMigrationPlan.serializer(), parseStrictJsonObject(serialized))
        require(frozen.sourceDigest.matches(Regex("[0-9a-f]{64}")))
        require(frozen.scopes.size in 1..256 && frozen.scopes.map { it.scope }.distinct().size == frozen.scopes.size)
        frozen.scopes.forEach { planned ->
            WorkspaceDraftScopeBundleCodec.scopeHash(planned.scope)
            planned.draft?.let { require(it.scope == planned.scope); WorkspaceDraftScopeBundleCodec.encode(it) }
            require(planned.safety.scope == planned.scope); RecoverySafetyBundleCodec.encode(planned.safety)
            require(planned.minimumClientSequence in 0..RealtimeMaxSequence)
        }
        sourceDigest = frozen.sourceDigest
        planDigest = SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        scopes = frozen.scopes.map { planned -> LegacyMigrationScopeSummary(planned.scope,
            WorkspaceDraftScopeBundleCodec.scopeHash(planned.scope), planned.draft?.journal?.operations?.size ?: 0,
            planned.draft?.pending != null, (if (planned.draft?.stoppedPending != null) 1 else 0) + planned.draft?.retainedStopped.orEmpty().size,
            planned.safety.fences.size, planned.safety.deletions.size, planned.minimumClientSequence, planned.retryQuarantined) }
    }
    /** Re-capture under real writer exclusion. A matching summary/digest alone is not
     * enough: compare the complete normalized output before returning a fresh frozen copy. */
    fun requireUnchanged(fresh: LegacySubmissionMigrationPlan): LegacySubmissionMigrationPlan {
        require(fresh.sourceDigest == sourceDigest && json.encodeToString(LegacySubmissionMigrationPlan.serializer(), fresh) == serialized) {
            "Migration source or plan changed; review again"
        }
        return json.decodeFromJsonElement(LegacySubmissionMigrationPlan.serializer(), parseStrictJsonObject(serialized))
    }
    internal fun encode(): ByteArray = serialized.encodeToByteArray(throwOnInvalidSequence = true)
    /** Full receipt-bound scope packet for local review only. Never recapture, read storage,
     * repair content or expose this packet to telemetry while navigating the review. */
    fun scopePacket(index: Int): String = json.encodeToString(PlannedLegacyScope.serializer(), frozen.scopes[index])
    companion object {
        internal fun decode(bytes: ByteArray): LegacySubmissionMigrationReview {
            require(bytes.size in 1..64 * 1024 * 1024)
            val raw = bytes.decodeToString(throwOnInvalidSequence = true)
            val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
            val review = LegacySubmissionMigrationReview(json.decodeFromJsonElement(
                LegacySubmissionMigrationPlan.serializer(), parseStrictJsonObject(raw)))
            require(review.encode().contentEquals(bytes)) { "Stored review is not the original normalized packet" }
            return review
        }
    }
}
