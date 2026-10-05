package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
internal data class PendingSubmissionScope(val apiBase: String, val userId: String, val clientId: String, val workspaceId: String)

@Serializable
internal data class PendingWorkspaceSubmission(
    val scope: PendingSubmissionScope,
    val localOperationId: String,
    val request: SubmitOperationsRequest,
)

/** Persistent wire identity foundation, not an automatic resend policy.
 * Write chunks first and publish one manifest last. Never replace an unresolved transaction.
 * Settings persistence does not claim fsync/power-loss durability on every platform.
 */
internal class PendingWorkspaceSubmissionStore(private val settings: Settings = Settings()) {
    private val json = Json { encodeDefaults = true }

    /** Migration-only read. Caller must exclude ALL legacy writers before and during capture.
     * Hashed v1 manifests contain no discoverable scope: reject an incomplete external catalog,
     * rather than silently omit another workspace's exact wire from the sequence floor.
     * This does not prove writer exclusion or include typed receipts/stopped archives/Settings counter.
     */
    fun inventoryForMigration(knownScopes: List<PendingSubmissionScope>): List<PendingWorkspaceSubmission> {
        try {
            require(knownScopes.size <= 256 && knownScopes.distinct().size == knownScopes.size)
            require(knownScopes.all { scope ->
                listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId).all(String::isNotBlank)
            })
            val catalog = knownScopes.associateBy(::key)
            check(catalog.size == knownScopes.size)
            val published = settings.keys.filter { it.startsWith("wsp1.") }.sorted()
            check(published.size <= 256 && published.all(catalog::containsKey)) {
                "Legacy pending scope catalog is incomplete"
            }
            // Bound aggregate allocation BEFORE reading any chunk. No orphan chunk reclaim here.
            var totalBytes = 0L
            published.forEach { manifestKey ->
                val raw = checkNotNull(settings.getStringOrNull(manifestKey))
                check(raw.length in 1..MaximumManifestCharacters)
                requireBoundedDraftJsonDepth(raw)
                val manifest = json.decodeFromString<Manifest>(raw)
                check(manifest.version == 1 && manifest.chunks in 1..MaximumChunks && manifest.byteSize in 1..MaximumBytes)
                check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
                totalBytes += manifest.byteSize
                check(totalBytes <= 4L * MaximumBytes) { "Legacy pending inventory exceeds capture limit" }
            }
            return published.map { manifestKey -> checkNotNull(load(catalog.getValue(manifestKey))) }
        } catch (failure: Exception) {
            throw BackendContractException("Legacy pending inventory requires recovery", failure)
        }
    }

    fun load(scope: PendingSubmissionScope): PendingWorkspaceSubmission? {
        val raw = settings.getStringOrNull(key(scope)) ?: return null
        return try {
            check(raw.length in 1..MaximumManifestCharacters)
            requireBoundedDraftJsonDepth(raw)
            val manifest = json.decodeFromString<Manifest>(raw)
            check(manifest.version == 1 && manifest.chunks in 1..MaximumChunks && manifest.byteSize in 1..MaximumBytes)
            check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
            val content = buildString {
                var bytes = 0
                repeat(manifest.chunks) { index ->
                    val chunk = checkNotNull(settings.getStringOrNull(chunkKey(scope, manifest.digest, index)))
                    check(chunk.length in 1..ChunkCharacters)
                    bytes += chunk.encodeToByteArray().size
                    check(bytes <= manifest.byteSize)
                    append(chunk)
                }
            }
            check(content.encodeToByteArray().size == manifest.byteSize && digest(content) == manifest.digest)
            requireBoundedDraftJsonDepth(content)
            json.decodeFromString<PendingWorkspaceSubmission>(content).also {
                check(it.scope == scope)
                validate(it)
            }
        } catch (failure: Exception) {
            throw BackendContractException("Pending workspace submission cannot be read", failure)
        }
    }

    fun save(entry: PendingWorkspaceSubmission) {
        validate(entry)
        load(entry.scope)?.let { existing ->
            check(existing == entry) { "An unresolved workspace submission already exists" }
            return
        }
        val content = json.encodeToString(entry)
        val byteSize = content.encodeToByteArray().size
        require(byteSize in 1..MaximumBytes) { "Pending workspace submission exceeds storage limit" }
        requireBoundedDraftJsonDepth(content)
        val hash = digest(content)
        var offset = 0
        var index = 0
        while (offset < content.length) {
            var end = (offset + ChunkCharacters).coerceAtMost(content.length)
            // Java Preferences XML must never receive an isolated UTF-16 surrogate.
            if (end < content.length && content[end - 1] in '\uD800'..'\uDBFF') end--
            settings.putString(chunkKey(entry.scope, hash, index++), content.substring(offset, end))
            offset = end
        }
        settings.putString(key(entry.scope), json.encodeToString(Manifest(1, index, byteSize, hash)))
    }

    /** A matching validated server outcome may remove this exact transaction, never another one. */
    fun acknowledge(scope: PendingSubmissionScope, transactionId: String): Boolean {
        val pending = load(scope) ?: return false
        if (pending.request.transactionId != transactionId) return false
        val manifest = json.decodeFromString<Manifest>(checkNotNull(settings.getStringOrNull(key(scope))))
        settings.remove(key(scope))
        repeat(manifest.chunks) { settings.remove(chunkKey(scope, manifest.digest, it)) }
        return true
    }

    private fun validate(entry: PendingWorkspaceSubmission) = validatePendingWorkspaceSubmission(entry)

    private fun key(scope: PendingSubmissionScope): String = "wsp1." + digest(json.encodeToString(scope))
    private fun chunkKey(scope: PendingSubmissionScope, hash: String, index: Int): String =
        "wsc1." + digest(json.encodeToString(scope) + ":" + hash + ":" + index)
    private fun digest(content: String): String = SHA256().digest(content.encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Serializable private data class Manifest(val version: Int, val chunks: Int, val byteSize: Int, val digest: String)
    private companion object {
        const val ChunkCharacters = 2048
        const val MaximumBytes = 1024 * 1024
        const val MaximumChunks = 513
        const val MaximumManifestCharacters = 512
    }
}

internal fun validatePendingWorkspaceSubmission(entry: PendingWorkspaceSubmission) {
        val scope = entry.scope
        require(listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId, entry.localOperationId).all { it.isNotBlank() })
        require(entry.request.clientId == scope.clientId && entry.request.transactionId.isNotBlank())
        require(entry.request.protocolVersion == 1 && entry.request.baseVersion in 0..9_007_199_254_740_991L)
        require(entry.request.operations.size in 1..200)
        require(entry.request.operations.all { it.operationId.isNotBlank() && it.clientSeq in 0..9_007_199_254_740_991L && it.kind.isNotBlank() })
        require(entry.request.operations.map { it.operationId }.distinct().size == entry.request.operations.size)
        require(entry.request.operations.zipWithNext().all { (first, next) -> next.clientSeq == first.clientSeq + 1 })
    }
