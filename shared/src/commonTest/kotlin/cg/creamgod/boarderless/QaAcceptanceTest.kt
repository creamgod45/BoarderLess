package cg.creamgod.boarderless

import cg.creamgod.boarderless.feature.qa.QaAnswer
import cg.creamgod.boarderless.feature.qa.QaQuestion
import cg.creamgod.boarderless.feature.qa.QaQuestionResult
import cg.creamgod.boarderless.feature.qa.QaReportDraft
import cg.creamgod.boarderless.feature.qa.overallAnswer
import cg.creamgod.boarderless.feature.qa.toQaHtml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QaAcceptanceTest {
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

