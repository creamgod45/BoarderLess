package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable internal enum class AwaitedWorkspaceEnrollmentPhase { Preparing, Ready }
@Serializable internal data class AwaitedWorkspaceEnrollment(
    val scope: PendingSubmissionScope,
    val phase: AwaitedWorkspaceEnrollmentPhase,
)
@Serializable internal data class AwaitedSubmissionActivationRecord(
    val version: Int = 2,
    val target: PendingSubmissionScope,
    val publicationGeneration: Long,
    val sourceDigest: String,
    val planDigest: String,
    val enrollments: List<AwaitedWorkspaceEnrollment> = emptyList(),
) {
    fun samePublication(other: AwaitedSubmissionActivationRecord): Boolean =
        copy(version = 2, enrollments = emptyList()) == other.copy(version = 2, enrollments = emptyList())
}
internal data class AwaitedSubmissionActivationReceipt(
    val generation: Long,
    val record: AwaitedSubmissionActivationRecord,
)

/** One control record binds the original publication and append-only workspace enrollment.
 * Reading v1 never rewrites it. The first explicit enrollment upgrades it by CAS; a missing
 * v2 field or missing control is never interpreted as an empty enrollment catalog. */
internal class AwaitedSubmissionActivationStore(
    private val files: AwaitedDurableLocalRecordStore,
    private val requireActive: (PendingSubmissionScope) -> Unit,
) {
    private val json = Json { encodeDefaults = true }
    private fun key(target: PendingSubmissionScope) = SHA256().digest(
        "boarderless.awaited-submission-activation.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(target)}".encodeToByteArray(),
    ).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private suspend fun guard(target: PendingSubmissionScope) {
        currentCoroutineContext().ensureActive()
        requireActive(target)
    }

    private fun validate(receipt: AwaitedSubmissionActivationReceipt, target: PendingSubmissionScope) {
        val value = receipt.record
        require(value.version in 1..2 && value.target == target)
        WorkspaceDraftScopeBundleCodec.scopeHash(target)
        require(value.publicationGeneration in 1 until RealtimeMaxSequence)
        require(value.sourceDigest.matches(Regex("[0-9a-f]{64}")) && value.planDigest.matches(Regex("[0-9a-f]{64}")))
        require(value.enrollments.size <= 255 && value.enrollments.map { it.scope }.distinct().size == value.enrollments.size)
        require(value.version == 2 || value.enrollments.isEmpty())
        require(value.enrollments.dropLast(1).none { it.phase == AwaitedWorkspaceEnrollmentPhase.Preparing })
        value.enrollments.forEach {
            WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
            require(it.scope != target && it.scope.apiBase == target.apiBase &&
                it.scope.userId == target.userId && it.scope.clientId == target.clientId)
        }
        val preparing = value.enrollments.count { it.phase == AwaitedWorkspaceEnrollmentPhase.Preparing }
        require(receipt.generation == 1L + value.enrollments.size * 2L - preparing)
    }

    suspend fun read(target: PendingSubmissionScope): AwaitedSubmissionActivationReceipt? {
        guard(target)
        val stored = files.read(key(target))
        guard(target)
        if (stored == null) return null
        require(stored.payload.size in 1..1024 * 1024)
        val raw = parseStrictJsonObject(stored.payload.decodeToString(throwOnInvalidSequence = true))
        fun requireScopeObject(element: JsonElement?) {
            val scope = requireNotNull(element as? JsonObject)
            require(scope.keys == setOf("apiBase", "userId", "clientId", "workspaceId"))
            require(scope.values.all { it is JsonPrimitive && it.isString })
        }
        requireScopeObject(raw["target"])
        val baseKeys = setOf("version", "target", "publicationGeneration", "sourceDigest", "planDigest")
        when (raw["version"]) {
            JsonPrimitive(1) -> require(raw.keys == baseKeys)
            JsonPrimitive(2) -> {
                require(raw.keys == baseKeys + "enrollments")
                val entries = requireNotNull(raw["enrollments"] as? JsonArray)
                entries.forEach { element ->
                    val entry = requireNotNull(element as? JsonObject)
                    require(entry.keys == setOf("scope", "phase"))
                    requireScopeObject(entry["scope"])
                    require((entry["phase"] as? JsonPrimitive)?.isString == true)
                }
            }
            else -> error("Unsupported activation control version")
        }
        require((raw["publicationGeneration"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
        return AwaitedSubmissionActivationReceipt(stored.generation,
            json.decodeFromJsonElement(AwaitedSubmissionActivationRecord.serializer(), raw)).also { validate(it, target) }
    }

    suspend fun confirm(target: PendingSubmissionScope, receipt: AwaitedSubmissionActivationReceipt) {
        validate(receipt, target)
        guard(target)
        require(read(target) == receipt)
        files.confirmDurable(key(target), receipt.generation)
        guard(target)
        require(read(target) == receipt) { "Activation control changed during confirmation" }
    }

    /** Only initial publication, append Preparing, or completion of that final entry is legal. */
    suspend fun save(target: PendingSubmissionScope, before: AwaitedSubmissionActivationReceipt?,
        next: AwaitedSubmissionActivationRecord): AwaitedSubmissionActivationReceipt {
        require(next.version == 2)
        if (before == null) require(next.enrollments.isEmpty())
        else {
            require(before.record.samePublication(next))
            val previous = before.record.enrollments
            val appended = next.enrollments.size == previous.size + 1 &&
                previous.none { it.phase == AwaitedWorkspaceEnrollmentPhase.Preparing } &&
                next.enrollments.dropLast(1) == previous && next.enrollments.last().phase == AwaitedWorkspaceEnrollmentPhase.Preparing
            val completed = previous.lastOrNull()?.phase == AwaitedWorkspaceEnrollmentPhase.Preparing &&
                next.enrollments == previous.dropLast(1) + previous.last().copy(phase = AwaitedWorkspaceEnrollmentPhase.Ready)
            require(appended || completed) { "Activation enrollment cannot be replaced or rewound" }
        }
        val receipt = AwaitedSubmissionActivationReceipt((before?.generation ?: 0L) + 1, next)
        validate(receipt, target)
        val bytes = json.encodeToString(AwaitedSubmissionActivationRecord.serializer(), next).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..1024 * 1024)
        guard(target)
        require(read(target) == before)
        files.compareAndSet(key(target), before?.generation, bytes)
        guard(target)
        confirm(target, receipt)
        return receipt
    }
}
