package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable internal data class RecoverySafetyBundle(val version: Int = 1, val scope: PendingSubmissionScope,
    val fences: List<PendingFenceAttemptRecord> = emptyList(), val deletions: List<CommittedDeletionEvidence> = emptyList(),
    val legacyImportDigest: String? = null)

/** Same wire/field order as the native v1 bundle, now usable on all platforms. */
internal object RecoverySafetyBundleCodec {
    private val json = Json { encodeDefaults = true }
    private fun validate(bundle: RecoverySafetyBundle, scope: PendingSubmissionScope) {
        WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        require(bundle.version == 1 && bundle.scope == scope)
        require(bundle.legacyImportDigest == null || bundle.legacyImportDigest.matches(Regex("[0-9a-f]{64}")))
        require(bundle.fences.size <= 256 && bundle.deletions.size <= 4096)
        require(bundle.fences.map { it.transactionId }.distinct().size == bundle.fences.size)
        require(bundle.deletions.map { it.entity }.distinct().size == bundle.deletions.size)
        bundle.fences.forEach {
            require(it.schema == 1 && it.scope == scope && it.transactionId.isNotBlank())
            require(it.wireDigest.matches(Regex("[0-9a-f]{64}")))
        }
        bundle.deletions.forEach {
            require(it.scope == scope && (it.entity.startsWith("object:") || it.entity.startsWith("relation:")) && it.entity.substringAfter(':').isNotBlank())
            require(it.transactionId.isNotBlank() && it.tombstoneVersion in 2..9_007_199_254_740_990L &&
                it.throughServerSeq in 1..9_007_199_254_740_991L && it.snapshotDigest.matches(Regex("[0-9a-f]{64}")))
        }
    }
    fun encode(bundle: RecoverySafetyBundle): ByteArray {
        val raw = json.encodeToString(bundle); requireBoundedDraftJsonDepth(raw)
        val bytes = raw.encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size in 1..6 * 1024 * 1024); validate(bundle, bundle.scope); return bytes
    }
    fun decode(bytes: ByteArray, scope: PendingSubmissionScope): RecoverySafetyBundle {
        require(bytes.size in 1..6 * 1024 * 1024)
        val raw = bytes.decodeToString(throwOnInvalidSequence = true)
        val root = parseStrictJsonObject(raw)
        root["version"]?.let { require(it == JsonPrimitive(1)) }
        (root["fences"] as? JsonArray)?.forEach { element ->
            val fence = element as? JsonObject ?: error("Invalid fence safety record")
            fence["schema"]?.let { require(it == JsonPrimitive(1)) }
        }
        (root["deletions"] as? JsonArray)?.forEach { element ->
            val deletion = element as? JsonObject ?: error("Invalid deletion safety record")
            listOf("tombstoneVersion", "throughServerSeq").forEach { field ->
                require((deletion[field] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
            }
        }
        return json.decodeFromJsonElement(RecoverySafetyBundle.serializer(), root).also { validate(it, scope) }
    }
    fun prepareLegacy(scope: PendingSubmissionScope, fences: List<PendingFenceAttemptRecord>, deletions: List<CommittedDeletionEvidence>): RecoverySafetyBundle {
        val source = RecoverySafetyBundle(scope = scope, fences = fences.sortedBy { it.transactionId }, deletions = deletions.sortedBy { it.entity })
        val bytes = encode(source)
        val fingerprint = SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return decode(encode(decode(bytes, scope).copy(legacyImportDigest = fingerprint)), scope)
    }
}
