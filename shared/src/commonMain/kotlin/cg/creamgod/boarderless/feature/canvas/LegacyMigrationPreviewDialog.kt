package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository
import cg.creamgod.boarderless.data.remote.LegacySubmissionMigrationReview
import cg.creamgod.boarderless.data.remote.LegacyMigrationCatalogInput
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

/** Actual platform entry for source inspection; deliberately no publication/activation.
 * Summaries expose no draft text, endpoints, credentials or exact object identifiers. */
@Composable internal fun LegacyMigrationPreviewDialog(repository: BackendWorkspaceRepository,
    owner: WorkspaceSession, canContinue: () -> Boolean, onDismiss: () -> Unit) {
    val colors = BoarderLessTheme.colors
    val gate by rememberUpdatedState(canContinue)
    var revision by remember(repository, owner) { mutableStateOf(0) }
    var busy by remember(repository, owner) { mutableStateOf(false) }
    var review by remember(repository, owner) { mutableStateOf<LegacySubmissionMigrationReview?>(null) }
    var failed by remember(repository, owner) { mutableStateOf(false) }
    var catalogDraft by remember(repository, owner) { mutableStateOf("") }
    var submittedCatalog by remember(repository, owner) { mutableStateOf<String?>(null) }
    var catalogVisible by remember(repository, owner) { mutableStateOf(false) }
    var catalogTooLarge by remember(repository, owner) { mutableStateOf(false) }
    LaunchedEffect(repository, owner, revision) {
        busy = true; failed = false; review = null
        try {
            check(gate())
            val loaded = repository.reviewLegacyMigration(owner, submittedCatalog)
            ensureActive(); check(gate()); review = loaded
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (gate()) failed = true }
        finally { busy = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.85f)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(tr("submissionMigration.title", "Review durable draft migration"), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(tr("submissionMigration.previewOnly", "Read-only source review. No data is migrated, no transaction is resent, and no storage mode is changed. Publication and activation are not yet connected on this platform."), style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    if (busy) BasicText(tr("submissionMigration.reading", "Reading complete local source…"), style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    if (failed) BasicText(tr("submissionMigration.previewFailed", "Cannot confirm the complete source. A missing legacy scope/archive catalog, identity/counter, corrupt data or changed source needs recovery; original data is retained."), style = TextStyle(color = colors.danger, fontSize = 13.sp))
                    review?.let { packet ->
                        BasicText("Source SHA-256: ${packet.sourceDigest}\nPlan SHA-256: ${packet.planDigest}", style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                        LegacyMigrationFullPacketReview(packet, enabled = !busy && gate())
                        packet.scopes.forEachIndexed { index, item ->
                            BasicText(tr("submissionMigration.scope", "Scope {index}: {hash}\nDraft operations: {operations}; pending: {pending}; stopped: {stopped}\nFences: {fences}; deletion evidence: {deletions}; sequence floor: {floor}; quarantined: {quarantined}",
                                "index" to index + 1, "hash" to item.scopeHash, "operations" to item.draftOperationCount,
                                "pending" to item.hasPendingWire, "stopped" to item.stoppedWireCount, "fences" to item.fenceCount,
                                "deletions" to item.deletionEvidenceCount, "floor" to item.minimumClientSequence,
                                "quarantined" to item.retryQuarantined), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                        }
                    }
                    ShellButton(tr("submissionMigration.catalogToggle", "Supplement original scope/archive locators"),
                        enabled = !busy, onClick = { catalogVisible = !catalogVisible })
                    if (catalogVisible) {
                        BasicText(tr("submissionMigration.catalogHint", "Optional private JSON: {\"version\":1,\"scopes\":[{\"apiBase\":\"exact original /api/v1 URL\",\"userId\":\"original ID\",\"clientId\":\"original ID\",\"workspaceId\":\"original ID\"}],\"stopped\":[{\"scope\":{\"apiBase\":\"…\",\"userId\":\"…\",\"clientId\":\"…\",\"workspaceId\":\"…\"},\"transactionId\":\"original transaction ID\"}]}. Use original values, never API keys. This is only locator input; records must match existing hashes and complete contents. Nothing is saved or sent."), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        BasicTextField(catalogDraft, onValueChange = { next ->
                            val fits = next.length <= LegacyMigrationCatalogInput.MaximumBytes && runCatching {
                                next.encodeToByteArray(throwOnInvalidSequence = true).size <= LegacyMigrationCatalogInput.MaximumBytes
                            }.getOrDefault(false)
                            catalogTooLarge = !fits
                            if (fits) { catalogDraft = next; review = null; failed = false }
                        }, enabled = !busy && gate(), textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp).padding(6.dp))
                        if (catalogTooLarge) BasicText(tr("submissionMigration.catalogLimit", "Input exceeds 4 MiB or contains invalid Unicode; it was not accepted."), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    }
                    ShellButton(tr("submissionMigration.refresh", "Read source again"), enabled = !busy && gate(), onClick = {
                        submittedCatalog = catalogDraft.takeIf { it.isNotBlank() }
                        revision++
                    })
                    ShellButton(tr("submissionMigration.close", "Close (retain records)"), onClick = onDismiss)
                }
            }
        }
    }
}
