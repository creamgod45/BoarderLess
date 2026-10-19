package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.ai.AiChatTokenLimitField
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.i18n.Strings

@Composable
internal fun AiTokenLimitFieldPicker(
    selected: AiChatTokenLimitField,
    onSelect: (AiChatTokenLimitField) -> Unit,
) {
    BasicText(Strings.aiProbe.tokenLimitField(), style = TextStyle(color = BoarderLessTheme.colors.contentMuted, fontSize = 12.sp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AiChatTokenLimitField.entries.forEach { choice ->
            ShellButton(choice.wireName, accent = selected == choice, onClick = { onSelect(choice) })
        }
    }
    BasicText(Strings.aiProbe.tokenLimitFieldHint(), style = TextStyle(color = BoarderLessTheme.colors.contentMuted, fontSize = 11.sp))
}
