package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
internal data class WorkspaceDraftJournal(
    val version: Int = 1,
    val scope: PendingSubmissionScope,
    val id: String,
    val baseVersion: Long,
    val baseServerSeq: Long,
    val baseWorkspace: Workspace,
    val operations: List<WorkspaceOperation>,
    val headTransactionId: String? = null,
    val lastAcknowledgedTransactionId: String? = null,
    val quarantined: Boolean = false,
) {
    fun replay(): Workspace =
        operations.fold(baseWorkspace) { workspace, operation ->
            (operation.applyTo(workspace) as? OperationResult.Applied)?.workspace
                ?: error("Draft operations no longer match their saved baseline")
        }
}

/** Local intentions, separate from the exact submitted wire record. No automatic replay.
 * Chunk publication is manifest-last; Settings does not promise power-loss fsync.
 */
internal class WorkspaceDraftJournalStore(
    private val settings: Settings,
) {
    private val json =
        Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
        }

    /** Quiescent migration capture only. Hashed manifests require a complete external scope
     * catalog, including drafts that have never had a pending submission. No writes or GC.
     * Observed drift is rejected; this does not exclude a non-cooperating legacy writer.
     */
    fun inventoryForMigration(knownScopes: List<PendingSubmissionScope>): List<WorkspaceDraftJournal> {
        try {
            require(knownScopes.size <= 256 && knownScopes.distinct().size == knownScopes.size)
            knownScopes.forEach(WorkspaceDraftScopeBundleCodec::scopeHash)
            val catalog = knownScopes.associateBy(::key)
            check(catalog.size == knownScopes.size)
            val keys = settings.keys.filter { it.startsWith("wdj1.") }.sorted()
            check(keys.size <= 256 && keys.all(catalog::containsKey)) { "Legacy draft catalog is incomplete" }
            var totalBytes = 0L
            val manifests =
                keys.associateWith { manifestKey ->
                    val raw = checkNotNull(settings.getStringOrNull(manifestKey))
                    check(raw.length in 1..MaximumManifestCharacters)
                    requireBoundedDraftJsonDepth(raw)
                    val manifest = json.decodeFromString<Manifest>(raw)
                    check(manifest.version == 1 && manifest.chunks in 1..2049 && manifest.bytes in 1..MaximumBytes)
                    check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
                    totalBytes += manifest.bytes
                    check(totalBytes <= 4L * MaximumBytes) { "Legacy draft inventory exceeds capture limit" }
                    raw
                }
            val journals = keys.map { checkNotNull(load(catalog.getValue(it))) }
            check(settings.keys.filter { it.startsWith("wdj1.") }.sorted() == keys) { "Legacy draft catalog changed" }
            manifests.forEach { (manifestKey, raw) -> check(settings.getStringOrNull(manifestKey) == raw) }
            return journals
        } catch (failure: Exception) {
            throw BackendContractException("Legacy draft migration inventory requires recovery", failure)
        }
    }

    fun load(scope: PendingSubmissionScope): WorkspaceDraftJournal? {
        val raw = settings.getStringOrNull(key(scope)) ?: return null
        try {
            check(raw.length in 1..MaximumManifestCharacters)
            requireBoundedDraftJsonDepth(raw)
            val manifest = json.decodeFromString<Manifest>(raw)
            check(manifest.version == 1 && manifest.chunks in 1..2049 && manifest.bytes in 1..MaximumBytes)
            check(manifest.digest.matches(Regex("[0-9a-f]{64}")))
            val content =
                buildString {
                    var bytes = 0
                    repeat(manifest.chunks) { index ->
                        val chunk = checkNotNull(settings.getStringOrNull(chunkKey(scope, manifest.digest, index)))
                        check(chunk.length in 1..ChunkCharacters)
                        bytes += chunk.encodeToByteArray().size
                        check(bytes <= manifest.bytes)
                        append(chunk)
                    }
                }
            check(content.encodeToByteArray().size == manifest.bytes && digest(content) == manifest.digest)
            requireBoundedDraftJsonDepth(content)
            return json.decodeFromString<WorkspaceDraftJournal>(content).also {
                check(it.scope == scope)
                validate(it)
            }
        } catch (failure: Exception) {
            throw BackendContractException("Saved workspace draft cannot be read", failure)
        }
    }

    fun append(
        scope: PendingSubmissionScope,
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) {
        check(before.id.value == scope.workspaceId && session.workspace.id == before.id)
        check(session.userId == scope.userId && session.clientId == scope.clientId)
        val current =
            load(scope) ?: WorkspaceDraftJournal(
                scope = scope,
                id = randomUuid(),
                baseVersion = session.workspaceVersion,
                baseServerSeq = session.lastServerSeq,
                baseWorkspace = before,
                operations = emptyList(),
            )
        check(!current.quarantined && current.replay() == before) { "Saved draft requires review first" }
        check(current.operations.size < 200)
        check(operation.operationId.isNotBlank())
        check(operation.applyTo(before) is OperationResult.Applied)
        save(current.copy(operations = current.operations + operation))
    }

    fun markSubmitted(
        scope: PendingSubmissionScope,
        operationId: String,
        transactionId: String,
    ) {
        val current = load(scope) ?: return // Existing legacy wire-only records remain supported.
        if (current.lastAcknowledgedTransactionId == transactionId) return
        check(!current.quarantined && current.operations.firstOrNull()?.operationId == operationId)
        check(current.headTransactionId == null || current.headTransactionId == transactionId)
        save(current.copy(headTransactionId = transactionId))
    }

    /** Publish advancement BEFORE deleting the corresponding wire record. Idempotent across that crash boundary. */
    fun acknowledge(
        scope: PendingSubmissionScope,
        operationId: String,
        transactionId: String,
        version: Long,
        seq: Long,
    ) {
        val current = load(scope) ?: return
        if (current.lastAcknowledgedTransactionId == transactionId) return
        check(current.headTransactionId == transactionId && current.operations.firstOrNull()?.operationId == operationId)
        check(version > current.baseVersion && seq > current.baseServerSeq)
        val after = (current.operations.first().applyTo(current.baseWorkspace) as OperationResult.Applied).workspace
        save(
            current.copy(
                baseWorkspace = after,
                baseVersion = version,
                baseServerSeq = seq,
                operations = current.operations.drop(1),
                headTransactionId = null,
                lastAcknowledgedTransactionId = transactionId,
            ),
        )
    }

    fun quarantine(scope: PendingSubmissionScope) {
        load(scope)?.let { save(it.copy(quarantined = true)) }
    }

    fun restore(
        scope: PendingSubmissionScope,
        id: String,
        current: WorkspaceSession,
    ): WorkspaceDraftJournal {
        val journal = checkNotNull(load(scope))
        check(current.userId == scope.userId && current.clientId == scope.clientId && current.workspace.id.value == scope.workspaceId)
        check(journal.id == id && !journal.quarantined && journal.headTransactionId == null)
        check(current.workspaceVersion == journal.baseVersion && current.lastServerSeq == journal.baseServerSeq)
        check(current.workspace.copy(version = 0, title = "") == journal.baseWorkspace.copy(version = 0, title = "")) {
            "Remote workspace changed; saved draft requires review"
        }
        return journal.copy(baseWorkspace = current.workspace).also { save(it) }
    }

    fun removeEmptyAcknowledged(
        scope: PendingSubmissionScope,
        transactionId: String,
    ) {
        val current = load(scope) ?: return
        if (current.operations.isEmpty() && current.lastAcknowledgedTransactionId == transactionId) remove(scope, current.id)
    }

    fun remove(
        scope: PendingSubmissionScope,
        id: String,
    ): Boolean {
        val current = load(scope) ?: return false
        if (current.id != id) return false
        val manifest = json.decodeFromString<Manifest>(checkNotNull(settings.getStringOrNull(key(scope))))
        settings.remove(key(scope))
        repeat(manifest.chunks) { settings.remove(chunkKey(scope, manifest.digest, it)) }
        return true
    }

    private fun save(journal: WorkspaceDraftJournal) {
        validate(journal) // Reject an unreadable state before publishing any chunks or manifest.
        val content = json.encodeToString(journal)
        val bytes = content.encodeToByteArray().size
        require(bytes in 1..MaximumBytes) { "Workspace draft exceeds local storage limit" }
        requireBoundedDraftJsonDepth(content)
        val hash = digest(content)
        val old = settings.getStringOrNull(key(journal.scope))?.let { json.decodeFromString<Manifest>(it) }
        var offset = 0
        var count = 0
        while (offset < content.length) {
            var end = (offset + ChunkCharacters).coerceAtMost(content.length)
            if (end < content.length && content[end - 1] in '\uD800'..'\uDBFF') end--
            val chunkKey = chunkKey(journal.scope, hash, count++)
            val chunk = content.substring(offset, end)
            settings.putString(chunkKey, chunk)
            check(settings.getStringOrNull(chunkKey) == chunk) { "Workspace draft chunk was not saved" }
            offset = end
        }
        val manifest = json.encodeToString(Manifest(1, count, bytes, hash))
        settings.putString(key(journal.scope), manifest)
        check(settings.getStringOrNull(key(journal.scope)) == manifest) { "Workspace draft manifest was not saved" }
        check(load(journal.scope) == journal) { "Workspace draft publication could not be verified" }
        // After publication, failure to reclaim old chunks must not make the saved edit look rejected.
        if (old != null && old.digest != hash) {
            runCatching {
                repeat(old.chunks) { settings.remove(chunkKey(journal.scope, old.digest, it)) }
            }
        }
    }

    private fun validate(journal: WorkspaceDraftJournal) = validateWorkspaceDraftJournal(journal)

    private fun key(scope: PendingSubmissionScope) = "wdj1." + digest(json.encodeToString(scope))

    private fun chunkKey(
        scope: PendingSubmissionScope,
        hash: String,
        index: Int,
    ) = "wdc1." + digest(json.encodeToString(scope) + ":" + hash + ":" + index)

    private fun digest(content: String) =
        SHA256()
            .digest(content.encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Serializable private data class Manifest(
        val version: Int,
        val chunks: Int,
        val bytes: Int,
        val digest: String,
    )

    private companion object {
        const val ChunkCharacters = 2048
        const val MaximumBytes = 4 * 1024 * 1024
        const val MaximumManifestCharacters = 512
    }
}

internal fun validateWorkspaceDraftJournal(journal: WorkspaceDraftJournal) {
    check(journal.version == 1 && journal.id.isNotBlank())
    check(
        listOf(journal.scope.apiBase, journal.scope.userId, journal.scope.clientId, journal.scope.workspaceId).all(String::isNotBlank),
    )
    check(journal.baseWorkspace.id.value == journal.scope.workspaceId && journal.operations.size <= 200)
    check(journal.baseVersion in 0..9_007_199_254_740_991L && journal.baseServerSeq in 0..9_007_199_254_740_991L)
    check(journal.operations.all { it.operationId.isNotBlank() })
    check(journal.headTransactionId?.isNotBlank() != false && journal.lastAcknowledgedTransactionId?.isNotBlank() != false)
    journal.replay()
}
