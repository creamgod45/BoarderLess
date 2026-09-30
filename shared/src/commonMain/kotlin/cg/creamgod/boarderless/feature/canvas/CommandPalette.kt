package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.GlassSurface
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.i18n.LocalizedText

internal data class PaletteEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String = "",
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/** English search keywords plus their translation, so either language finds the command. */
internal fun paletteKeywords(keywords: LocalizedText): String =
    keywords().let { translated -> if (translated == keywords.english) translated else "${keywords.english} $translated" }

internal fun diagramShapePaletteEntries(
    enabled: Boolean,
    disabledReason: String,
): List<PaletteEntry> = NodeShape.entries
    .filterNot { it == NodeShape.RoundedRectangle }
    .map { shape ->
        PaletteEntry(
            id = "new-shape:${shape.token}",
            title = Strings.palette.newNode(nodeShapeLabel(shape)),
            subtitle = Strings.palette.createAndEditDiagramShape(),
            keywords = "${paletteKeywords(Strings.palette.keywords.addCreateDiagramFlowchartNode)} " +
                "${shape.token} ${paletteKeywords(nodeShapeText(shape))}",
            enabled = enabled,
            disabledReason = disabledReason,
        )
    }

private val DiagramTemplateComponentIds = setOf(
    "flowchart-starter",
    "swimlane-starter",
    "org-chart-starter",
    "architecture-starter",
    "relationship-map",
    "topology-starter",
    "tree-starter",
)

private fun diagramTemplateAliases(componentId: String): String = when (componentId) {
    "flowchart-starter" -> paletteKeywords(Strings.palette.keywords.flowchartTemplate)
    "swimlane-starter" -> paletteKeywords(Strings.palette.keywords.swimlaneTemplate)
    "org-chart-starter" -> paletteKeywords(Strings.palette.keywords.orgChartTemplate)
    "architecture-starter" -> paletteKeywords(Strings.palette.keywords.architectureTemplate)
    "relationship-map" -> paletteKeywords(Strings.palette.keywords.relationshipMapTemplate)
    "topology-starter" -> paletteKeywords(Strings.palette.keywords.topologyTemplate)
    "tree-starter" -> paletteKeywords(Strings.palette.keywords.treeTemplate)
    else -> ""
}

internal fun diagramTemplatePaletteEntries(
    components: List<ComponentLibraryEntry>,
    enabled: Boolean,
    disabledReason: String,
): List<PaletteEntry> = components
    .filter { it.id in DiagramTemplateComponentIds }
    .map { component ->
        PaletteEntry(
            id = "insert-component:${component.id}",
            title = Strings.library.insert(component.title),
            subtitle = Strings.library.createEditableDiagramTemplateAt(),
            keywords = buildString {
                append(paletteKeywords(Strings.palette.keywords.insertDiagramTemplateStarter))
                append(' ')
                append(component.id)
                append(' ')
                append(diagramTemplateAliases(component.id))
                append(' ')
                append(component.title)
                append(' ')
                append(component.description)
            },
            enabled = enabled,
            disabledReason = disabledReason,
        )
    }

internal fun relationPaletteEntries(
    enabled: Boolean,
    disabledReason: String,
): List<PaletteEntry> = listOf(
    "connect-relates" to Strings.inspector.relate(),
    "connect-supports" to Strings.inspector.supports(),
    "connect-conflicts" to Strings.inspector.conflicts(),
).map { (id, title) ->
    PaletteEntry(
        id = id,
        title = title,
        subtitle = Strings.palette.connectTheTwoSelectedThoughts(),
        keywords = paletteKeywords(Strings.palette.keywords.connectRelationIntent),
        enabled = enabled,
        disabledReason = disabledReason,
    )
}

internal fun connectionTargetPaletteEntries(
    sourceId: CanvasObjectId,
    nodes: Collection<TextNode>,
    enabled: Boolean,
    disabledReason: String,
): List<PaletteEntry> = nodes
    .asSequence()
    .filterNot { it.id == sourceId }
    .sortedWith(compareByDescending<TextNode> { it.zIndex }.thenBy { it.id.value })
    .map { target ->
        val targetTitle = target.text.lineSequence().firstOrNull()?.take(80).orEmpty()
            .ifBlank { Strings.content.untitledThought() }
        PaletteEntry(
            id = "connect-target:${target.id.value}",
            title = Strings.palette.connectToThought(targetTitle),
            subtitle = Strings.palette.createDirectedRelatedConnection(),
            keywords = "${paletteKeywords(Strings.palette.keywords.connectRelationIntent)} ${target.text}",
            enabled = enabled,
            disabledReason = disabledReason,
        )
    }
    .toList()

internal fun relationNavigationPaletteEntries(
    relations: Collection<Relation>,
    nodesById: Map<CanvasObjectId, TextNode>,
): List<PaletteEntry> = relations
    .asSequence()
    .sortedWith(compareByDescending<Relation> { it.version }.thenBy { it.id.value })
    .mapNotNull { relation ->
        val source = nodesById[relation.sourceObjectId] ?: return@mapNotNull null
        val target = nodesById[relation.targetObjectId] ?: return@mapNotNull null
        val sourceTitle = source.text.lineSequence().firstOrNull()?.take(60).orEmpty()
            .ifBlank { Strings.content.untitledThought() }
        val targetTitle = target.text.lineSequence().firstOrNull()?.take(60).orEmpty()
            .ifBlank { Strings.content.untitledThought() }
        val arrow = when (relation.direction) {
            RelationDirection.None -> "—"
            RelationDirection.Forward -> "→"
            RelationDirection.Backward -> "←"
            RelationDirection.Both -> "↔"
        }
        val intent = when (relation.intent) {
            "relates" -> Strings.objects.relates()
            "supports" -> Strings.objects.supports()
            "conflicts" -> Strings.objects.conflicts()
            null -> Strings.objects.connection()
            else -> relation.intent.replaceFirstChar(Char::uppercase)
        }
        PaletteEntry(
            id = "find-relation:${relation.id.value}",
            title = "$sourceTitle $arrow $targetTitle",
            subtitle = relation.label?.takeIf(String::isNotBlank)
                ?.let { "$it • $intent" }
                ?: Strings.palette.connectionIntent(intent),
            keywords = buildString {
                append(paletteKeywords(Strings.palette.keywords.findConnectionRelation))
                append(' ')
                append(source.text)
                append(' ')
                append(target.text)
                append(' ')
                append(relation.intent.orEmpty())
                append(' ')
                append(relation.label.orEmpty())
            },
        )
    }
    .toList()

internal fun groupNavigationPaletteEntries(groups: Collection<GroupFrame>): List<PaletteEntry> = groups
    .asSequence()
    .sortedWith(compareByDescending<GroupFrame> { it.zIndex }.thenBy { it.id.value })
    .map { group ->
        val title = group.title.take(80).ifBlank { Strings.content.untitledGroup() }
        PaletteEntry(
            id = "find-group:${group.id.value}",
            title = title,
            subtitle = Strings.palette.jumpToGroup(),
            keywords = "${paletteKeywords(Strings.palette.keywords.findGroupFrame)} ${group.title}",
        )
    }
    .toList()

internal fun filterPaletteEntries(
    entries: List<PaletteEntry>,
    query: String,
): List<PaletteEntry> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return entries
    return entries
        .filter { entry ->
            val searchable = "${entry.title} ${entry.subtitle} ${entry.keywords}".lowercase()
            tokens.all(searchable::contains)
        }
        .sortedWith(
            compareByDescending<PaletteEntry> { it.title.lowercase().startsWith(tokens.first()) }
                .thenBy { it.title.lowercase() },
        )
}

@Composable
internal fun CommandPalette(
    query: String,
    entries: List<PaletteEntry>,
    compact: Boolean = false,
    onQueryChange: (String) -> Unit,
    onInvoke: (PaletteEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val focusRequester = remember { FocusRequester() }
    val filtered = filterPaletteEntries(entries, query).take(12)
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val highlightedIndex = filtered.indexOfFirst { it.id == highlightedId }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(filtered) {
        if (filtered.none { it.id == highlightedId && it.enabled }) {
            highlightedId = filtered.firstOrNull { it.enabled }?.id
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(onClick = onDismiss),
        )
        GlassSurface(
            modifier = if (compact) {
                Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp, start = 18.dp, end = 18.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            },
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(
                    text = Strings.palette.commandPalette(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = colors.contentMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.canvas.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) {
                        BasicText(
                            text = Strings.palette.searchCommandsOrThoughts(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 14.sp),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { onQueryChange(it.take(160)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        if (filtered.isNotEmpty()) {
                                            val next = (highlightedIndex + 1 until filtered.size)
                                                .firstOrNull { filtered[it].enabled }
                                                ?: filtered.indexOfFirst { it.enabled }.takeIf { it >= 0 }
                                            next?.let { highlightedId = filtered[it].id }
                                        }
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        if (filtered.isNotEmpty()) {
                                            val previous = (highlightedIndex - 1 downTo 0)
                                                .firstOrNull { filtered[it].enabled }
                                                ?: filtered.indexOfLast { it.enabled }.takeIf { it >= 0 }
                                            previous?.let { highlightedId = filtered[it].id }
                                        }
                                        true
                                    }

                                    Key.Enter -> {
                                        filtered.firstOrNull { it.id == highlightedId && it.enabled }
                                            ?.let(onInvoke)
                                        true
                                    }

                                    else -> false
                                }
                            },
                        textStyle = TextStyle(color = colors.contentText, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.selection),
                        singleLine = true,
                    )
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (filtered.isEmpty()) {
                        BasicText(
                            text = Strings.palette.noMatchingCommandOrThought(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 13.sp),
                        )
                    }
                    filtered.forEach { entry ->
                        val highlighted = entry.id == highlightedId
                        val foreground = if (entry.enabled) colors.contentText else colors.contentMuted
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (highlighted) colors.selection.copy(alpha = 0.16f) else Color.Transparent,
                                )
                                .clickable(enabled = entry.enabled, role = Role.Button) { onInvoke(entry) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = entry.title,
                                    style = TextStyle(
                                        color = foreground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                BasicText(
                                    text = if (entry.enabled) entry.subtitle else entry.disabledReason ?: entry.subtitle,
                                    style = TextStyle(
                                        color = colors.contentMuted.copy(alpha = if (entry.enabled) 1f else 0.65f),
                                        fontSize = 10.sp,
                                    ),
                                )
                            }
                            entry.shortcut?.let { shortcut ->
                                BasicText(
                                    text = shortcut,
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
