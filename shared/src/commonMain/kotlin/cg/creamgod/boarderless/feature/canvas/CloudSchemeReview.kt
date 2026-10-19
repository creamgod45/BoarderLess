package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.data.remote.CloudSchemeBinding
import cg.creamgod.boarderless.data.remote.CloudSchemeBody
import cg.creamgod.boarderless.data.remote.CloudSchemeResource
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings

/** Metadata only: no materialization, upload, local save or permission assumptions. */
internal fun cloudSchemeBodyFromLocal(local: QuickScheme, name: String = local.name): CloudSchemeBody {
    val payload = parseStrictJsonObject(local.payload)
    val definition = requireNotNull(decodeQuickSchemePayload(local))
    val bindings = definition.media.flatMap { media ->
        val source = requireNotNull(local.sourceWorkspaceId)
        listOfNotNull(CloudSchemeBinding(media.assetId, source, "original"),
            media.thumbnailAssetId?.let { CloudSchemeBinding(it, source, "thumbnail") })
    }.distinct()
    return CloudSchemeBody(name = name, schemaVersion = definition.version, payload = payload, assetBindings = bindings)
}

/** A local metadata copy, not a materialized asset bundle. The current local schema has
 * one provenance workspace; never silently flatten a cloud scheme's multiple sources. */
internal fun cloudSchemeLocalCopy(resource: CloudSchemeResource): QuickScheme {
    val sources = resource.assetBindings.map { it.sourceWorkspaceId }.distinct()
    require(sources.size <= 1) { "Multiple source workspaces need a materialized bundle" }
    val copy = QuickScheme(0, resource.name, resource.payload.toString(), resource.schemaVersion, sources.singleOrNull())
    requireNotNull(decodeQuickSchemePayload(copy))
    return copy
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CloudSchemeVisualReview(resource: CloudSchemeResource, enabled: Boolean) {
    val colors = BoarderLessTheme.colors
    val payload = remember(resource) {
        decodeQuickSchemePayload(QuickScheme(0, resource.name, resource.payload.toString(), resource.schemaVersion))
    }
    payload?.let { QuickSchemePreview(it, Modifier.fillMaxWidth().height(160.dp)) }
    if (resource.assetBindings.isEmpty()) return
    BasicText(Strings.cloudSchemes.mediaMetadataOnly(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
    var page by remember(resource) { mutableStateOf(0) }
    val pages = (resource.assetAvailability.size + 7) / 8
    val current = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    resource.assetAvailability.drop(current * 8).take(8).forEach { asset ->
        val status = when (asset.status) {
            "available" -> Strings.cloudSchemes.mediaAvailable()
            "access_denied" -> Strings.cloudSchemes.mediaDenied()
            else -> Strings.cloudSchemes.mediaUnavailable()
        }
        BasicText("${asset.kind} · $status\n${asset.assetId}\n${asset.sourceWorkspaceId}",
            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    }
    if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShellButton(Strings.cloudSchemes.previous(), enabled = enabled && current > 0, onClick = { page = current - 1 })
        BasicText("${current + 1}/$pages", style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        ShellButton(Strings.cloudSchemes.next(), enabled = enabled && current + 1 < pages, onClick = { page = current + 1 })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CloudSchemePayloadReview(raw: String, enabled: Boolean) {
    val colors = BoarderLessTheme.colors
    val pages = remember(raw) { cloudSchemeReviewPages(raw) }
    var page by remember(raw) { mutableStateOf(0) }
    BasicText(pages[page], style = TextStyle(color = colors.contentText, fontSize = 11.sp))
    if (pages.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShellButton(Strings.cloudSchemes.previous(), enabled = enabled && page > 0, onClick = { page-- })
        BasicText("${page + 1}/${pages.size}", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        ShellButton(Strings.cloudSchemes.next(), enabled = enabled && page < pages.lastIndex, onClick = { page++ })
    }
}

/** Complete review in bounded pages, never cut a UTF-16 surrogate pair. */
internal fun cloudSchemeReviewPages(raw: String): List<String> {
    raw.encodeToByteArray(throwOnInvalidSequence = true)
    if (raw.isEmpty()) return listOf("")
    return buildList {
        var start = 0
        while (start < raw.length) {
            var end = minOf(start + 4096, raw.length)
            if (end < raw.length && raw[end - 1].isHighSurrogate() && raw[end].isLowSurrogate()) end--
            add(raw.substring(start, end))
            start = end
        }
    }
}
