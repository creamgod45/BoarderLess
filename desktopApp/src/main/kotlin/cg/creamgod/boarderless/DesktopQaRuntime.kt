package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.qa.QaActionResult
import cg.creamgod.boarderless.feature.qa.QaDocumentType
import cg.creamgod.boarderless.feature.qa.QaGeneratedDocument
import cg.creamgod.boarderless.feature.qa.QaReportDraft
import cg.creamgod.boarderless.feature.qa.QaRuntime
import cg.creamgod.boarderless.feature.qa.QaScreenshot
import cg.creamgod.boarderless.feature.qa.overallAnswer
import cg.creamgod.boarderless.feature.qa.toPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal fun desktopQaRuntime(window: Window): QaRuntime {
    val enabled =
        qaFlagEnabled(System.getenv("BOARDERLESS_QA_MODE")) ||
            qaFlagEnabled(System.getProperty("boarderless.qaMode"))
    if (!enabled) return QaRuntime.Disabled

    return QaRuntime(
        enabled = true,
        environmentLabel = "Desktop development QA",
        canCaptureWindow = true,
        canChooseScreenshot = true,
        canExportHtml = true,
        canExportPdf = true,
        captureWindow = {
            runQaAction {
                val location = window.locationOnScreen
                val bounds = Rectangle(location.x, location.y, window.width, window.height)
                require(bounds.width > 0 && bounds.height > 0) { "BoarderLess window has no drawable size" }
                val image = Robot().createScreenCapture(bounds)
                image.toQaScreenshot("boarderless-window-${System.currentTimeMillis()}.jpg")
            }
        },
        chooseScreenshot = {
            val chooser =
                JFileChooser().apply {
                    dialogTitle = "Attach QA screenshot"
                    fileFilter = FileNameExtensionFilter("PNG or JPEG screenshots", "png", "jpg", "jpeg")
                    isAcceptAllFileFilterUsed = false
                }
            if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
                QaActionResult(message = "Screenshot selection cancelled")
            } else {
                runQaAction {
                    val selected = chooser.selectedFile
                    val image = ImageIO.read(selected) ?: error("The selected file is not a readable image")
                    image.toQaScreenshot(selected.name)
                }
            }
        },
        exportHtml = { fileName, html ->
            withContext(Dispatchers.IO) {
                runQaAction {
                    val target = qaOutputFile(fileName, "html")
                    Files.writeString(
                        target,
                        html,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    )
                    QaGeneratedDocument(QaDocumentType.Html, target.fileName.toString(), target.toString())
                }
            }
        },
        exportPdf = { fileName, report ->
            withContext(Dispatchers.IO) {
                runQaAction {
                    val target = qaOutputFile(fileName, "pdf")
                    Files.write(target, createQaPdf(report))
                    QaGeneratedDocument(QaDocumentType.Pdf, target.fileName.toString(), target.toString())
                }
            }
        },
        openDocument = { document ->
            runQaAction {
                require(Desktop.isDesktopSupported()) { "Desktop document opening is unavailable" }
                Desktop.getDesktop().open(Path.of(document.location).toFile())
            }
        },
    )
}

private fun qaFlagEnabled(value: String?): Boolean =
    value
        ?.trim()
        ?.lowercase()
        .let { it == "1" || it == "true" || it == "yes" || it == "on" }

private inline fun <T> runQaAction(block: () -> T): QaActionResult<T> =
    try {
        QaActionResult(value = block())
    } catch (error: Throwable) {
        QaActionResult(message = error.message ?: error::class.simpleName ?: "QA action failed")
    }

private fun qaOutputFile(
    fileName: String,
    extension: String,
): Path {
    val outputDirectory =
        Paths
            .get(
                System.getProperty("user.home"),
                "Documents",
                "BoarderLess QA",
            ).toAbsolutePath()
            .normalize()
    Files.createDirectories(outputDirectory)
    val safeName =
        fileName
            .map { character -> if (character.isLetterOrDigit() || character == '-' || character == '_') character else '-' }
            .joinToString("")
            .trim('-')
            .take(72)
            .ifBlank { "boarderless-qa" }
    val target = outputDirectory.resolve("$safeName.$extension").normalize()
    require(target.parent == outputDirectory) { "Invalid QA document name" }
    return target
}

private fun BufferedImage.toQaScreenshot(fileName: String): QaScreenshot {
    val rgb =
        if (type == BufferedImage.TYPE_INT_RGB) {
            this
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also {
                val graphics = it.createGraphics()
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, width, height)
                graphics.drawImage(this, 0, 0, null)
                graphics.dispose()
            }
        }
    val bytes =
        ByteArrayOutputStream().use { output ->
            check(ImageIO.write(rgb, "jpg", output)) { "JPEG encoder unavailable" }
            output.toByteArray()
        }
    return QaScreenshot(
        id = "screenshot-${System.currentTimeMillis()}-${fileName.hashCode()}",
        fileName = fileName.substringBeforeLast('.') + ".jpg",
        mediaType = "image/jpeg",
        base64 = Base64.getEncoder().encodeToString(bytes),
    )
}

internal fun createQaPdf(report: QaReportDraft): ByteArray {
    val pages = renderReportPages(report)
    val jpegPages =
        pages.map { page ->
            ByteArrayOutputStream().use { output ->
                check(ImageIO.write(page, "jpg", output)) { "JPEG encoder unavailable" }
                output.toByteArray()
            }
        }
    val objectCount = 2 + jpegPages.size * 3
    val output = ByteArrayOutputStream()
    val offsets = IntArray(objectCount + 1)

    fun ascii(value: String) = output.write(value.toByteArray(StandardCharsets.ISO_8859_1))

    fun beginObject(number: Int) {
        offsets[number] = output.size()
        ascii("$number 0 obj\n")
    }

    fun endObject() = ascii("endobj\n")

    ascii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
    beginObject(1)
    ascii("<< /Type /Catalog /Pages 2 0 R >>\n")
    endObject()
    beginObject(2)
    val pageReferences = jpegPages.indices.joinToString(" ") { index -> "${3 + index * 3} 0 R" }
    ascii("<< /Type /Pages /Count ${jpegPages.size} /Kids [$pageReferences] >>\n")
    endObject()

    jpegPages.forEachIndexed { index, jpeg ->
        val pageObject = 3 + index * 3
        val contentObject = pageObject + 1
        val imageObject = pageObject + 2
        beginObject(pageObject)
        ascii(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /PageImage $imageObject 0 R >> >> /Contents $contentObject 0 R >>\n",
        )
        endObject()
        val content = "q\n595 0 0 842 0 0 cm\n/PageImage Do\nQ\n".toByteArray(StandardCharsets.US_ASCII)
        beginObject(contentObject)
        ascii("<< /Length ${content.size} >>\nstream\n")
        output.write(content)
        ascii("endstream\n")
        endObject()
        beginObject(imageObject)
        ascii(
            "<< /Type /XObject /Subtype /Image /Width 1240 /Height 1754 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n",
        )
        output.write(jpeg)
        ascii("\nendstream\n")
        endObject()
    }

    val xrefOffset = output.size()
    ascii("xref\n0 ${objectCount + 1}\n")
    ascii("0000000000 65535 f \n")
    for (number in 1..objectCount) ascii("${offsets[number].toString().padStart(10, '0')} 00000 n \n")
    ascii("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
    return output.toByteArray()
}

private fun renderReportPages(report: QaReportDraft): List<BufferedImage> {
    val width = 1240
    val height = 1754
    val margin = 92
    val contentWidth = width - margin * 2
    val pages = mutableListOf<BufferedImage>()
    var image = newPdfPage(width, height)
    var graphics = image.createGraphics().configured()
    var y = margin

    fun finishPage() {
        graphics.dispose()
        pages += image
        image = newPdfPage(width, height)
        graphics = image.createGraphics().configured()
        y = margin
    }

    fun drawParagraph(
        text: String,
        font: Font,
        color: Color = Color(0x24, 0x24, 0x21),
        spacing: Int = 12,
    ) {
        graphics.font = font
        graphics.color = color
        val metrics = graphics.fontMetrics
        val lines = text.lines().flatMap { line -> wrapText(line.ifEmpty { " " }, metrics, contentWidth) }
        lines.forEach { line ->
            if (y + metrics.height > height - margin) finishPage()
            graphics.drawString(line, margin, y + metrics.ascent)
            y += metrics.height + spacing
        }
    }

    drawParagraph(report.title.ifBlank { "BoarderLess QA Acceptance" }, Font("SansSerif", Font.BOLD, 34), spacing = 20)
    drawParagraph("Overall: ${report.overallAnswer().token}", Font("SansSerif", Font.BOLD, 22), Color(0x62, 0x55, 0xD9), 12)
    drawParagraph(report.toPlainText().substringAfter('\n').substringAfter('\n'), Font("SansSerif", Font.PLAIN, 17), spacing = 8)

    report.screenshots.forEach { screenshot ->
        val decoded = runCatching { Base64.getDecoder().decode(screenshot.base64) }.getOrNull() ?: return@forEach
        val screenshotImage = ImageIO.read(decoded.inputStream()) ?: return@forEach
        val captionFont = Font("SansSerif", Font.BOLD, 17)
        val maxImageHeight = 900
        val scale = minOf(contentWidth.toDouble() / screenshotImage.width, maxImageHeight.toDouble() / screenshotImage.height, 1.0)
        val drawWidth = (screenshotImage.width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (screenshotImage.height * scale).toInt().coerceAtLeast(1)
        if (y + drawHeight + 80 > height - margin) finishPage()
        drawParagraph(screenshot.caption.ifBlank { screenshot.fileName }, captionFont, spacing = 8)
        graphics.drawImage(screenshotImage, margin, y, drawWidth, drawHeight, null)
        y += drawHeight + 28
    }
    graphics.dispose()
    pages += image
    return pages
}

private fun newPdfPage(
    width: Int,
    height: Int,
) = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
    val graphics = image.createGraphics()
    graphics.color = Color.WHITE
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
}

private fun Graphics2D.configured(): Graphics2D =
    apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

private fun wrapText(
    text: String,
    metrics: java.awt.FontMetrics,
    maxWidth: Int,
): List<String> {
    if (text.isBlank()) return listOf(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.forEach { character ->
        val candidate = current.toString() + character
        if (current.isNotEmpty() && metrics.stringWidth(candidate) > maxWidth) {
            lines += current.toString().trimEnd()
            current = StringBuilder().append(character)
        } else {
            current.append(character)
        }
    }
    if (current.isNotEmpty()) lines += current.toString().trimEnd()
    return lines
}
