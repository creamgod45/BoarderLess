package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun giphyStillUrl(item: GiphyItem): String? =
    listOf("fixed_width_still", "fixed_height_still").firstNotNullOfOrNull { name ->
        item.images[name]?.takeIf { rendition ->
            val size = rendition.size.toLongOrNull()
            isGiphyMediaUrl(rendition.url) && (size == null || size in 1..GiphyMediaClient.MaxStillBytes.toLong())
        }?.url
    }

internal data class GiphyGridScrollRequest(val generation: Int, val index: Int)
internal data class GiphyResultsPageUpdate(val items: List<GiphyItem>, val firstNewIndex: Int?)

/** Keep every provider result in order, including overlapping IDs. Only successful pages move
 * the viewport; an empty appended page, failure or cancellation leaves the current view intact.
 */
internal fun giphyResultsAfterPage(existing: List<GiphyItem>, page: GiphyPage): GiphyResultsPageUpdate {
    require(page.pagination.offset == 0 || page.pagination.offset == existing.size)
    return if (page.pagination.offset == 0) GiphyResultsPageUpdate(page.items, 0)
    else GiphyResultsPageUpdate(existing + page.items, existing.size.takeIf { page.items.isNotEmpty() })
}

@Composable internal fun GiphyResultsGrid(
    items: List<GiphyItem>,
    runtime: MediaImportRuntime,
    selectedId: String?,
    scrollRequest: GiphyGridScrollRequest,
    onSelect: (GiphyItem) -> Unit,
) {
    if (items.isEmpty()) return
    val gridState = rememberLazyGridState()
    // The generation also resets a new search with the same number of results. Item count
    // alone would miss that case, and a plain append leaves new pages below the viewport.
    LaunchedEffect(scrollRequest) {
        gridState.scrollToItem(scrollRequest.index.coerceIn(0, items.lastIndex))
    }
    // Constrained height permits a lazy viewport inside the library's independently scrollable panel.
    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(224.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // An index keeps overlapping provider pages intact without silently deduplicating their results.
        itemsIndexed(items, key = { index, item -> "$index:${item.id}" }) { _, item ->
            GiphyResultCell(item, runtime, selectedId == item.id) { onSelect(item) }
        }
    }
}

@Composable private fun GiphyResultCell(item: GiphyItem, runtime: MediaImportRuntime, isSelected: Boolean, onSelect: () -> Unit) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val url = giphyStillUrl(item)
    val label = item.alt_text.ifBlank { item.title.ifBlank { item.id } }
    var bitmap by remember(item.id, url, runtime) { mutableStateOf<ImageBitmap?>(null) }
    var visible by remember(item.id, url) { mutableStateOf(false) }
    var failed by remember(item.id, url) { mutableStateOf(false) }
    LaunchedEffect(item.id, url, runtime, visible) {
        bitmap = null; failed = false
        if (!visible) return@LaunchedEffect
        val loader = runtime.loadGiphyStill
        if (url == null || loader == null) { failed = true; return@LaunchedEffect }
        try {
            val decoded = loader(url)
            currentCoroutineContext().ensureActive()
            bitmap = decoded
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); failed = true }
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.canvas)
        .border(1.dp, if (isSelected) colors.accent else colors.shellBorder, shape)
        .semantics(mergeDescendants = true) { contentDescription = label; selected = isSelected }
        .clickable(role = Role.Button, onClick = onSelect).onGloballyPositioned {
            val bounds = it.boundsInWindow(); visible = bounds.width > 0f && bounds.height > 0f
        }.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (bitmap == null) BasicText(if (failed) Strings.giphy.previewUnavailable() else Strings.media.loadingPreview(),
                style = TextStyle(color = colors.contentMuted, fontSize = 10.sp), modifier = Modifier.padding(4.dp))
        }
        BasicText(item.title.ifBlank { item.id }, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = colors.contentText, fontSize = 11.sp), modifier = Modifier.height(30.dp))
    }
}
