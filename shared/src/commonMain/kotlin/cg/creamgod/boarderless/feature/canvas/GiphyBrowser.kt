package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.*

@Composable internal fun GiphyBrowserContent(
    runtime: MediaImportRuntime,
    reduceMotion: Boolean = false,
    onTextEditingChange: (Boolean) -> Unit = {},
) {
    val colors = BoarderLessTheme.colors
    var keyFocused by remember { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    val editingCallback by rememberUpdatedState(onTextEditingChange)
    // Report the actual fields as well as the library's focus group. Ancestor canvas handlers
    // run before TextField editing handlers and must yield even during a focus transition.
    DisposableEffect(Unit) { onDispose { editingCallback(false) } }
    var keyDraft by remember { mutableStateOf(runtime.giphyApiKey.orEmpty()) }
    var activeKey by remember { mutableStateOf(runtime.giphyApiKey.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var issuedQuery by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<GiphyItem>>(emptyList()) }
    var resultsScrollRequest by remember { mutableStateOf(GiphyGridScrollRequest(0, 0)) }
    var nextOffset by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hasResponse by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<GiphyIssue?>(null) }
    var generation by remember { mutableStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<GiphyItem?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewGeneration by remember { mutableStateOf(0) }
    var previewRefreshing by remember { mutableStateOf(false) }
    var previewMissing by remember { mutableStateOf(false) }
    val client = remember(activeKey) { GiphyClient(activeKey) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    DisposableEffect(client) { onDispose { client.close() } }

    fun request(
        term: String?,
        offset: Int = 0,
    ) {
        job?.cancel()
        if (offset == 0) {
            previewJob?.cancel()
            previewGeneration++
            previewRefreshing = false
            previewMissing = false
        }
        val epoch = ++generation
        if (offset == 0) {
            items = emptyList()
            nextOffset = null
            preview = null
            hasResponse = false
        }
        issuedQuery = term
        busy = true
        error = null
        job =
            scope.launch {
                try {
                    val page = client.browse(term, offset, language = "zh-TW")
                    currentCoroutineContext().ensureActive()
                    if (generation == epoch) {
                        val update = giphyResultsAfterPage(items, page)
                        items = update.items
                        update.firstNewIndex?.let { resultsScrollRequest = GiphyGridScrollRequest(epoch, it) }
                        nextOffset = page.nextOffset
                        hasResponse = true
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (
                    failed: GiphyException,
                ) {
                    if (generation == epoch) error = failed.issue
                } finally {
                    if (generation == epoch) busy = false
                }
            }
    }
    BasicText(Strings.giphy.queryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    if (runtime.giphyApiKey.isNullOrBlank()) {
        BasicText(Strings.giphy.keyHint(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        BasicTextField(
            keyDraft,
            { keyDraft = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = colors.contentText),
            modifier =
                Modifier
                    .onFocusChanged {
                        keyFocused = it.isFocused
                        editingCallback(keyFocused || queryFocused)
                    }.fillMaxWidth()
                    .background(colors.canvas)
                    .padding(10.dp),
        )
        ShellButton(label = Strings.giphy.useKey(), enabled = keyDraft.isNotBlank() && !busy, onClick = {
            generation++
            job?.cancel()
            previewJob?.cancel()
            previewGeneration++
            previewRefreshing = false
            previewMissing = false
            activeKey = keyDraft
            items = emptyList()
            preview = null
            nextOffset = null
            error = null
            hasResponse = false
        })
    }
    BasicTextField(
        query,
        { query = it },
        singleLine = true,
        textStyle = TextStyle(color = colors.contentText),
        modifier =
            Modifier
                .onFocusChanged {
                    queryFocused = it.isFocused
                    editingCallback(keyFocused || queryFocused)
                }.fillMaxWidth()
                .background(colors.canvas)
                .padding(10.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShellButton(
            label = Strings.giphy.search(),
            icon = ShellIcon.Search,
            enabled =
                activeKey.isNotBlank() && query.isNotBlank() && (!busy || query != issuedQuery) &&
                    (!hasResponse || query != issuedQuery || error != null),
            onClick = { request(query) },
        )
        ShellButton(
            label = Strings.giphy.trending(),
            enabled = activeKey.isNotBlank() && !busy && (!hasResponse || issuedQuery != null || error != null),
            onClick = { request(null) },
        )
    }
    if (busy) {
        ShellButton(label = Strings.giphy.cancel(), onClick = {
            generation++
            job?.cancel()
            busy = false
        })
    }
    error?.let { issue ->
        BasicText(
            when (issue) {
                GiphyIssue.RateLimited -> Strings.giphy.rateLimit()
                GiphyIssue.InvalidQuery -> Strings.giphy.invalidQuery()
                GiphyIssue.MissingKey, GiphyIssue.Unauthorized -> Strings.giphy.invalidKey()
                else -> Strings.giphy.unavailable()
            },
            style = TextStyle(color = colors.danger, fontSize = 11.sp),
        )
    }
    // Keep the provider's result order. No deduplication, filtering or mixing with our asset library.
    GiphyResultsGrid(items, runtime, preview?.id, resultsScrollRequest) { item ->
        previewJob?.cancel()
        previewGeneration++
        previewRefreshing = false
        previewMissing = false
        preview = item
    }
    if (!busy && nextOffset != null && error != GiphyIssue.RateLimited) {
        ShellButton(label = Strings.giphy.more(), onClick = { request(issuedQuery, nextOffset!!) })
    }
    preview?.let { item ->
        var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
        var unavailable by remember(item.id) { mutableStateOf(false) }
        val still = giphyStillUrl(item)
        LaunchedEffect(item.id, still, runtime, previewGeneration) {
            bitmap = null
            unavailable = false
            if (previewRefreshing || previewMissing) return@LaunchedEffect
            try {
                if (still != null && isGiphyMediaUrl(still) && runtime.loadGiphyStill != null) {
                    val decoded = runtime.loadGiphyStill.invoke(still)
                    currentCoroutineContext().ensureActive()
                    bitmap = decoded
                } else {
                    unavailable = true
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                unavailable = true
            }
        }
        if (!previewRefreshing && !previewMissing) {
            key(item.id, previewGeneration) {
                GiphyAnimationPreview(item, bitmap, runtime, reduceMotion)
            }
        }
        if (unavailable ||
            previewMissing
        ) {
            BasicText(Strings.giphy.previewUnavailable(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        }
        ShellButton(
            label = Strings.giphy.refreshPreview(),
            icon = ShellIcon.Retry,
            enabled = !busy && !previewRefreshing && activeKey.isNotBlank(),
            onClick = {
                val epoch = ++previewGeneration
                previewRefreshing = true
                previewMissing = false
                previewJob =
                    scope.launch {
                        try {
                            val refreshed = client.resolve(listOf(item.id)).singleOrNull()
                            currentCoroutineContext().ensureActive()
                            if (previewGeneration == epoch) {
                                previewMissing = refreshed == null
                                if (refreshed != null) preview = refreshed
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: GiphyException) {
                            if (previewGeneration == epoch) {
                                previewMissing = true
                                error = failed.issue
                            }
                        } finally {
                            if (previewGeneration == epoch) {
                                previewRefreshing = false
                                previewGeneration++
                            }
                        }
                    }
            },
        )
        ShellButton(label = Strings.giphy.viewOnGiphy(), onClick = { uri.openUri("https://giphy.com/gifs/${item.id.encodeURLPathPart()}") })
        ShellButton(label = Strings.common.close(), onClick = {
            previewJob?.cancel()
            previewGeneration++
            previewRefreshing = false
            previewMissing = false
            preview = null
        })
    }
    BasicText(Strings.giphy.externalReferencePending(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
}
