package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.GlassSurface
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.designsystem.ShellIcon
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.NodeShape

internal data class ComponentLibraryEntry(
    val id: String,
    val title: String,
    val description: String,
    val colorToken: String,
    val shape: NodeShape = NodeShape.RoundedRectangle,
    val categories: Set<ComponentLibraryCategory> = componentCategories(id, shape),
)

internal enum class ComponentLibraryCategory(val token: String) {
    All("all"), Geometry("geometry"), Flow("flow"), Arrows("arrows"), Text("text"), Containers("containers"), Diagrams("diagrams");
    companion object { fun fromToken(token: String) = entries.firstOrNull { it.token == token } ?: All }
}

private fun componentCategories(id: String, shape: NodeShape): Set<ComponentLibraryCategory> {
    if (id == "swimlane-starter") return setOf(ComponentLibraryCategory.Containers, ComponentLibraryCategory.Diagrams)
    if (id == "flowchart-starter") return setOf(ComponentLibraryCategory.Flow, ComponentLibraryCategory.Diagrams)
    if (id in setOf("org-chart-starter", "architecture-starter", "relationship-map", "topology-starter", "tree-starter", "comparison", "support-pair"))
        return setOf(ComponentLibraryCategory.Diagrams)
    if (id in setOf("thought", "highlight") || shape == NodeShape.PlainText) return setOf(ComponentLibraryCategory.Text)
    return when (shape) {
        NodeShape.ArrowRight, NodeShape.ArrowLeft, NodeShape.ArrowUp, NodeShape.ArrowDown,
        NodeShape.Chevron, NodeShape.DoubleArrow ->
            setOf(ComponentLibraryCategory.Geometry, ComponentLibraryCategory.Arrows)
        NodeShape.Rectangle, NodeShape.Pill, NodeShape.Diamond, NodeShape.Parallelogram, NodeShape.Document, NodeShape.Database,
        NodeShape.ManualInput ->
            setOf(ComponentLibraryCategory.Geometry, ComponentLibraryCategory.Flow)
        else -> setOf(ComponentLibraryCategory.Geometry)
    }
}

private fun categoryLabel(category: ComponentLibraryCategory): String = when (category) {
    ComponentLibraryCategory.All -> Strings.library.allCategories()
    ComponentLibraryCategory.Geometry -> Strings.library.categoryGeometry()
    ComponentLibraryCategory.Flow -> Strings.library.categoryFlow()
    ComponentLibraryCategory.Arrows -> Strings.library.categoryArrows()
    ComponentLibraryCategory.Text -> Strings.library.categoryText()
    ComponentLibraryCategory.Containers -> Strings.library.categoryContainers()
    ComponentLibraryCategory.Diagrams -> Strings.library.categoryDiagrams()
}

internal enum class ComponentLibraryView { All, Favorites, Recent }

internal fun componentLibraryEntriesForView(
    entries: List<ComponentLibraryEntry>, query: String, view: ComponentLibraryView,
    favorites: Set<String>, recent: List<String>,
    category: ComponentLibraryCategory = ComponentLibraryCategory.All,
): List<ComponentLibraryEntry> {
    val ordered = when (view) {
        ComponentLibraryView.All -> entries
        ComponentLibraryView.Favorites -> entries.filter { it.id in favorites }
        ComponentLibraryView.Recent -> {
            val byId = entries.associateBy { it.id }
            recent.distinct().mapNotNull(byId::get)
        }
    }
    return filterComponentLibraryEntries(ordered.filter { category == ComponentLibraryCategory.All || category in it.categories }, query)
}

internal fun filterComponentLibraryEntries(
    entries: List<ComponentLibraryEntry>,
    query: String,
): List<ComponentLibraryEntry> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (tokens.isEmpty()) return entries
    return entries.filter { entry ->
        val searchable = "${entry.title} ${entry.description}".lowercase()
        tokens.all(searchable::contains)
    }
}

@Composable
internal fun ComponentLibrary(
    entries: List<ComponentLibraryEntry>,
    modifier: Modifier = Modifier,
    onInsert: (ComponentLibraryEntry) -> Unit,
    onDragStart: (ComponentLibraryEntry, Vec2) -> Unit,
    onDragMove: (ComponentLibraryEntry, Vec2) -> Unit,
    onDragEnd: (ComponentLibraryEntry, Vec2) -> Unit,
    onDragCancel: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    workspaceMediaContent: (@Composable () -> Unit)? = null,
    giphyContent: (@Composable () -> Unit)? = null,
    onFocusWithinChange: (Boolean) -> Unit = {},
    favorites: Set<String> = emptySet(),
    recent: List<String> = emptyList(),
    onToggleFavorite: ((ComponentLibraryEntry) -> Unit)? = null,
    category: ComponentLibraryCategory = ComponentLibraryCategory.All,
    categoriesExpanded: Boolean = true,
    onCategoryChange: (ComponentLibraryCategory) -> Unit = {},
    onCategoriesExpandedChange: (Boolean) -> Unit = {},
) {
    val colors = BoarderLessTheme.colors
    var query by remember { mutableStateOf("") }
    var showMedia by remember { mutableStateOf(false) }
    var showGiphy by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(ComponentLibraryView.All) }
    val visibleEntries = componentLibraryEntriesForView(entries, query, view, favorites, recent, category)
    val currentFocusCallback by rememberUpdatedState(onFocusWithinChange)
    DisposableEffect(Unit) { onDispose { currentFocusCallback(false) } }
    // A focus group reports descendant focus, including provider and asset search fields.
    GlassSurface(modifier = modifier.onFocusChanged { currentFocusCallback(it.hasFocus) }.focusGroup()) {
        Column(Modifier.width(276.dp).heightIn(max = 430.dp)) {
        if (showGiphy && giphyContent != null) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { GiphyAttribution() }
        }
        Column(
            modifier = Modifier
                .width(276.dp)
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                text = Strings.library.objectLibrary(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(
                    color = colors.contentText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (workspaceMediaContent != null) {
                ShellButton(label = Strings.media.builtInLibrary(), icon = ShellIcon.Library, accent = !showMedia && !showGiphy,
                    onClick = { showMedia = false; showGiphy = false })
                ShellButton(label = Strings.media.workspaceLibrary(), icon = ShellIcon.Library, accent = showMedia && !showGiphy,
                    onClick = { showMedia = true; showGiphy = false })
            }
            if (giphyContent != null) ShellButton(label = Strings.giphy.browse(), icon = ShellIcon.Search, accent = showGiphy,
                onClick = { showGiphy = true; showMedia = false })
            if (showGiphy && giphyContent != null) {
                giphyContent()
            } else if (showMedia && workspaceMediaContent != null) {
                workspaceMediaContent()
            } else {
            BasicText(
                text = Strings.library.dragComponentOntoCanvasOr(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ComponentLibraryView.entries.forEach { option ->
                    ShellButton(label = when (option) {
                        ComponentLibraryView.All -> Strings.library.allComponents()
                        ComponentLibraryView.Favorites -> Strings.library.favorites()
                        ComponentLibraryView.Recent -> Strings.library.recentlyUsed()
                    }, accent = view == option, onClick = { view = option })
                }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier
                    .width(256.dp)
                    .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                    .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.selection),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isBlank()) {
                            BasicText(
                                text = Strings.library.searchComponents(),
                                style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            ShellButton(label = "${Strings.library.categories()}: ${categoryLabel(category)}",
                icon = if (categoriesExpanded) ShellIcon.Back else ShellIcon.Forward,
                onClick = { onCategoriesExpandedChange(!categoriesExpanded) })
            if (categoriesExpanded) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ComponentLibraryCategory.entries.forEach { option ->
                    ShellButton(label = categoryLabel(option), compact = true, accent = category == option,
                        onClick = { onCategoryChange(option) })
                }
            }
            if (visibleEntries.isEmpty()) {
                BasicText(
                    text = Strings.library.noMatchingComponents(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                )
            }
            visibleEntries.forEach { entry ->
                var layoutCoordinates by remember(entry.id) { mutableStateOf<LayoutCoordinates?>(null) }
                val currentCoordinates by rememberUpdatedState(layoutCoordinates)
                val surfaceShape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .width(256.dp)
                        .background(colors.canvas.copy(alpha = 0.74f), surfaceShape)
                        .border(1.dp, colors.contentBorder, surfaceShape)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    onToggleFavorite?.let { toggle ->
                        ShellButton(label = if (entry.id in favorites) Strings.library.removeFavorite() else Strings.library.addFavorite(),
                            accent = entry.id in favorites, onClick = { toggle(entry) })
                    }
                    Box(
                        modifier = Modifier
                            .width(244.dp)
                            .clip(entry.shape.composeShape())
                            .background(
                                color = if (entry.shape == NodeShape.PlainText) Color.Transparent else when (entry.colorToken) {
                                    "lilac" -> colors.nodeLilac
                                    "amber" -> colors.nodeAmber
                                    "mint" -> colors.nodeMint
                                    else -> colors.contentSurface
                                },
                                shape = entry.shape.composeShape(),
                            )
                            .border(1.dp, if (entry.shape == NodeShape.PlainText) Color.Transparent else colors.contentBorder, entry.shape.composeShape())
                            .onGloballyPositioned { layoutCoordinates = it }
                            .pointerInput(entry.id) {
                                var rootPosition = Vec2.Zero
                                detectDragGestures(
                                    onDragStart = { localPosition ->
                                        val rootOffset = currentCoordinates
                                            ?.localToRoot(localPosition)
                                            ?: Offset(localPosition.x, localPosition.y)
                                        rootPosition = Vec2(rootOffset.x, rootOffset.y)
                                        onDragStart(entry, rootPosition)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        rootPosition += Vec2(dragAmount.x, dragAmount.y)
                                        onDragMove(entry, rootPosition)
                                    },
                                    onDragEnd = { onDragEnd(entry, rootPosition) },
                                    onDragCancel = onDragCancel,
                                )
                            }
                            .padding(
                                horizontal = entry.shape.horizontalContentPadding(244.dp),
                                vertical = 10.dp,
                            ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(
                                text = entry.title,
                                style = TextStyle(
                                    color = colors.contentText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            BasicText(
                                text = entry.description,
                                style = TextStyle(color = colors.contentMuted, fontSize = 10.sp),
                            )
                            BasicText(
                                text = Strings.library.dragHandle(),
                                style = TextStyle(
                                    color = colors.selection,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                    ShellButton(
                        label = Strings.library.insertAtCenter(),
                        onClick = { onInsert(entry) },
                    )
                }
            }
            }
            onDismiss?.let { dismiss ->
                ShellButton(
                    label = Strings.common.close(),
                    icon = ShellIcon.Close,
                    onClick = dismiss,
                )
            }
        }
        }
    }
}
