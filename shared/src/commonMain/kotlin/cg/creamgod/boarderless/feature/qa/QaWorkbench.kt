package cg.creamgod.boarderless.feature.qa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.GlassSurface
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.designsystem.ShellIcon
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.launch

@Composable
fun QaWorkbenchHost(runtime: QaRuntime) {
    if (!runtime.enabled) return
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 8.dp, end = 10.dp),
        ) {
            ShellButton(
                label = Strings.qa.openWorkbench(),
                icon = ShellIcon.QaChecklist,
                showLabel = false,
                accent = open,
                onClick = { open = true },
            )
        }
        if (open) QaWorkbench(runtime = runtime, onClose = { open = false })
    }
}

@Composable
private fun QaWorkbench(runtime: QaRuntime, onClose: () -> Unit) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val questions = rememberQaQuestions()
    val answers = remember { mutableStateMapOf<String, QaAnswer>() }
    val notes = remember { mutableStateMapOf<String, String>() }
    val screenshots = remember { mutableStateListOf<QaScreenshot>() }
    val generatedDocuments = remember { mutableStateListOf<QaGeneratedDocument>() }
    var title by remember { mutableStateOf(Strings.qa.defaultReportTitle()) }
    var version by remember { mutableStateOf("") }
    var platform by remember(runtime.environmentLabel) { mutableStateOf(runtime.environmentLabel) }
    var device by remember { mutableStateOf("") }
    var tester by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var reportScope by remember { mutableStateOf("") }
    var exclusions by remember { mutableStateOf("") }
    var automatedEvidence by remember { mutableStateOf("") }
    var generalNotes by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf(Strings.qa.developmentOnly()) }

    fun draft(): QaReportDraft = QaReportDraft(
        title = title,
        version = version,
        platform = platform,
        deviceAndOrientation = device,
        tester = tester,
        date = date,
        scope = reportScope,
        exclusions = exclusions,
        automatedEvidence = automatedEvidence,
        generalNotes = generalNotes,
        questions = questions.map { question ->
            QaQuestionResult(
                question = question,
                answer = answers[question.id] ?: QaAnswer.Pending,
                note = notes[question.id].orEmpty(),
            )
        },
        screenshots = screenshots.toList(),
    )

    fun addScreenshot(action: suspend () -> QaActionResult<QaScreenshot>) {
        scope.launch {
            val result = action()
            result.value?.let { screenshots += it }
            feedback = result.message ?: if (result.succeeded) Strings.qa.screenshotAttached() else Strings.common.unknownError()
        }
    }

    fun export(type: QaDocumentType) {
        scope.launch {
            val report = draft()
            val fileName = reportFileName(title, date)
            val result = when (type) {
                QaDocumentType.Html -> runtime.exportHtml(
                    fileName,
                    report.toQaHtml("docs/產品 QA 品質檢驗 SOP.md"),
                )
                QaDocumentType.Pdf -> runtime.exportPdf(fileName, report)
            }
            result.value?.let { generatedDocuments += it }
            feedback = result.message ?: if (result.succeeded) Strings.qa.documentGenerated() else Strings.common.unknownError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas.copy(alpha = 0.82f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = Strings.qa.closeWorkbench()
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClick = onClose),
        )
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .widthIn(max = 980.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = Strings.qa.workbench(),
                            style = TextStyle(colors.contentText, 20.sp, FontWeight.Bold),
                        )
                        BasicText(
                            text = Strings.qa.environment(runtime.environmentLabel),
                            style = TextStyle(colors.contentMuted, 11.sp),
                        )
                    }
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        showLabel = false,
                        onClick = onClose,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QaSection(Strings.qa.reportMetadata()) {
                        QaTextField(Strings.qa.reportTitle(), title, { title = it })
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QaTextField(Strings.qa.versionCommit(), version, { version = it }, Modifier.width(250.dp))
                            QaTextField(Strings.qa.date(), date, { date = it }, Modifier.width(180.dp))
                            QaTextField(Strings.qa.tester(), tester, { tester = it }, Modifier.width(220.dp))
                        }
                        QaTextField(Strings.qa.platform(), platform, { platform = it })
                        QaTextField(Strings.qa.deviceOrientation(), device, { device = it })
                        QaTextField(Strings.qa.scope(), reportScope, { reportScope = it }, singleLine = false)
                        QaTextField(Strings.qa.exclusions(), exclusions, { exclusions = it }, singleLine = false)
                        QaTextField(
                            Strings.qa.automatedEvidence(),
                            automatedEvidence,
                            { automatedEvidence = it },
                            singleLine = false,
                        )
                    }

                    QaSection(Strings.qa.questionnaire()) {
                        questions.forEach { question ->
                            QaQuestionEditor(
                                question = question,
                                answer = answers[question.id] ?: QaAnswer.Pending,
                                note = notes[question.id].orEmpty(),
                                onAnswer = { answers[question.id] = it },
                                onNote = { notes[question.id] = it },
                            )
                        }
                    }

                    QaSection(Strings.qa.screenshots()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShellButton(
                                label = Strings.qa.captureWindow(),
                                icon = ShellIcon.Add,
                                enabled = runtime.canCaptureWindow,
                                onClick = { addScreenshot(runtime.captureWindow) },
                            )
                            ShellButton(
                                label = Strings.qa.chooseScreenshot(),
                                icon = ShellIcon.Library,
                                enabled = runtime.canChooseScreenshot,
                                onClick = { addScreenshot(runtime.chooseScreenshot) },
                            )
                        }
                        if (screenshots.isEmpty()) {
                            QaHint(Strings.qa.noScreenshots())
                        } else {
                            screenshots.forEachIndexed { index, screenshot ->
                                QaAttachmentEditor(
                                    screenshot = screenshot,
                                    onCaption = { caption -> screenshots[index] = screenshot.copy(caption = caption) },
                                    onRemove = { screenshots.removeAt(index) },
                                )
                            }
                        }
                    }

                    QaSection(Strings.qa.notesAndDocuments()) {
                        QaTextField(Strings.qa.generalNotes(), generalNotes, { generalNotes = it }, singleLine = false)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShellButton(
                                label = Strings.qa.exportHtml(),
                                icon = ShellIcon.Copy,
                                enabled = runtime.canExportHtml,
                                onClick = { export(QaDocumentType.Html) },
                            )
                            ShellButton(
                                label = Strings.qa.exportPdf(),
                                icon = ShellIcon.Schemes,
                                enabled = runtime.canExportPdf,
                                onClick = { export(QaDocumentType.Pdf) },
                            )
                        }
                        QaHint(feedback)
                        if (generatedDocuments.isEmpty()) {
                            QaHint(Strings.qa.noDocuments())
                        } else {
                            generatedDocuments.forEach { document ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        BasicText(
                                            text = document.displayName,
                                            style = TextStyle(colors.contentText, 12.sp, FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        BasicText(
                                            text = document.location,
                                            style = TextStyle(colors.contentMuted, 10.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    ShellButton(
                                        label = Strings.qa.openDocument(),
                                        icon = ShellIcon.Forward,
                                        onClick = {
                                            val result = runtime.openDocument(document)
                                            feedback = result.message ?: if (result.succeeded) {
                                                Strings.qa.documentOpened()
                                            } else {
                                                Strings.common.unknownError()
                                            }
                                        },
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

@Composable
private fun rememberQaQuestions(): List<QaQuestion> = qaQuestionCatalog()

@Composable
private fun QaSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = BoarderLessTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.canvas.copy(alpha = 0.56f))
            .border(1.dp, colors.contentBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title, style = TextStyle(colors.contentText, 14.sp, FontWeight.Bold))
        content()
    }
}

@Composable
private fun QaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
) {
    val colors = BoarderLessTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BasicText(label, style = TextStyle(colors.contentMuted, 10.sp, FontWeight.SemiBold))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(colors.contentText, 13.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 42.dp else 74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.contentSurface)
                .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                .padding(11.dp),
        )
    }
}

@Composable
private fun QaQuestionEditor(
    question: QaQuestion,
    answer: QaAnswer,
    note: String,
    onAnswer: (QaAnswer) -> Unit,
    onNote: (String) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.contentSurface)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BasicText(
            text = "${question.category} • ${question.prompt}",
            style = TextStyle(colors.contentText, 12.sp, FontWeight.Medium),
        )
        if (question.requiresProductSignoff) QaHint(Strings.qa.productSignoffRequired())
        if (question.sopSection.isNotBlank()) QaHint("SOP §${question.sopSection}")
        if (question.evidenceHint.isNotBlank()) QaHint(question.evidenceHint)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            QaAnswer.entries.forEach { candidate ->
                ShellButton(
                    label = when (candidate) {
                        QaAnswer.Pending -> Strings.qa.pending()
                        QaAnswer.Passed -> Strings.qa.pass()
                        QaAnswer.Failed -> Strings.qa.fail()
                        QaAnswer.NotApplicable -> Strings.qa.notApplicable()
                    },
                    accent = answer == candidate,
                    onClick = { onAnswer(candidate) },
                )
            }
        }
        QaTextField(Strings.qa.itemNote(), note, onNote, singleLine = false)
    }
}

@Composable
private fun QaAttachmentEditor(
    screenshot: QaScreenshot,
    onCaption: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.contentSurface)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(
                text = screenshot.fileName,
                style = TextStyle(colors.contentText, 11.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            QaTextField(Strings.qa.screenshotCaption(), screenshot.caption, onCaption)
        }
        ShellButton(
            label = Strings.qa.removeAttachment(),
            icon = ShellIcon.Delete,
            showLabel = false,
            onClick = onRemove,
        )
    }
}

@Composable
private fun QaHint(text: String) {
    BasicText(
        text = text,
        style = TextStyle(BoarderLessTheme.colors.contentMuted, 10.sp),
    )
}

private fun reportFileName(title: String, date: String): String {
    val safe = title.lowercase().map { character ->
        when {
            character.isLetterOrDigit() -> character
            character == '-' || character == '_' -> character
            else -> '-'
        }
    }.joinToString("").trim('-').take(48).ifBlank { "boarderless-qa" }
    val suffix = date.map { if (it.isLetterOrDigit() || it == '-') it else '-' }
        .joinToString("").trim('-').take(20)
    return if (suffix.isBlank()) safe else "$safe-$suffix"
}
