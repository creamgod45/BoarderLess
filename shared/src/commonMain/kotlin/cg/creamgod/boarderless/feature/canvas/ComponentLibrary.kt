package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
)

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
) {
    val colors = BoarderLessTheme.colors
    var query by remember { mutableStateOf("") }
    val visibleEntries = filterComponentLibraryEntries(entries, query)
    GlassSurface(modifier = modifier) {
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
            BasicText(
                text = Strings.library.dragComponentOntoCanvasOr(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
            )
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
                    Box(
                        modifier = Modifier
                            .width(244.dp)
                            .clip(entry.shape.composeShape())
                            .background(
                                color = when (entry.colorToken) {
                                    "lilac" -> colors.nodeLilac
                                    "amber" -> colors.nodeAmber
                                    "mint" -> colors.nodeMint
                                    else -> colors.contentSurface
                                },
                                shape = entry.shape.composeShape(),
                            )
                            .border(1.dp, colors.contentBorder, entry.shape.composeShape())
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
