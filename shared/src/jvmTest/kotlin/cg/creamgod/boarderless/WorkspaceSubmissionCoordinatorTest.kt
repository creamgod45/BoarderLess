package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.SubmitOutcome
import cg.creamgod.boarderless.data.WorkspaceRepository
import cg.creamgod.boarderless.data.WorkspaceActivity
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceSubmissionQueue
import cg.creamgod.boarderless.data.WorkspaceSubmissionStep
import cg.creamgod.boarderless.data.WorkspaceSummary
import cg.creamgod.boarderless.data.WorkspaceMember
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.submitNextWorkspaceChange
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceSubmissionCoordinatorTest {
    private val workspaceId = WorkspaceId("workspace-1")
    private val initial = Workspace(workspaceId, "Coordinator test")
    private val initialSession = session(initial, version = 0)

    @Test
    fun dependentOperationsAdvanceServerBaseVersionInOrder() = runBlocking {
        val nodeId = CanvasObjectId("node-1")
        val create = CreateObjectsOperation(
            "create",
            listOf(TextNode(id = nodeId, transform = transform(), text = "Draft")),
        )
        val afterCreate = assertIs<OperationResult.Applied>(create.applyTo(initial)).workspace
        val edit = EditTextOperation(
            "edit",
            listOf(TextChange(nodeId, 1, "Draft", "Edited while create was saving")),
        )
        val afterEdit = assertIs<OperationResult.Applied>(edit.applyTo(afterCreate)).workspace
        var queue = WorkspaceSubmissionQueue()
            .enqueue(create, afterCreate)
            .enqueue(edit, afterEdit)
        val submittedBaseVersions = mutableListOf<Long>()
        val repository = FakeRepository(
            onSubmit = { submittedSession, _ ->
                submittedBaseVersions += submittedSession.workspaceVersion
                SubmitOutcome.Accepted(
                    workspaceVersion = submittedSession.workspaceVersion + 1,
                    lastServerSeq = submittedSession.lastServerSeq + 1,
                )
            },
        )

        val first = assertIs<WorkspaceSubmissionStep.Accepted>(
            submitNextWorkspaceChange(repository, initialSession, queue),
        )
        queue = queue.accept(first.acceptedToken)
        val second = assertIs<WorkspaceSubmissionStep.Accepted>(
            submitNextWorkspaceChange(repository, first.session, queue),
        )

        assertEquals(listOf(0L, 1L), submittedBaseVersions)
        assertTrue(queue.accept(second.acceptedToken).isEmpty)
        assertEquals(2L, second.session.workspaceVersion)
        assertEquals(
            "Edited while create was saving",
            assertIs<TextNode>(second.session.workspace.objectById(nodeId)).text,
        )
    }

    @Test
    fun atomicTransactionKeepsOptimisticAndServerWorkspaceVersionsAligned() = runBlocking {
        val first = TextNode(
            id = CanvasObjectId("node-1"),
            transform = transform(),
            text = "First",
        )
        val second = TextNode(
            id = CanvasObjectId("node-2"),
            transform = transform(),
            text = "Second",
        )
        val transaction = TransactionOperation(
            operationId = "pair",
            operations = listOf(
                CreateObjectsOperation("first", listOf(first)),
                CreateObjectsOperation("second", listOf(second)),
            ),
        )
        val optimistic = assertIs<OperationResult.Applied>(transaction.applyTo(initial)).workspace
        val repository = FakeRepository(
            onSubmit = { submittedSession, _ ->
                SubmitOutcome.Accepted(
                    workspaceVersion = submittedSession.workspaceVersion + 1,
                    lastServerSeq = submittedSession.lastServerSeq + 2,
                )
            },
        )

        val accepted = assertIs<WorkspaceSubmissionStep.Accepted>(
            submitNextWorkspaceChange(
                repository = repository,
                session = initialSession,
                queue = WorkspaceSubmissionQueue().enqueue(transaction, optimistic),
            ),
        )

        assertEquals(1L, optimistic.version)
        assertEquals(accepted.session.workspaceVersion, accepted.session.workspace.version)
        assertEquals(2, accepted.session.workspace.objects.size)
        assertEquals(2L, accepted.session.lastServerSeq)
    }

    @Test
    fun acceptedResponseRemovesOnlyItsTokenFromLatestQueue() = runBlocking {
        val firstOperation = createOperation("first")
        val afterFirst = assertIs<OperationResult.Applied>(firstOperation.applyTo(initial)).workspace
        val queueAtRequestStart = WorkspaceSubmissionQueue().enqueue(firstOperation, afterFirst)
        val repository = FakeRepository(
            onSubmit = { submittedSession, _ ->
                SubmitOutcome.Accepted(
                    workspaceVersion = submittedSession.workspaceVersion + 1,
                    lastServerSeq = submittedSession.lastServerSeq + 1,
                )
            },
        )
        val accepted = assertIs<WorkspaceSubmissionStep.Accepted>(
            submitNextWorkspaceChange(repository, initialSession, queueAtRequestStart),
        )
        val laterOperation = createOperation("later", "node-2")
        val afterLater = assertIs<OperationResult.Applied>(laterOperation.applyTo(afterFirst)).workspace
        val latestQueue = queueAtRequestStart.enqueue(laterOperation, afterLater)

        val remaining = latestQueue.accept(accepted.acceptedToken)

        assertEquals(1, remaining.size)
        assertEquals("later", remaining.first?.operation?.operationId)
    }

    @Test
    fun conflictReturnsAuthoritativeSessionAndDiscardsWholeQueue() = runBlocking {
        val operation = createOperation("local")
        val optimistic = assertIs<OperationResult.Applied>(operation.applyTo(initial)).workspace
        val queue = WorkspaceSubmissionQueue().enqueue(operation, optimistic).enqueue(
            createOperation("later", "node-2"),
            optimistic,
        )
        val authoritative = session(initial.copy(title = "Remote title"), version = 4)
        val repository = FakeRepository(onSubmit = { _, _ -> SubmitOutcome.Conflict(authoritative) })

        val result = assertIs<WorkspaceSubmissionStep.Conflict>(
            submitNextWorkspaceChange(repository, initialSession, queue),
        )

        assertEquals(authoritative, result.session)
        assertEquals(2, result.discardedCount)
    }

    @Test
    fun lostSubmitResponseReconcilesFromServer() = runBlocking {
        val operation = createOperation("local")
        val committed = assertIs<OperationResult.Applied>(operation.applyTo(initial)).workspace
        val queue = WorkspaceSubmissionQueue().enqueue(operation, committed)
        val recovered = session(committed, version = 1)
        val repository = FakeRepository(
            onSubmit = { _, _ -> error("response lost") },
            onRefresh = { recovered },
        )

        val result = assertIs<WorkspaceSubmissionStep.Recovered>(
            submitNextWorkspaceChange(repository, initialSession, queue),
        )

        assertEquals(recovered, result.session)
        assertEquals(1, result.discardedCount)
    }

    @Test
    fun submitAndRefreshFailureKeepsLastAuthoritativeSession() = runBlocking {
        val operation = createOperation("local")
        val optimistic = assertIs<OperationResult.Applied>(operation.applyTo(initial)).workspace
        val repository = FakeRepository(
            onSubmit = { _, _ -> error("offline") },
            onRefresh = { error("still offline") },
        )

        val result = assertIs<WorkspaceSubmissionStep.Failed>(
            submitNextWorkspaceChange(
                repository,
                initialSession,
                WorkspaceSubmissionQueue().enqueue(operation, optimistic),
            ),
        )

        assertEquals(initialSession, result.session)
        assertEquals(1, result.discardedCount)
    }

    private fun createOperation(text: String, id: String = "node-1") = CreateObjectsOperation(
        text,
        listOf(TextNode(id = CanvasObjectId(id), transform = transform(), text = text)),
    )

    private fun transform() = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f))

    private fun session(workspace: Workspace, version: Long) = WorkspaceSession(
        userId = "user-1",
        clientId = "client-1",
        role = WorkspaceMemberRole.Owner,
        workspaceVersion = version,
        lastServerSeq = version,
        workspace = workspace,
    )

    private class FakeRepository(
        private val onSubmit: suspend (WorkspaceSession, WorkspaceOperation) -> SubmitOutcome,
        private val onRefresh: suspend (WorkspaceSession) -> WorkspaceSession = { it },
    ) : WorkspaceRepository {
        override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?): WorkspaceSession = error("Not used")

        override suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary> = error("Not used")

        override suspend fun createWorkspace(session: WorkspaceSession, title: String): WorkspaceSession =
            error("Not used")

        override suspend fun openWorkspace(
            session: WorkspaceSession,
            workspaceId: WorkspaceId,
        ): WorkspaceSession = error("Not used")

        override suspend fun renameWorkspace(
            session: WorkspaceSession,
            workspaceId: WorkspaceId,
            title: String,
        ): WorkspaceSummary = error("Not used")

        override suspend fun deleteWorkspace(session: WorkspaceSession, workspaceId: WorkspaceId): Unit =
            error("Not used")

        override suspend fun listRecentActivity(
            session: WorkspaceSession,
            limit: Int,
        ): List<WorkspaceActivity> = error("Not used")

        override suspend fun listWorkspaceMembers(session: WorkspaceSession): List<WorkspaceMember> =
            error("Not used")

        override suspend fun setWorkspaceMemberRole(
            session: WorkspaceSession,
            userId: String,
            role: WorkspaceMemberRole,
        ): Unit = error("Not used")

        override suspend fun removeWorkspaceMember(session: WorkspaceSession, userId: String): Unit =
            error("Not used")

        override suspend fun refresh(session: WorkspaceSession): WorkspaceSession = onRefresh(session)

        override suspend fun submit(
            session: WorkspaceSession,
            operation: WorkspaceOperation,
        ): SubmitOutcome = onSubmit(session, operation)

        override fun close() = Unit
    }
}
