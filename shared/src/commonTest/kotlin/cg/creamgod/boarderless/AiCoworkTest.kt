package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.ai.AiContextScope
import cg.creamgod.boarderless.domain.ai.AiContextSnapshot
import cg.creamgod.boarderless.domain.ai.AiProposal
import cg.creamgod.boarderless.domain.ai.AiProposalConflict
import cg.creamgod.boarderless.domain.ai.AiProposalItem
import cg.creamgod.boarderless.domain.ai.AiProposalPreview
import cg.creamgod.boarderless.domain.ai.AiProposalStatus
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AiCoworkTest {
    private val firstId = CanvasObjectId("first")
    private val secondId = CanvasObjectId("second")
    private val first = TextNode(
        id = firstId,
        transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
        text = "Raw idea",
    )
    private val second = TextNode(
        id = secondId,
        transform = CanvasTransform(Vec2(300f, 0f), CanvasSize(240f, 120f)),
        text = "Private unselected idea",
    )
    private val workspace = Workspace(
        id = WorkspaceId("workspace"),
        title = "Test",
        version = 1,
        objects = mapOf(firstId to first, secondId to second),
    )

    @Test
    fun contextIncludesOnlyExplicitSelection() {
        val context = AiContextSnapshot.fromSelection(workspace, setOf(firstId))

        assertEquals(AiContextScope.Selection, context.scope)
        assertEquals(listOf("first"), context.objects.map { it.objectId })
        assertTrue(context.objects.none { it.content.contains("Private") })
    }

    @Test
    fun emptySelectionCreatesPromptOnlyContext() {
        val context = AiContextSnapshot.fromSelection(workspace, emptySet())

        assertEquals(AiContextScope.PromptOnly, context.scope)
        assertTrue(context.objects.isEmpty())
        assertTrue(context.relations.isEmpty())
    }

    @Test
    fun previewDoesNotMutateFormalWorkspaceAndAcceptedTransactionCanUndo() {
        val proposal = proposalFor(first, "Organized idea")

        val preview = assertIs<AiProposalPreview.Ready>(proposal.preview(workspace))
        assertNotSame(workspace, preview.workspace)
        assertEquals("Raw idea", (workspace.objectById(firstId) as TextNode).text)
        assertEquals("Organized idea", (preview.workspace.objectById(firstId) as TextNode).text)

        var history = WorkspaceHistory(workspace).execute(preview.operation).history
        assertEquals("Organized idea", (history.workspace.objectById(firstId) as TextNode).text)
        history = history.undo().history
        assertEquals("Raw idea", (history.workspace.objectById(firstId) as TextNode).text)
    }

    @Test
    fun proposalDetectsWorkspaceVersionDriftBeforePreview() {
        val proposal = proposalFor(first, "Organized idea")
        val newerWorkspace = workspace.copy(version = 2)

        val preview = assertIs<AiProposalPreview.Conflict>(proposal.preview(newerWorkspace))
        assertIs<AiProposalConflict.WorkspaceVersion>(preview.error)
    }

    @Test
    fun partialAcceptanceBuildsOneTransactionAndRejectDoesNotApplyAnything() {
        val create = TextNode(
            id = CanvasObjectId("new"),
            transform = CanvasTransform(Vec2(0f, 200f), CanvasSize(240f, 120f)),
            text = "Suggested next step",
        )
        val proposal = AiProposal(
            proposalId = "proposal",
            contextWorkspaceVersion = workspace.version,
            items = listOf(
                proposalFor(first, "Organized idea").items.single(),
                AiProposalItem(
                    itemId = "create",
                    summary = "Add a next step",
                    rationale = "Makes the idea actionable",
                    operation = CreateObjectsOperation("ai-create", listOf(create)),
                    included = false,
                ),
            ),
        )

        val operation = proposal.selectedOperation()
        assertEquals(1, operation?.operations?.size)
        assertEquals(AiProposalStatus.PartiallyAccepted, proposal.markCommitted().status)
        assertEquals(AiProposalStatus.Rejected, proposal.reject().status)
        assertEquals("Raw idea", (workspace.objectById(firstId) as TextNode).text)
        assertTrue(workspace.objectById(create.id) == null)
    }

    private fun proposalFor(node: TextNode, nextText: String): AiProposal = AiProposal(
        proposalId = "rewrite",
        contextWorkspaceVersion = workspace.version,
        items = listOf(
            AiProposalItem(
                itemId = "rewrite-first",
                summary = "Clarify the selected thought",
                rationale = "Improves readability",
                operation = EditTextOperation(
                    operationId = "ai-edit",
                    changes = listOf(
                        TextChange(
                            objectId = node.id,
                            expectedVersion = node.version,
                            before = node.text,
                            after = nextText,
                        ),
                    ),
                ),
            ),
        ),
    )
}
