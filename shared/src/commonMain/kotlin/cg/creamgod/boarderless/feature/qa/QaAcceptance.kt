package cg.creamgod.boarderless.feature.qa

enum class QaAnswer(val token: String) {
    Pending("pending"),
    Passed("passed"),
    Failed("failed"),
    NotApplicable("not_applicable"),
}

data class QaQuestion(
    val id: String,
    val category: String,
    val prompt: String,
    val requiresProductSignoff: Boolean = false,
)

data class QaQuestionResult(
    val question: QaQuestion,
    val answer: QaAnswer = QaAnswer.Pending,
    val note: String = "",
)

data class QaScreenshot(
    val id: String,
    val fileName: String,
    val mediaType: String,
    val base64: String,
    val caption: String = "",
) {
    val dataUrl: String
        get() = "data:$mediaType;base64,$base64"

    init {
        require(mediaType == "image/png" || mediaType == "image/jpeg") {
            "QA screenshots must be PNG or JPEG"
        }
        require(base64.isNotBlank()) { "QA screenshot data must not be blank" }
    }
}

data class QaReportDraft(
    val title: String,
    val version: String,
    val platform: String,
    val deviceAndOrientation: String,
    val tester: String,
    val date: String,
    val scope: String,
    val exclusions: String,
    val automatedEvidence: String,
    val generalNotes: String,
    val questions: List<QaQuestionResult>,
    val screenshots: List<QaScreenshot>,
)

data class QaGeneratedDocument(
    val type: QaDocumentType,
    val displayName: String,
    val location: String,
)

enum class QaDocumentType { Html, Pdf }

data class QaActionResult<T>(
    val value: T? = null,
    val message: String? = null,
) {
    val succeeded: Boolean get() = value != null
}

class QaRuntime(
    val enabled: Boolean,
    val environmentLabel: String,
    val canCaptureWindow: Boolean = false,
    val canChooseScreenshot: Boolean = false,
    val canExportHtml: Boolean = false,
    val canExportPdf: Boolean = false,
    val captureWindow: suspend () -> QaActionResult<QaScreenshot> = {
        QaActionResult(message = "Window capture is unavailable on this platform")
    },
    val chooseScreenshot: suspend () -> QaActionResult<QaScreenshot> = {
        QaActionResult(message = "Screenshot selection is unavailable on this platform")
    },
    val exportHtml: suspend (fileName: String, html: String) -> QaActionResult<QaGeneratedDocument> = { _, _ ->
        QaActionResult(message = "HTML export is unavailable on this platform")
    },
    val exportPdf: suspend (fileName: String, report: QaReportDraft) -> QaActionResult<QaGeneratedDocument> = { _, _ ->
        QaActionResult(message = "PDF export is unavailable on this platform")
    },
    val openDocument: (QaGeneratedDocument) -> QaActionResult<Unit> = {
        QaActionResult(message = "Opening documents is unavailable on this platform")
    },
) {
    companion object {
        val Disabled = QaRuntime(enabled = false, environmentLabel = "production")
    }
}

fun QaReportDraft.overallAnswer(): QaAnswer = when {
    questions.any { it.answer == QaAnswer.Failed } -> QaAnswer.Failed
    questions.any { it.answer == QaAnswer.Pending } -> QaAnswer.Pending
    questions.all { it.answer == QaAnswer.NotApplicable } -> QaAnswer.NotApplicable
    else -> QaAnswer.Passed
}

fun QaReportDraft.toQaHtml(sopReference: String): String {
    val safeTitle = title.ifBlank { "BoarderLess QA Acceptance" }.escapeHtml()
    val status = overallAnswer()
    val rows = questions.joinToString("\n") { result ->
        """
        <tr>
          <td>${result.question.category.escapeHtml()}</td>
          <td>${result.question.prompt.escapeHtml()}${if (result.question.requiresProductSignoff) " <span class=\"owner\">Product sign-off</span>" else ""}</td>
          <td><span class=\"status ${result.answer.token}\">${result.answer.token.replace('_', ' ').escapeHtml()}</span></td>
          <td>${result.note.escapeHtml().replace("\n", "<br>")}</td>
        </tr>
        """.trimIndent()
    }
    val screenshotHtml = if (screenshots.isEmpty()) {
        "<p class=\"muted\">No screenshots attached.</p>"
    } else {
        screenshots.joinToString("\n") { screenshot ->
            """
            <figure>
              <img src="${screenshot.dataUrl}" alt="${screenshot.caption.ifBlank { screenshot.fileName }.escapeHtml()}">
              <figcaption>${screenshot.caption.ifBlank { screenshot.fileName }.escapeHtml()}</figcaption>
            </figure>
            """.trimIndent()
        }
    }
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>$safeTitle</title>
          <style>
            :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
            body { margin: 0 auto; max-width: 1040px; padding: 36px; color: #242421; background: #f4f3ef; }
            h1, h2 { line-height: 1.2; } .summary, section { background: white; border: 1px solid #d8d5cb; border-radius: 16px; padding: 20px; margin: 16px 0; }
            dl { display: grid; grid-template-columns: 180px 1fr; gap: 8px 18px; } dt { color: #6f6d65; } dd { margin: 0; white-space: pre-wrap; }
            table { width: 100%; border-collapse: collapse; } th, td { border-bottom: 1px solid #e1dfd8; padding: 10px; text-align: left; vertical-align: top; }
            .status, .owner { display: inline-block; border-radius: 999px; padding: 3px 8px; font-size: 12px; font-weight: 650; }
            .passed { background: #dff5e7; color: #17643a; } .failed { background: #ffe1e1; color: #922c2c; }
            .pending { background: #fff1cf; color: #77540d; } .not_applicable { background: #ecebea; color: #5f5d57; }
            .owner { background: #f0edff; color: #4c3fb0; margin-left: 5px; } .muted { color: #6f6d65; }
            figure { break-inside: avoid; margin: 20px 0; } img { display: block; max-width: 100%; max-height: 760px; border: 1px solid #d8d5cb; border-radius: 12px; }
            figcaption { color: #6f6d65; margin-top: 7px; } @media print { body { background: white; padding: 0; } section, .summary { break-inside: avoid; } }
          </style>
        </head>
        <body>
          <h1>$safeTitle</h1>
          <p class="muted">Generated from the BoarderLess development QA Workbench. SOP: ${sopReference.escapeHtml()}</p>
          <div class="summary">
            <dl>
              <dt>Overall status</dt><dd><span class="status ${status.token}">${status.token.replace('_', ' ').escapeHtml()}</span></dd>
              <dt>Version / commit</dt><dd>${version.escapeHtml()}</dd>
              <dt>Platform</dt><dd>${platform.escapeHtml()}</dd>
              <dt>Device / orientation</dt><dd>${deviceAndOrientation.escapeHtml()}</dd>
              <dt>Tester</dt><dd>${tester.escapeHtml()}</dd>
              <dt>Date</dt><dd>${date.escapeHtml()}</dd>
              <dt>Scope</dt><dd>${scope.escapeHtml()}</dd>
              <dt>Exclusions</dt><dd>${exclusions.escapeHtml()}</dd>
              <dt>Automated evidence</dt><dd>${automatedEvidence.escapeHtml()}</dd>
            </dl>
          </div>
          <section><h2>Acceptance questionnaire</h2><table><thead><tr><th>Category</th><th>Question</th><th>Result</th><th>Notes</th></tr></thead><tbody>$rows</tbody></table></section>
          <section><h2>Screenshots</h2>$screenshotHtml</section>
          <section><h2>General notes</h2><p>${generalNotes.escapeHtml().replace("\n", "<br>")}</p></section>
        </body>
        </html>
    """.trimIndent()
}

fun QaReportDraft.toPlainText(): String = buildString {
    appendLine(title.ifBlank { "BoarderLess QA Acceptance" })
    appendLine("Overall: ${overallAnswer().token}")
    appendLine("Version: $version")
    appendLine("Platform: $platform")
    appendLine("Device / orientation: $deviceAndOrientation")
    appendLine("Tester: $tester")
    appendLine("Date: $date")
    appendLine("Scope: $scope")
    appendLine("Exclusions: $exclusions")
    appendLine("Automated evidence: $automatedEvidence")
    appendLine()
    appendLine("Acceptance questionnaire")
    questions.forEach { result ->
        append("[${result.answer.token}] ${result.question.category}: ${result.question.prompt}")
        if (result.question.requiresProductSignoff) append(" (Product sign-off)")
        appendLine()
        if (result.note.isNotBlank()) appendLine("  Note: ${result.note}")
    }
    appendLine()
    appendLine("Screenshots: ${screenshots.size}")
    screenshots.forEach { appendLine("- ${it.caption.ifBlank { it.fileName }}") }
    appendLine()
    appendLine("General notes: $generalNotes")
}

internal fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}

