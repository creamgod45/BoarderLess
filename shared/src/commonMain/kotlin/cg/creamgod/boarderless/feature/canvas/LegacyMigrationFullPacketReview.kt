package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.remote.LegacySubmissionMigrationReview
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.tr

/** Only the frozen reviewed packet. Navigation never recaptures, logs or writes storage. */
@Composable internal fun LegacyMigrationFullPacketReview(review: LegacySubmissionMigrationReview, enabled: Boolean) {
    val colors = BoarderLessTheme.colors
    var visible by remember(review) { mutableStateOf(false) }
    var scope by remember(review) { mutableStateOf(0) }
    var page by remember(review, scope) { mutableStateOf(0) }
    ShellButton(Strings.submissionMigration.inspectPacket(), enabled = enabled,
        onClick = { visible = !visible })
    if (!visible || review.scopes.isEmpty()) return
    val packet = remember(review, scope) { review.scopePacket(scope) }
    val pages = ((packet.length + 4095) / 4096).coerceAtLeast(1)
    val current = page.coerceIn(0, pages - 1)
    var start = current * 4096
    var end = minOf(packet.length, start + 4096)
    if (start > 0 && packet[start].isLowSurrogate() && packet[start - 1].isHighSurrogate()) start--
    if (end < packet.length && packet[end - 1].isHighSurrogate() && packet[end].isLowSurrogate()) end--
    BasicText(Strings.submissionMigration.packetWarning(),
        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
    BasicText("${scope + 1}/${review.scopes.size} · ${current + 1}/$pages", style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShellButton(Strings.submissionMigration.previousScope(), enabled = enabled && scope > 0, onClick = { scope-- })
        ShellButton(Strings.submissionMigration.nextScope(), enabled = enabled && scope + 1 < review.scopes.size, onClick = { scope++ })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShellButton(Strings.submissionMigration.previousPage(), enabled = enabled && current > 0, onClick = { page = current - 1 })
        ShellButton(Strings.submissionMigration.nextPage(), enabled = enabled && current + 1 < pages, onClick = { page = current + 1 })
    }
    SelectionContainer { BasicText(packet.substring(start, end), style = TextStyle(color = colors.contentText, fontSize = 11.sp)) }
}
