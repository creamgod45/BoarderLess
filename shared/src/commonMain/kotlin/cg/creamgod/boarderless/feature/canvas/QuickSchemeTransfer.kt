package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Transferable definition; local library IDs, credentials and picker/cache paths are excluded.
 * Media IDs retain provenance and still require verified destination assets before insertion.
 */
internal object QuickSchemeTransferCodec {
    private val json = Json { encodeDefaults = true }

    @Serializable private data class Document(
        val format: String = "boarderless.quick-scheme",
        val schemaVersion: Int = 1,
        val name: String,
        val selection: ClipboardPayload,
        val sourceWorkspaceId: String? = null,
    )

    private fun validate(document: Document) {
        require(document.format == "boarderless.quick-scheme" && document.schemaVersion == 1)
        require(document.name.isNotBlank() && document.name.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096)
        require(document.selection.nodes.size + document.selection.groups.size + document.selection.media.size <= 1000)
        require(document.selection.relations.size <= 2000)
        val groups = document.selection.groups.associateBy { it.originalId }
        document.selection.groups.forEach { group ->
            val visited = mutableSetOf<String>()
            var id: String? = group.originalId
            while (id != null) {
                require(visited.add(id) && visited.size <= 64) { "Scheme hierarchy is too deep or cyclic" }
                id = groups[id]?.parentOriginalId
            }
        }
        require(validateClipboardPayload(document.selection) == null)
        require(
            document.sourceWorkspaceId == null ||
                (
                    document.sourceWorkspaceId.isNotBlank() &&
                        document.sourceWorkspaceId.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096
                ),
        )
        if (document.selection.media.isNotEmpty()) require(document.sourceWorkspaceId != null)
        val payload = ClipboardJson.encodeToString(ClipboardPayload.serializer(), document.selection)
        require(payload.encodeToByteArray(throwOnInvalidSequence = true).size <= 1024 * 1024)
        parseStrictJsonObject(payload)
    }

    fun encode(scheme: QuickScheme): String {
        val selection = requireNotNull(decodeQuickSchemePayload(scheme))
        val document = Document(name = scheme.name, selection = selection, sourceWorkspaceId = scheme.sourceWorkspaceId)
        validate(document)
        return json.encodeToString(document).also { require(it.encodeToByteArray().size <= MaximumBytes) }
    }

    fun decode(content: String): QuickScheme {
        try {
            require(content.length in 1..MaximumBytes && content.encodeToByteArray(throwOnInvalidSequence = true).size <= MaximumBytes)
            val root = parseStrictJsonObject(content)
            require(root.keys == setOf("format", "schemaVersion", "name", "selection", "sourceWorkspaceId"))
            val selection = root["selection"] as? JsonObject ?: error("Missing selection")
            require("format" in selection && "version" in selection)
            val document = json.decodeFromJsonElement(Document.serializer(), root)
            validate(document)
            return QuickScheme(
                0,
                document.name,
                ClipboardJson.encodeToString(ClipboardPayload.serializer(), document.selection),
                document.selection.version,
                document.sourceWorkspaceId,
            )
        } catch (_: Exception) {
            throw IllegalArgumentException("Scheme file is invalid or unsupported")
        }
    }

    private const val MaximumBytes = 4 * 1024 * 1024
}

/** Explicit file delivery. A fresh local definition is required after the picker returns;
 * cancellation/scope changes prevent private content reaching the destination.
 */
internal suspend fun exportQuickScheme(
    runtime: DraftBackupRuntime,
    scheme: QuickScheme,
    isCurrent: () -> Boolean,
    readFresh: () -> QuickScheme?,
): DraftBackupResult {
    val context = currentCoroutineContext()

    fun checkCurrent() {
        context.ensureActive()
        check(isCurrent())
    }
    checkCurrent()
    val destination = checkNotNull(runtime.chooseDestination).invoke("boarderless-scheme.json") ?: return DraftBackupResult.Cancelled
    var failure: Throwable? = null
    try {
        checkCurrent()
        require(readFresh() == scheme) { "Scheme changed during export" }
        val document = QuickSchemeTransferCodec.encode(scheme)
        checkCurrent()
        destination.write(document) { context.isActive && isCurrent() }
        checkCurrent()
        return if (runtime.usesBrowserDownload) DraftBackupResult.DownloadRequested else DraftBackupResult.Saved
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            withContext(NonCancellable) { destination.dispose() }
        } catch (error: Throwable) {
            if (failure == null) throw error
        }
    }
}
