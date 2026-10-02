package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException

/** Metadata browsing never initiates GIF/video playback or an upload. */
@Composable
internal fun MediaAssetLibraryContent(
    session: WorkspaceSession?,
    assets: Collection<WorkspaceAsset>,
    nodes: Collection<CanvasObject>,
    runtime: MediaImportRuntime,
    loading: Boolean,
    unavailable: Boolean,
    canInsert: Boolean,
    onRefresh: () -> Unit,
    onInsert: (MediaAssetLibraryEntry) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    var query by remember(session?.userId, session?.workspace?.id) { mutableStateOf("") }
    var kind by remember(session?.userId, session?.workspace?.id) { mutableStateOf<MediaKind?>(null) }
    var page by remember(query, kind, session?.userId, session?.workspace?.id) { mutableStateOf(0) }
    val entries = session?.let { mediaAssetLibraryEntries(it.workspace.id, assets, nodes, query, kind) }.orEmpty()
    val pageCount = ((entries.size + 7) / 8).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    BasicText(Strings.media.libraryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    BasicTextField(
        value = query, onValueChange = { query = it.take(80) }, singleLine = true,
        modifier = Modifier.width(256.dp).background(colors.canvas).padding(10.dp)
            .semantics { contentDescription = Strings.media.searchAssets() },
        textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp), cursorBrush = SolidColor(colors.selection),
        decorationBox = { inner ->
            Box { if (query.isBlank()) BasicText(Strings.media.searchAssets(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)); inner() }
        },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(null to Strings.media.allAssets(), MediaKind.Image to Strings.objects.image(),
            MediaKind.Gif to Strings.objects.gif(), MediaKind.Video to Strings.objects.video()).forEach { (filter, label) ->
            ShellButton(label = label, accent = kind == filter, onClick = { kind = filter })
        }
        ShellButton(label = Strings.media.refreshAssets(), icon = ShellIcon.Retry, enabled = session != null && !loading, onClick = onRefresh)
    }
    when {
        loading -> BasicText(Strings.media.loadingAssets(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        unavailable -> BasicText(Strings.media.assetsUnavailable(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        entries.isEmpty() -> BasicText(Strings.media.noMatchingAssets(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        else -> entries.drop(currentPage * 8).take(8).forEach { entry ->
            Column(Modifier.width(256.dp).background(colors.canvas).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val previewId = entry.previewAssetId
                val load = runtime.loadPreview
                var bitmap by remember(session?.userId, session?.clientId, entry.asset) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(session?.userId, session?.clientId, session?.workspace?.id, entry.asset, previewId, load) {
                    bitmap = null
                    if (session != null && previewId != null && load != null) {
                        try { bitmap = load(session, previewId) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Metadata and insertion remain usable without a preview. */ }
                    }
                }
                bitmap?.let { Image(it, contentDescription = entry.title, modifier = Modifier.width(240.dp).height(70.dp), contentScale = ContentScale.Fit) }
                BasicText(entry.title, maxLines = 2, style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                val status = when (entry.asset.status) {
                    AssetStatus.Pending -> Strings.media.pending()
                    AssetStatus.Ready -> Strings.media.assetReady()
                    AssetStatus.Rejected -> Strings.media.rejected()
                    AssetStatus.Missing -> Strings.media.missing()
                }
                BasicText("${entry.asset.mediaType} · ${entry.asset.byteSize} B · $status", style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                if (entry.kind == null) BasicText(Strings.media.unsupportedAsset(), style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                if (entry.asset.byteSize > MaxWorkspaceAssetBytes) BasicText(Strings.media.assetTooLarge(), style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                ShellButton(label = Strings.library.insertAtCenter(), icon = ShellIcon.Add,
                    enabled = canInsert && entry.insertable, onClick = { onInsert(entry) })
            }
        }
    }
    if (!loading && !unavailable && pageCount > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ShellButton(label = Strings.media.previousAssets(), enabled = currentPage > 0, onClick = { page = currentPage - 1 })
        BasicText("${currentPage + 1}/$pageCount", modifier = Modifier.padding(6.dp), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        ShellButton(label = Strings.media.nextAssets(), enabled = currentPage + 1 < pageCount, onClick = { page = currentPage + 1 })
    }
}
