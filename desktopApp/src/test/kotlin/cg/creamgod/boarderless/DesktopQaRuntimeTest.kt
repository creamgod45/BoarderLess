package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.qa.QaAnswer
import cg.creamgod.boarderless.feature.qa.QaQuestion
import cg.creamgod.boarderless.feature.qa.QaQuestionResult
import cg.creamgod.boarderless.feature.qa.QaReportDraft
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopQaRuntimeTest {
    @Test
    fun generatedPdfHasValidHeaderCrossReferenceAndTrailer() {
        val bytes =
            createQaPdf(
                QaReportDraft(
                    title = "BoarderLess QA 驗收",
                    version = "test",
                    platform = "Desktop",
                    deviceAndOrientation = "Desktop landscape",
                    tester = "QA",
                    date = "2026-09-30",
                    scope = "QA Workbench",
                    exclusions = "None",
                    automatedEvidence = "Tests passed",
                    generalNotes = "Ready for product visual sign-off",
                    questions =
                        listOf(
                            QaQuestionResult(
                                question = QaQuestion("visual", "Product", "Visual sign-off", true),
                                answer = QaAnswer.Pending,
                            ),
                        ),
                    screenshots = emptyList(),
                ),
            )
        val source = bytes.toString(StandardCharsets.ISO_8859_1)

        assertTrue(source.startsWith("%PDF-1.4"))
        assertTrue(source.endsWith("%%EOF\n"))
        val startXref = source.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        assertTrue(source.substring(startXref).startsWith("xref\n"))
        val objectOffsets =
            Regex("(?m)^(\\d{10}) 00000 n $")
                .findAll(source)
                .map { it.groupValues[1].toInt() }
                .toList()
        assertTrue(objectOffsets.isNotEmpty())
        objectOffsets.forEachIndexed { index, offset ->
            assertTrue(source.substring(offset).startsWith("${index + 1} 0 obj\n"))
        }
    }
}
