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
import cg.creamgod.boarderless.i18n.Localization
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.*

@Composable internal fun GiphyBrowserContent(
    runtime: MediaImportRuntime,
    reduceMotion: Boolean = false,
    onTextEditingChange: (Boolean) -> Unit = {},
    onInsertReference: ((GiphyItem, String) -> Unit)? = null,
    onKeyChange: (String) -> Unit = {},
    /** Key already accepted in this session; the panel is recreated each time it is reopened. */
    sessionKey: String = "",
) {
    val colors = BoarderLessTheme.colors
    val searchLanguage = giphySearchLanguage(Localization.language.tag)
    val currentSearchLanguage by rememberUpdatedState(searchLanguage)
    var resultsLanguage by remember { mutableStateOf(searchLanguage) }
    var keyFocused by remember { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    val editingCallback by rememberUpdatedState(onTextEditingChange)
    // Report the actual fields as well as the library's focus group. Ancestor canvas handlers
    // run before TextField editing handlers and must yield even during a focus transition.
    DisposableEffect(Unit) { onDispose { editingCallback(false) } }
    val initialKey = sessionKey.ifBlank { runtime.giphyApiKey.orEmpty() }
    var keyDraft by remember { mutableStateOf(initialKey) }
    var activeKey by remember { mutableStateOf(initialKey) }
    var query by remember { mutableStateOf("") }
    var issuedQuery by remember { mutableStateOf<String?>(null) }
    var pendingQuery by remember { mutableStateOf<String?>(null) }
    var pendingOffset by remember { mutableStateOf(0) }
    var queryCancelled by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<GiphyItem>>(emptyList()) }
    var resultsScrollRequest by remember { mutableStateOf(GiphyGridScrollRequest(0, 0)) }
    var nextOffset by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hasResponse by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<GiphyIssue?>(null) }
    var retryDelayMillis by remember { mutableStateOf(0L) }
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
    LaunchedEffect(client, error) {
        retryDelayMillis = 0L
        if (error == GiphyIssue.RateLimited) {
            while (isActive) {
                retryDelayMillis = client.retryDelayMillis()
                if (retryDelayMillis == 0L) break
                delay(minOf(retryDelayMillis, 1000L)) // Local clock only; never auto-fetch.
            }
        }
    }

    // A page offset belongs to its search language. Changing locale clears that session without
    // spending API budget; the user starts the next query explicitly. Keep the entered key/query.
    LaunchedEffect(searchLanguage) {
        generation++
        job?.cancel()
        previewJob?.cancel()
        previewGeneration++
        busy = false
        previewRefreshing = false
        previewMissing = false
        items = emptyList()
        preview = null
        nextOffset = null
        hasResponse = false
        issuedQuery = null
        pendingQuery = null
        queryCancelled = false
        resultsLanguage = searchLanguage
        error = null
    }

    fun request(
        term: String?,
        offset: Int = 0,
    ) {
        if (offset > 0 && resultsLanguage != searchLanguage) return
        if (retryDelayMillis > 0) return
        job?.cancel()
        // An old preview refresh cannot overwrite the new query's status. Keep the displayed
        // preview and its availability, but invalidate the refresh's completion callbacks.
        previewJob?.cancel()
        previewGeneration++
        previewRefreshing = false
        val epoch = ++generation
        // Freeze the displayed session. A replacement search only replaces it after success;
        // cancellation/failure must not relabel its results or consume its next-page offset.
        val existing = items
        pendingQuery = term
        pendingOffset = offset
        val language = searchLanguage // Freeze the language for the entire pending request.
        busy = true
        queryCancelled = false
        error = null
        job =
            scope.launch {
                try {
                    val page = client.browse(term, offset, language = language)
                    currentCoroutineContext().ensureActive()
                    if (generation == epoch && currentSearchLanguage == language) {
                        val update = giphyResultsAfterPage(existing, page)
                        if (offset == 0) {
                            previewJob?.cancel()
                            previewGeneration++
                            previewRefreshing = false
                            previewMissing = false
                            preview = null
                        }
                        items = update.items
                        update.firstNewIndex?.let { resultsScrollRequest = GiphyGridScrollRequest(epoch, it) }
                        nextOffset = page.nextOffset
                        issuedQuery = term
                        resultsLanguage = language
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
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (generation == epoch) error = GiphyIssue.Unavailable
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
            onKeyChange(activeKey)
            items = emptyList()
            preview = null
            nextOffset = null
            error = null
            hasResponse = false
            issuedQuery = null
            pendingQuery = null
            queryCancelled = false
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
                activeKey.isNotBlank() && query.isNotBlank() && retryDelayMillis == 0L && (!busy || query != pendingQuery) &&
                    (!hasResponse || query != issuedQuery || error != null),
            onClick = { request(query) },
        )
        ShellButton(
            label = Strings.giphy.trending(),
            enabled = activeKey.isNotBlank() && !busy && retryDelayMillis == 0L && (!hasResponse || issuedQuery != null || error != null),
            onClick = { request(null) },
        )
    }
    if (busy) {
        BasicText(
            if (pendingOffset == 0) Strings.giphy.loadingQuery() else Strings.giphy.loadingPage(pendingOffset.toString()),
            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
        )
        ShellButton(label = Strings.giphy.cancel(), onClick = {
            generation++
            job?.cancel()
            busy = false
            queryCancelled = true
        })
    }
    if (queryCancelled) BasicText(Strings.giphy.queryCancelled(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    if (hasResponse) BasicText(
        Strings.giphy.resultsContext(issuedQuery ?: Strings.giphy.trending(), items.size.toString()),
        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
    )
    error?.let { issue ->
        BasicText(
            when (issue) {
                GiphyIssue.RateLimited -> if (retryDelayMillis > 0) Strings.giphy.retryIn(((retryDelayMillis + 999) / 1000).toString())
                    else Strings.giphy.retryReady()
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
    nextOffset?.let { offset ->
        val sameQuery = issuedQuery == null || query == issuedQuery
        ShellButton(label = Strings.giphy.more(), enabled = !busy && retryDelayMillis == 0L &&
            sameQuery && resultsLanguage == searchLanguage, onClick = {
            if (nextOffset == offset && (issuedQuery == null || query == issuedQuery)) request(issuedQuery, offset)
        })
        if (!sameQuery) BasicText(Strings.giphy.queryChanged(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    }
    preview?.let { item ->
        onInsertReference?.let { insert ->
            ShellButton(Strings.giphy.insertReference(), enabled = !busy && !previewRefreshing && !previewMissing,
                onClick = { insert(item, activeKey) })
        }
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
            enabled = !busy && !previewRefreshing && retryDelayMillis == 0L && activeKey.isNotBlank(),
            onClick = {
                val epoch = ++previewGeneration
                previewRefreshing = true
                previewMissing = false
                previewJob =
                    scope.launch {
                        try {
                            val refreshed = client.resolveReferences(listOf(item.durableReference())).singleOrNull()
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
                        } catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            if (previewGeneration == epoch) {
                                previewMissing = true
                                error = GiphyIssue.Unavailable
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
    if (onInsertReference == null) BasicText(Strings.giphy.externalReferencePending(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
}
