package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.qa.QaAnswer
import cg.creamgod.boarderless.feature.qa.QaQuestion
import cg.creamgod.boarderless.feature.qa.QaQuestionResult
import cg.creamgod.boarderless.feature.qa.QaReportDraft
import cg.creamgod.boarderless.feature.qa.overallAnswer
import cg.creamgod.boarderless.feature.qa.toQaHtml
import cg.creamgod.boarderless.feature.qa.toPlainText
import cg.creamgod.boarderless.feature.qa.qaQuestionCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QaAcceptanceTest {
    @Test fun draftRecoveryAcceptanceRemainsPendingAndExportsItsEvidence() {
        val questions = qaQuestionCatalog()
        val question = questions.single { it.id == "collaboration-draft-recovery" }
        assertEquals("6", question.sopSection)
        assertTrue(question.evidenceHint.isNotBlank())
        val draft = report(questions.map { QaQuestionResult(it, if (it.id == question.id) QaAnswer.Pending else QaAnswer.Passed) })
            .copy(automatedEvidence = "All offline tests pass")
        assertEquals(QaAnswer.Pending, draft.overallAnswer())
        assertTrue("SOP §6" in draft.toQaHtml("QA SOP"))
        assertTrue(question.evidenceHint in draft.toPlainText())
        assertEquals("performance", questions.last().id)
    }
    @Test fun mediaCatalogKeepsStableIdsSopEvidenceAndPendingProductSignoff() {
        val questions = qaQuestionCatalog()
        assertEquals(questions.size, questions.map { it.id }.toSet().size)
        assertTrue(setOf("functional", "persistence", "permissions", "mobile", "visual", "occlusion", "operation", "voiceover", "regression", "performance")
            .all { id -> questions.any { it.id == id } })
        val media = questions.filter { it.id.startsWith("media-") }
        assertEquals(15, media.size)
        assertTrue(media.all { it.sopSection.isNotBlank() && it.evidenceHint.isNotBlank() && it.prompt.isNotBlank() })
        assertTrue(media.single { it.id == "media-visual" }.requiresProductSignoff)
        assertEquals("performance", questions.last().id)
        val results = questions.map { QaQuestionResult(it) }
        assertTrue(results.all { it.answer == QaAnswer.Pending })
        assertEquals(QaAnswer.Pending, report(results).overallAnswer())
    }

    @Test fun newMediaRecoveryQuestionsStayPendingAndRetainSopReferencesInBothExports() {
        val expected = mapOf("media-library" to "5.23", "media-completion-recovery" to "5.24",
            "media-download-cancellation" to "5.25", "media-restart-recovery" to "5.26")
        expected.forEach { (id, section) ->
            val questions = qaQuestionCatalog()
            val question = questions.single { it.id == id }
            assertEquals(section, question.sopSection)
            assertTrue(question.evidenceHint.isNotBlank())
            val draft = report(questions.map { QaQuestionResult(it, if (it.id == id) QaAnswer.Pending else QaAnswer.Passed) })
            assertEquals(QaAnswer.Pending, draft.overallAnswer())
            assertTrue("SOP §$section" in draft.toQaHtml("QA SOP"))
            assertTrue(question.evidenceHint in draft.toPlainText())
            assertTrue("SOP §$section" in draft.toPlainText())
        }
    }

    @Test fun sopAndEvidenceHintsSurviveHtmlAndPdfTextExportWithoutHtmlInjection() {
        val question = QaQuestion("media", "Media", "Review", true, "5.17 <script>", "Attach <evidence> & owner")
        val draft = report(listOf(QaQuestionResult(question)))
        val html = draft.toQaHtml("QA SOP")
        assertTrue("SOP §5.17 &lt;script&gt;" in html)
        assertTrue("Attach &lt;evidence&gt; &amp; owner" in html)
        assertFalse("<script>" in html)
        assertTrue("Product sign-off" in html)
        val plain = draft.toPlainText()
        assertTrue("SOP §5.17 <script>" in plain)
        assertTrue("Evidence: Attach <evidence> & owner" in plain)
    }

    @Test fun automatedPassCannotOverridePendingRealStorageOrProductMediaAcceptance() {
        val results = qaQuestionCatalog().map { question ->
            QaQuestionResult(question, if (question.id == "media-assets" || question.id == "media-visual") QaAnswer.Pending else QaAnswer.Passed)
        }
        val draft = report(results).copy(automatedEvidence = "All builds and native fixtures pass")
        assertEquals(QaAnswer.Pending, draft.overallAnswer())
        assertTrue("pending" in draft.toQaHtml("QA SOP"))
    }
    @Test fun giphyQuestionsStayPendingAndPreserveSopEvidenceNotesInBothExports() {
        val questions = qaQuestionCatalog()
        val ids = setOf("media-giphy-browser", "media-giphy-canvas", "media-giphy-product")
        val giphy = questions.filter { it.id in ids }
        assertEquals(3, giphy.size)
        assertTrue(giphy.all { it.sopSection.startsWith("5.30") && it.evidenceHint.isNotBlank() })
        assertTrue(giphy.single { it.id == "media-giphy-product" }.requiresProductSignoff)
        assertFalse(giphy.single { it.id == "media-giphy-canvas" }.requiresProductSignoff)
        val draft = report(questions.map { question ->
            QaQuestionResult(question, if (question.id in ids) QaAnswer.Pending else QaAnswer.Passed,
                if (question.id in ids) "Review <masked> & capture" else "")
        }).copy(automatedEvidence = "All mock tests and resource hashes pass")
        assertEquals(QaAnswer.Pending, draft.overallAnswer())
        val html = draft.toQaHtml("QA SOP")
        val plain = draft.toPlainText()
        giphy.forEach { question ->
            assertTrue(question.prompt in html)
            assertTrue(question.evidenceHint in plain)
            assertTrue("SOP §${question.sopSection}" in plain)
        }
        assertTrue("Review &lt;masked&gt; &amp; capture" in html)
        assertTrue("Review <masked> & capture" in plain)
        assertEquals("performance", questions.last().id)
    }
    @Test
    fun failedQuestionControlsOverallAnswer() {
        val draft = report(
            listOf(
                result("one", QaAnswer.Passed),
                result("two", QaAnswer.Failed),
            ),
        )

        assertEquals(QaAnswer.Failed, draft.overallAnswer())
    }

    @Test
    fun pendingQuestionPreventsPrematurePass() {
        val draft = report(listOf(result("one", QaAnswer.Passed), result("two", QaAnswer.Pending)))

        assertEquals(QaAnswer.Pending, draft.overallAnswer())
    }

    @Test
    fun htmlEscapesFormContent() {
        val html = report(listOf(result("<script>alert(1)</script>", QaAnswer.Passed))).copy(
            title = "QA <unsafe>",
            generalNotes = "A & B",
        ).toQaHtml("docs/QA.md")

        assertTrue("QA &lt;unsafe&gt;" in html)
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;" in html)
        assertTrue("A &amp; B" in html)
        assertFalse("<script>alert(1)</script>" in html)
    }

    private fun result(prompt: String, answer: QaAnswer) = QaQuestionResult(
        question = QaQuestion(id = prompt, category = "Functional", prompt = prompt),
        answer = answer,
    )

    private fun report(results: List<QaQuestionResult>) = QaReportDraft(
        title = "QA",
        version = "v1",
        platform = "test",
        deviceAndOrientation = "test",
        tester = "tester",
        date = "2026-09-30",
        scope = "scope",
        exclusions = "none",
        automatedEvidence = "tests pass",
        generalNotes = "",
        questions = results,
        screenshots = emptyList(),
    )
}
