package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** Explicit apply only; selection/input never mutates the path. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PenCoordinatePanel(
    draft: PenPathDraft,
    index: Int,
    enabled: Boolean,
    onApply: (PenPathDraft) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val anchor = draft.anchors.getOrNull(index) ?: return
    var target by remember(index) { mutableStateOf(PenCoordinateTarget.Anchor) }
    val point =
        when (target) {
            PenCoordinateTarget.Anchor -> anchor.point
            PenCoordinateTarget.Incoming -> anchor.incoming
            PenCoordinateTarget.Outgoing -> anchor.outgoing
        }
    val inputPoint = point ?: anchor.point
    var x by remember(index, target, anchor) { mutableStateOf(inputPoint.x.toString()) }
    var y by remember(index, target, anchor) { mutableStateOf(inputPoint.y.toString()) }
    val next =
        remember(draft, index, target, x, y) {
            val px = parsePenCoordinate(x)
            val py = parsePenCoordinate(y)
            if (px == null || py == null) null else runCatching { editPenCoordinate(draft, index, target, Vec2(px, py)) }.getOrNull()
        }
    val label =
        when (target) {
            PenCoordinateTarget.Anchor -> Strings.penDraft.anchorPoint()
            PenCoordinateTarget.Incoming -> Strings.penDraft.incomingControl()
            PenCoordinateTarget.Outgoing -> Strings.penDraft.outgoingControl()
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                PenCoordinateTarget.Anchor to Strings.penDraft.anchorPoint(),
                PenCoordinateTarget.Incoming to Strings.penDraft.incomingControl(),
                PenCoordinateTarget.Outgoing to Strings.penDraft.outgoingControl(),
            ).forEach { (value, text) ->
                ShellButton(
                    text,
                    compact = true,
                    accent = target == value,
                    enabled = enabled,
                    modifier = Modifier.semantics { selected = target == value },
                    onClick = { target = value },
                )
            }
        }
        BasicText(Strings.penDraft.coordinateInstructions(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("X" to x, "Y" to y).forEach { (axis, value) ->
                val fieldLabel = "$label $axis"
                BasicTextField(
                    value,
                    onValueChange = {
                        if (it.length <= 16) {
                            if (axis == "X") x = it else y = it
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = fieldLabel }
                            .background(colors.canvas)
                            .padding(8.dp),
                    decorationBox = { field ->
                        Column {
                            BasicText(fieldLabel, style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                            field()
                        }
                    },
                )
            }
        }
        if (next == null) BasicText(Strings.penDraft.invalidCoordinates(), style = TextStyle(color = colors.accent, fontSize = 11.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellButton(
                Strings.penDraft.applyCoordinates(),
                compact = true,
                enabled = enabled && next != null,
                onClick = { next?.let(onApply) },
            )
            ShellButton(
                Strings.penDraft.removeControl(),
                compact = true,
                enabled =
                    enabled && target != PenCoordinateTarget.Anchor && point != null,
                onClick = { onApply(removePenControl(draft, index, target)) },
            )
        }
    }
}
