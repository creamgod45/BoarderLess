package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.serialization.json.JsonObject

/** Read-only legacy adaptation shared by preview and insertion; never rewrites saved content. */
internal fun decodeQuickSchemePayload(scheme: QuickScheme): ClipboardPayload? {
    if (scheme.schemaVersion !in 1..7) return null
    val payload =
        runCatching {
            val document =
                cg.creamgod.boarderless.data.parseStrictJsonObject(scheme.payload) as? JsonObject
                    ?: return null
            val decoded = ClipboardJson.decodeFromJsonElement(ClipboardPayload.serializer(), document)
            // Missing JSON version must not inherit the latest constructor default (currently v4).
            if ("version" in document) decoded else decoded.copy(version = scheme.schemaVersion)
        }.getOrNull() ?: return null
    // Old Settings records without version metadata default to 1, even when their JSON is newer.
    // Explicit non-legacy metadata must agree; never silently downgrade a future payload.
    if (scheme.schemaVersion != 1 && scheme.schemaVersion != payload.version) return null
    return payload.takeIf { validateClipboardPayload(it) == null }
}

/** Until backend materialization exists, media can only reuse verified destination assets.
 * Legacy missing provenance never means "use the currently opened workspace as the source".
 * This UI gate does not replace authoritative operation-service ACL checks.
 */
internal fun quickSchemeMediaAvailable(
    scheme: QuickScheme,
    payload: ClipboardPayload,
    workspaceId: WorkspaceId?,
    assets: Map<String, WorkspaceAsset>,
): Boolean {
    if (payload.media.isEmpty()) return true
    if (workspaceId == null ||
        (scheme.sourceWorkspaceId != null && scheme.sourceWorkspaceId != workspaceId.value)
    ) {
        return false
    }
    return selectionMediaAvailable(payload, workspaceId, assets)
}
