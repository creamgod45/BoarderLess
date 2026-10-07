package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Explicit review only; no canvas handlers, media fetching, restore, delete or submission controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceDraftReviewDialog(
    review: WorkspaceDraftReview,
    busy: Boolean,
    message: String?,
    onCopyBackup: (() -> Unit)?,
    onSaveBackup: (() -> Unit)?,
    saveChoosesFolder: Boolean,
    saveUsesBrowserDownload: Boolean,
    imported: Boolean = false,
    onCheckMerge: (suspend (WorkspaceDraftMergePlan, Map<DraftMergeFieldId, DraftMergeChoice>) -> DraftMergePreflightResult)? = null,
    onReloadReview: ((WorkspaceSession) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val differences = remember(review) { review.differences() }
    val mergePlan = remember(review, imported) { if (imported) WorkspaceDraftMergePlan(review) else null }
    var mergeChoices by remember(review) { mutableStateOf<Map<DraftMergeFieldId, DraftMergeChoice>>(emptyMap()) }
    var mergeMessage by remember(review) { mutableStateOf<String?>(null) }
    var checking by remember(review) { mutableStateOf(false) }
    var newerRemote by remember(review) { mutableStateOf<WorkspaceSession?>(null) }
    val scope = rememberCoroutineScope()
    val checkMerge by rememberUpdatedState(onCheckMerge)
    var copyArmed by remember(review.draftId) { mutableStateOf(false) }
    var saveArmed by remember(review.draftId) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(
                Modifier
                    .widthIn(max = 880.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 720.dp)
                    .fillMaxHeight(0.85f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        if (imported) Strings.draftImport.title() else Strings.draftReview.title(),
                        style = TextStyle(color = colors.contentText, fontSize = 18.sp),
                    )
                    BasicText(
                        Strings.draftReview.summary(review.operations.size, differences.size),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                    )
                    BasicText(
                        Strings.draftReview.versions(
                            review.baseVersion,
                            review.baseServerSeq,
                            review.currentVersion,
                            review.currentServerSeq,
                        ),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    BasicText(
                        if (review.baselineMatchesCurrent) Strings.draftReview.baselineMatches() else Strings.draftReview.baselineChanged(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                    )
                    if (review.quarantined || review.hasUnconfirmedSubmission) {
                        BasicText(
                            Strings.draftReview.submissionWarning(),
                            style = TextStyle(color = colors.accent, fontSize = 12.sp),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onCopyBackup !=
                            null
                        ) {
                            ShellButton(
                                label = if (copyArmed) Strings.draftReview.confirmCopy() else Strings.draftReview.copyBackup(),
                                enabled = !busy,
                                onClick = {
                                    saveArmed = false
                                    if (copyArmed) {
                                        copyArmed = false
                                        onCopyBackup()
                                    } else {
                                        copyArmed =
                                            true
                                    }
                                },
                            )
                        }
                        if (onSaveBackup != null) {
                            ShellButton(
                                label =
                                    if (saveArmed) {
                                        Strings.draftReview.confirmSave()
                                    } else if (saveUsesBrowserDownload) {
                                        Strings.draftReview.downloadBackup()
                                    } else {
                                        Strings.draftReview.saveBackup()
                                    },
                                enabled = !busy,
                                onClick = {
                                    copyArmed = false
                                    if (saveArmed) {
                                        saveArmed = false
                                        onSaveBackup()
                                    } else {
                                        saveArmed =
                                            true
                                    }
                                },
                            )
                        }
                        if (copyArmed) ShellButton(label = Strings.common.cancel(), enabled = !busy, onClick = { copyArmed = false })
                        if (saveArmed) ShellButton(label = Strings.common.cancel(), enabled = !busy, onClick = { saveArmed = false })
                        ShellButton(label = Strings.draftReview.close(), onClick = onDismiss)
                    }
                    if (copyArmed) {
                        BasicText(
                            Strings.draftReview.copyWarning(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    if (saveArmed) {
                        BasicText(
                            when {
                                saveUsesBrowserDownload -> Strings.draftReview.downloadWarning()
                                saveChoosesFolder -> Strings.draftReview.saveFolderWarning()
                                else -> Strings.draftReview.saveWarning()
                            },
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (mergePlan != null) {
                            item {
                                BasicText(Strings.draftMerge.title(), style = TextStyle(color = colors.contentText, fontSize = 14.sp))
                                BasicText(Strings.draftMerge.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                                ShellButton(
                                    label = Strings.draftMerge.validate(),
                                    enabled =
                                        !checking && !busy && newerRemote == null && mergeChoices.size == mergePlan.fields.size,
                                    onClick = {
                                        mergeMessage =
                                            if (runCatching { mergePlan.resolve(mergeChoices) }.isSuccess) {
                                                Strings.draftMerge.valid()
                                            } else {
                                                Strings.draftMerge.invalid()
                                            }
                                    },
                                )
                                mergeMessage?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                                if (onCheckMerge != null) {
                                    ShellButton(
                                        label = Strings.draftMerge.checkAuthority(),
                                        enabled = !checking && !busy && newerRemote == null && mergeChoices.size == mergePlan.fields.size,
                                        onClick = {
                                            checking = true
                                            mergeMessage = null
                                            val selected = mergeChoices.toMap()
                                            scope.launch {
                                                try {
                                                    val result = checkMerge?.invoke(mergePlan, selected)
                                                    ensureActive()
                                                    if (result is DraftMergePreflightResult.ReviewAgain) newerRemote = result.current
                                                    if (result is DraftMergePreflightResult.Blocked &&
                                                        result.reason == DraftMergePreflightBlock.ScopeExpired
                                                    ) {
                                                        onDismiss()
                                                    }
                                                    mergeMessage =
                                                        when (result) {
                                                            is DraftMergePreflightResult.Checked -> {
                                                                Strings.draftMerge.checked()
                                                            }

                                                            is DraftMergePreflightResult.ReviewAgain -> {
                                                                Strings.draftMerge.reviewAgain()
                                                            }

                                                            is DraftMergePreflightResult.Blocked -> {
                                                                Strings.draftMerge.blocked(
                                                                    result.reason.name,
                                                                )
                                                            }

                                                            null -> {
                                                                Strings.draftMerge.invalid()
                                                            }
                                                        }
                                                } catch (
                                                    cancelled: CancellationException,
                                                ) {
                                                    throw cancelled
                                                } catch (
                                                    _: Exception,
                                                ) {
                                                    mergeMessage = Strings.draftMerge.invalid()
                                                } finally {
                                                    checking = false
                                                }
                                            }
                                        },
                                    )
                                }
                                val latest = newerRemote
                                if (latest != null && onReloadReview != null) {
                                    ShellButton(
                                        label = Strings.draftMerge.reloadReview(),
                                        enabled = !checking && !busy,
                                        onClick = { onReloadReview(latest) },
                                    )
                                }
                            }
                            items(mergePlan.fields, key = { "merge:${it.id}" }) { field ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BasicText(
                                        (
                                            if (field.id.workspaceStyle) {
                                                "workspace:"
                                            } else if (field.id.relation) {
                                                "relation:"
                                            } else {
                                                "object:"
                                            }
                                        ) +
                                            field.id.entityId +
                                            "/" + field.id.path.joinToString("."),
                                        style = TextStyle(color = colors.accent, fontSize = 12.sp),
                                    )
                                    if (field.conflicts) {
                                        BasicText(
                                            Strings.draftReview.remoteChanged(),
                                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                        )
                                    }
                                    listOf(
                                        Strings.draftReview.baseline() to field.baseline,
                                        Strings.draftReview.proposed() to field.draft,
                                        Strings.draftReview.remote() to field.remote,
                                    ).forEach { (label, value) ->
                                        SelectionContainer {
                                            BasicText(
                                                "$label: ${value ?: Strings.draftReview.absent()}",
                                                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                            )
                                        }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        ShellButton(
                                            label = Strings.draftMerge.keepRemote(),
                                            accent =
                                                mergeChoices[field.id] == DraftMergeChoice.KeepRemote,
                                            enabled = !checking && !busy && newerRemote == null,
                                            onClick = {
                                                mergeChoices = mergeChoices + (field.id to DraftMergeChoice.KeepRemote)
                                                mergeMessage =
                                                    null
                                            },
                                        )
                                        ShellButton(
                                            label = Strings.draftMerge.useDraft(),
                                            accent =
                                                mergeChoices[field.id] == DraftMergeChoice.UseDraft,
                                            enabled = !checking && !busy && newerRemote == null,
                                            onClick = {
                                                mergeChoices = mergeChoices + (field.id to DraftMergeChoice.UseDraft)
                                                mergeMessage =
                                                    null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            BasicText(Strings.draftReview.readOnly(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                            SelectionContainer {
                                BasicText(
                                    review.operations
                                        .mapIndexed {
                                            index,
                                            operation,
                                            ->
                                            "${index + 1}. ${operation.operationId}"
                                        }.joinToString("\n"),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                )
                            }
                        }
                        if (differences.isEmpty()) {
                            item {
                                BasicText(
                                    Strings.draftReview.noDifferences(),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                                )
                            }
                        }
                        items(differences, key = { it.id }) { difference ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BasicText(difference.id, style = TextStyle(color = colors.accent, fontSize = 13.sp))
                                BasicText(
                                    when (difference.remoteComparison) {
                                        DraftRemoteComparison.Unchanged -> Strings.draftReview.remoteUnchanged()
                                        DraftRemoteComparison.MatchesDraft -> Strings.draftReview.remoteMatchesDraft()
                                        DraftRemoteComparison.Changed -> Strings.draftReview.remoteChanged()
                                    },
                                    style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                )
                                val values =
                                    when (difference) {
                                        is WorkspaceDraftDifference.ObjectChange -> {
                                            listOf(difference.before, difference.draft, difference.remote)
                                                .map { it?.let(::draftObjectJson) }
                                        }

                                        is WorkspaceDraftDifference.RelationChange -> {
                                            listOf(difference.before, difference.draft, difference.remote)
                                                .map { it?.let(::draftRelationJson) }
                                        }
                                    }
                                listOf(
                                    Strings.draftReview.baseline(),
                                    Strings.draftReview.proposed(),
                                    Strings.draftReview.remote(),
                                ).zip(values).forEach { (title, value) ->
                                    BasicText(title, style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                                    SelectionContainer {
                                        BasicText(
                                            value ?: Strings.draftReview.absent(),
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
    }
}
