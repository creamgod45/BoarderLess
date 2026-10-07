package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
internal data class PendingSubmissionScope(
    val apiBase: String,
    val userId: String,
    val clientId: String,
    val workspaceId: String,
)

internal data class StoppedSubmissionLocator(
    val scope: PendingSubmissionScope,
    val transactionId: String,
)

@Serializable
internal data class PendingWorkspaceSubmission(
    val scope: PendingSubmissionScope,
    val localOperationId: String,
    val request: SubmitOperationsRequest,
    // Captured before transport, including relation cascades. Old records cannot authorize restore.
    val deletionVersions: Map<String, Long> = emptyMap(),
    val deletionSnapshotDigests: Map<String, String> = emptyMap(),
)

/** Persistent wire identity foundation, not an automatic resend policy.
 * Write chunks first and publish one manifest last. Never replace an unresolved transaction.
 * Settings persistence does not claim fsync/power-loss durability on every platform.
 */
internal class PendingWorkspaceSubmissionStore(
    private val settings: Settings = Settings(),
    private val archivedTransactionId: String? = null,
) {
    private val json = Json { encodeDefaults = true }

    /** Local stop is not a server receipt. Retain exact wire and restore provenance before
     * removing the retry slot; no archive is ever automatically acknowledged or reclaimed.
     * Readback verifies Settings publication, not native fsync or writer exclusion.
     */
    fun archiveStopped(entry: PendingWorkspaceSubmission) {
        check(archivedTransactionId == null)
        val archive = PendingWorkspaceSubmissionStore(settings, entry.request.transactionId)
        archive.save(entry)
        check(archive.load(entry.scope) == entry) { "Stopped submission archive was not saved" }
    }

    fun loadStopped(
        scope: PendingSubmissionScope,
        transactionId: String,
    ): PendingWorkspaceSubmission? {
        check(archivedTransactionId == null)
        require(transactionId.isNotBlank())
        return PendingWorkspaceSubmissionStore(settings, transactionId).load(scope)
    }

    /** Bounded discovery; pre-catalog archives require an exact caller-supplied legacy ID.
     * Never silently omit an undiscoverable record or expose another scope's wire.
     */
    fun stoppedInventory(
        scope: PendingSubmissionScope,
        legacyTransactionIds: List<String> = emptyList(),
    ): List<PendingWorkspaceSubmission> {
        check(archivedTransactionId == null)
        require(legacyTransactionIds.size <= 256 && legacyTransactionIds.all(String::isNotBlank))
        val legacy = legacyTransactionIds.distinct().associateBy { PendingWorkspaceSubmissionStore(settings, it).key(scope) }
        val keys = settings.keys.filter { it.startsWith("wsa1.") }.sorted()
        check(keys.size <= 256) { "Stopped submission catalog requires recovery" }
        var bytes = 0L
        val selected =
            keys.mapNotNull { manifestKey ->
                val raw = checkNotNull(settings.getStringOrNull(manifestKey))
                check(raw.length in 1..MaximumArchiveManifestCharacters)
                requireBoundedDraftJsonDepth(raw)
                val manifest = json.decodeFromString<Manifest>(raw)
                check(manifest.version == 1 && manifest.chunks in 1..MaximumChunks && manifest.byteSize in 1..MaximumBytes)
                check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
                val archivedScope = manifest.archiveScope
                val transaction = manifest.archiveTransactionId
                if (archivedScope == null && transaction == null) {
                    check(legacy.containsKey(manifestKey)) { "Legacy stopped submission catalog is incomplete" }
                } else {
                    check(archivedScope != null && !transaction.isNullOrBlank())
                    check(
                        listOf(
                            archivedScope.apiBase,
                            archivedScope.userId,
                            archivedScope.clientId,
                            archivedScope.workspaceId,
                        ).all(String::isNotBlank),
                    )
                    check(PendingWorkspaceSubmissionStore(settings, transaction).key(archivedScope) == manifestKey)
                    if (archivedScope != scope) return@mapNotNull null
                }
                bytes += manifest.byteSize
                check(bytes <= 4L * MaximumBytes) { "Stopped submission inventory exceeds read limit" }
                transaction ?: legacy.getValue(manifestKey)
            }
        return selected.map { checkNotNull(loadStopped(scope, it)) }.sortedBy { it.request.transactionId }
    }

    /** Complete all-scope source for migration/floor audit, not a writer lock or activation.
     * Caller must exclude legacy writers throughout capture. Rechecks catch observed drift only.
     */
    fun stoppedInventoryForMigration(legacyCatalog: List<StoppedSubmissionLocator>): List<PendingWorkspaceSubmission> {
        check(archivedTransactionId == null)
        try {
            require(legacyCatalog.size <= 256 && legacyCatalog.distinct().size == legacyCatalog.size)

            fun validateLocator(locator: StoppedSubmissionLocator) {
                require(
                    (
                        listOf(
                            locator.scope.apiBase,
                            locator.scope.userId,
                            locator.scope.clientId,
                            locator.scope.workspaceId,
                            locator.transactionId,
                        )
                    ).all { it.isNotBlank() && it.length <= 4096 },
                )
            }
            legacyCatalog.forEach(::validateLocator)
            val legacy = legacyCatalog.associateBy { PendingWorkspaceSubmissionStore(settings, it.transactionId).key(it.scope) }
            check(legacy.size == legacyCatalog.size)
            val keys = settings.keys.filter { it.startsWith("wsa1.") }.sorted()
            check(keys.size <= 256 && keys.containsAll(legacy.keys)) { "Stopped archive catalog is incomplete" }
            var bytes = 0L
            val captured =
                keys.map { key ->
                    val raw = checkNotNull(settings.getStringOrNull(key))
                    check(raw.length in 1..MaximumArchiveManifestCharacters)
                    requireBoundedDraftJsonDepth(raw)
                    val manifest = json.decodeFromString<Manifest>(raw)
                    check(manifest.version == 1 && manifest.chunks in 1..MaximumChunks && manifest.byteSize in 1..MaximumBytes)
                    check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
                    val locator =
                        if (manifest.archiveScope == null && manifest.archiveTransactionId == null) {
                            checkNotNull(legacy[key]) { "Legacy stopped archive catalog is incomplete" }
                        } else {
                            StoppedSubmissionLocator(checkNotNull(manifest.archiveScope), checkNotNull(manifest.archiveTransactionId))
                        }
                    validateLocator(locator)
                    check(PendingWorkspaceSubmissionStore(settings, locator.transactionId).key(locator.scope) == key)
                    legacy[key]?.let { check(it == locator) }
                    bytes += manifest.byteSize
                    check(bytes <= 4L * MaximumBytes) { "Stopped archive capture exceeds read limit" }
                    Triple(key, raw, locator)
                }
            val entries = captured.map { (_, _, locator) -> checkNotNull(loadStopped(locator.scope, locator.transactionId)) }
            check(settings.keys.filter { it.startsWith("wsa1.") }.sorted() == keys) { "Stopped archive catalog changed during capture" }
            captured.forEach { (key, raw, _) -> check(settings.getStringOrNull(key) == raw) { "Stopped archive changed during capture" } }
            return entries // Every namespace is validated; caller filters only after complete capture.
        } catch (failure: Exception) {
            throw BackendContractException("Stopped archive migration inventory requires recovery", failure)
        }
    }

    /** Migration-only read. Caller must exclude ALL legacy writers before and during capture.
     * Hashed v1 manifests contain no discoverable scope: reject an incomplete external catalog,
     * rather than silently omit another workspace's exact wire from the sequence floor.
     * This does not prove writer exclusion or include typed receipts/stopped archives/Settings counter.
     */
    fun inventoryForMigration(knownScopes: List<PendingSubmissionScope>): List<PendingWorkspaceSubmission> {
        check(archivedTransactionId == null) // This legacy inventory does not include stopped archives.
        try {
            require(knownScopes.size <= 256 && knownScopes.distinct().size == knownScopes.size)
            require(
                knownScopes.all { scope ->
                    listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId).all(String::isNotBlank)
                },
            )
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
            check(raw.length in 1..if (archivedTransactionId == null) MaximumManifestCharacters else MaximumArchiveManifestCharacters)
            requireBoundedDraftJsonDepth(raw)
            val manifest = json.decodeFromString<Manifest>(raw)
            check(manifest.version == 1 && manifest.chunks in 1..MaximumChunks && manifest.byteSize in 1..MaximumBytes)
            check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
            check(
                (manifest.archiveScope == null && manifest.archiveTransactionId == null) ||
                    (
                        archivedTransactionId != null && manifest.archiveScope == scope &&
                            manifest.archiveTransactionId == archivedTransactionId
                    ),
            )
            val content =
                buildString {
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
            val chunkKey = chunkKey(entry.scope, hash, index++)
            val chunk = content.substring(offset, end)
            settings.putString(chunkKey, chunk)
            check(settings.getStringOrNull(chunkKey) == chunk) { "Pending submission chunk was not saved" }
            offset = end
        }
        // Keep the original four-field active manifest readable by older strict v1 decoders.
        val manifest =
            if (archivedTransactionId == null) {
                buildJsonObject {
                    put("version", 1)
                    put("chunks", index)
                    put("byteSize", byteSize)
                    put("digest", hash)
                }.toString()
            } else {
                json.encodeToString(Manifest(1, index, byteSize, hash, entry.scope, archivedTransactionId))
            }
        check(manifest.length <= if (archivedTransactionId == null) MaximumManifestCharacters else MaximumArchiveManifestCharacters)
        settings.putString(key(entry.scope), manifest)
        check(settings.getStringOrNull(key(entry.scope)) == manifest) { "Pending submission manifest was not saved" }
        check(load(entry.scope) == entry) { "Pending submission publication could not be verified" }
    }

    /** A matching validated server outcome may remove this exact transaction, never another one. */
    fun acknowledge(
        scope: PendingSubmissionScope,
        transactionId: String,
    ): Boolean {
        check(archivedTransactionId == null) { "Stopped submission evidence must not be acknowledged locally" }
        val pending = load(scope) ?: return false
        if (pending.request.transactionId != transactionId) return false
        val manifest = json.decodeFromString<Manifest>(checkNotNull(settings.getStringOrNull(key(scope))))
        settings.remove(key(scope))
        repeat(manifest.chunks) { settings.remove(chunkKey(scope, manifest.digest, it)) }
        return true
    }

    private fun validate(entry: PendingWorkspaceSubmission) {
        validatePendingWorkspaceSubmission(entry)
        check(archivedTransactionId == null || entry.request.transactionId == archivedTransactionId)
    }

    private fun storageIdentity(scope: PendingSubmissionScope): String =
        if (archivedTransactionId == null) {
            json.encodeToString(scope)
        } else {
            json.encodeToString(scope) + ":" + archivedTransactionId
        }

    private fun key(scope: PendingSubmissionScope): String =
        (if (archivedTransactionId == null) "wsp1." else "wsa1.") + digest(storageIdentity(scope))

    private fun chunkKey(
        scope: PendingSubmissionScope,
        hash: String,
        index: Int,
    ): String = (if (archivedTransactionId == null) "wsc1." else "wsac1.") + digest(storageIdentity(scope) + ":" + hash + ":" + index)

    private fun digest(content: String): String =
        SHA256()
            .digest(content.encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Serializable private data class Manifest(
        val version: Int,
        val chunks: Int,
        val byteSize: Int,
        val digest: String,
        val archiveScope: PendingSubmissionScope? = null,
        val archiveTransactionId: String? = null,
    )

    private companion object {
        const val ChunkCharacters = 2048
        const val MaximumBytes = 1024 * 1024
        const val MaximumChunks = 513
        const val MaximumManifestCharacters = 512
        const val MaximumArchiveManifestCharacters = 4096
    }
}

internal fun validatePendingWorkspaceSubmission(entry: PendingWorkspaceSubmission) {
    val scope = entry.scope
    require(listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId, entry.localOperationId).all { it.isNotBlank() })
    require(entry.request.clientId == scope.clientId && entry.request.transactionId.isNotBlank())
    require(entry.request.protocolVersion == 1 && entry.request.baseVersion in 0..9_007_199_254_740_991L)
    require(entry.request.operations.size in 1..200)
    require(
        entry.request.operations.all {
            it.operationId.isNotBlank() && it.clientSeq in 0..9_007_199_254_740_991L &&
                it.kind.isNotBlank()
        },
    )
    require(
        entry.request.operations
            .map { it.operationId }
            .distinct()
            .size == entry.request.operations.size,
    )
    require(
        entry.request.operations
            .zipWithNext()
            .all { (first, next) -> next.clientSeq == first.clientSeq + 1 },
    )
    entry.request.operations
        .filter { it.kind.startsWith("restore_") }
        .forEach(::validateRestorationWire)
    require(
        entry.deletionVersions.size <= 100_000 &&
            entry.deletionVersions.all { (id, version) ->
                (id.startsWith("object:") || id.startsWith("relation:")) && id.length in 8..256 &&
                    version in 2..9_007_199_254_740_990L
            },
    )
    require(
        entry.deletionSnapshotDigests.keys == entry.deletionVersions.keys &&
            entry.deletionSnapshotDigests.values.all { it.matches(Regex("[0-9a-f]{64}")) },
    )
}
