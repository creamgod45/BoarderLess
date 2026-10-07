package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendHistoryRestorationTest {
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(20f, 20f)), text = "A")
    private val b = a.copy(id = CanvasObjectId("b"), text = "B")
    private val relation = Relation(RelationId("r"), sourceObjectId = a.id, targetObjectId = b.id)
    private val workspace =
        Workspace(
            WorkspaceId("workspace"),
            "QA",
            objects = listOf(a, b).associateBy { it.id },
            relations = mapOf(relation.id to relation),
        )
    private val deletion = DeleteObjectsOperation("delete", listOf(a), listOf(relation))

    @Test fun historyCyclesAdvanceObjectAndCascadeVersionsAndSendIdsOnly() {
        var history = WorkspaceHistory(workspace).execute(deletion).history
        val undo = history.undo(remoteRestoration = true)
        val wire = checkNotNull(undo.appliedOperation).toExpandedDtos(20)
        assertEquals(listOf("restore_objects", "restore_relations"), wire.map { it.kind })
        assertEquals(mapOf("a" to 2L), wire[0].expectedObjectVersions)
        assertEquals(mapOf("r" to 2L), wire[1].expectedObjectVersions)
        assertEquals(setOf("objectIds"), wire[0].payload.keys)
        assertEquals(
            3L,
            undo.history.workspace.objects
                .getValue(a.id)
                .version,
        )
        history = undo.history.redo(remoteRestoration = true).history
        val next = history.undo(remoteRestoration = true)
        assertEquals(
            5L,
            next.history.workspace.objects
                .getValue(a.id)
                .version,
        )
        assertEquals(
            5L,
            next.history.workspace.relations
                .getValue(relation.id)
                .version,
        )
        assertEquals(mapOf("a" to 4L), checkNotNull(next.appliedOperation).toExpandedDtos(0)[0].expectedObjectVersions)
    }

    @Test fun newlyCreatedRedoUsesRestoreNotCreate() {
        val created =
            WorkspaceHistory(workspace.copy(objects = emptyMap(), relations = emptyMap()))
                .execute(CreateObjectsOperation("create", listOf(a)))
                .history
        val removed = created.undo(remoteRestoration = true).history
        val redone = removed.redo(remoteRestoration = true)
        assertEquals("restore_objects", checkNotNull(redone.appliedOperation).toExpandedDtos(0).single().kind)
        assertEquals(
            3L,
            redone.history.workspace.objects
                .getValue(a.id)
                .version,
        )
    }

    @Test fun groupsAndChildrenRestoreInOneBatchBeforeRelations() {
        val group = GroupFrame(CanvasObjectId("group"), transform = a.transform, title = "Group")
        val child = a.copy(parentId = group.id)
        val before = workspace.copy(objects = listOf(group, child, b).associateBy { it.id })
        val undo =
            WorkspaceHistory(before)
                .execute(DeleteObjectsOperation("delete", listOf(child, group), listOf(relation)))
                .history
                .undo(remoteRestoration = true)
        assertTrue(undo.succeeded)
        val wire = checkNotNull(undo.appliedOperation).toExpandedDtos(0)
        assertEquals(2, wire.size)
        assertEquals(setOf("a", "group"), wire[0].expectedObjectVersions!!.keys)
    }

    @Test fun repositorySendsRestoreAfterPersistedDeleteAcrossRepositoryRecreation() =
        runTest {
            val settings = InMemorySettings()
            val preferences = SessionPreferences(settings)
            var session = WorkspaceSession("actor", preferences.clientId, WorkspaceMemberRole.Owner, 0, 0, workspace)
            val sent = mutableListOf<SubmitOperationsRequest>()

            fun client() =
                HttpClient(
                    MockEngine { request ->
                        val body = Json.decodeFromString<SubmitOperationsRequest>((request.body as TextContent).text)
                        sent += body
                        var seq = session.lastServerSeq
                        val records =
                            body.operations.map { operation ->
                                val payload =
                                    buildJsonObject {
                                        operation.payload.forEach { (key, value) -> put(key, value) }
                                        if (operation.kind ==
                                            "delete_objects"
                                        ) {
                                            put("cascadedRelationIds", JsonArray(listOf(JsonPrimitive("r"))))
                                        }
                                        if (operation.kind.startsWith("restore_")) {
                                            put(
                                                "restoredVersions",
                                                buildJsonObject {
                                                    operation.expectedObjectVersions!!.forEach { (id, version) ->
                                                        put(
                                                            id,
                                                            JsonPrimitive(version + 1),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                CommittedWorkspaceOperationDto(
                                    ++seq,
                                    operation.operationId,
                                    body.transactionId,
                                    "actor",
                                    body.clientId,
                                    operation.clientSeq,
                                    body.baseVersion,
                                    body.baseVersion + 1,
                                    operation.kind,
                                    payload,
                                    1,
                                    "2026-10-05",
                                )
                            }
                        respond(
                            Json.encodeToString(
                                AcceptedOperationsDto(
                                    "accepted",
                                    body.baseVersion + 1,
                                    records.first().serverSeq,
                                    records.last().serverSeq,
                                    records,
                                ),
                            ),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            var history = WorkspaceHistory(workspace).execute(deletion).history
            val first = BackendWorkspaceRepository("https://qa.invalid", preferences, client())
            val accepted = assertIs<SubmitOutcome.Accepted>(first.submit(session, deletion))
            first.close()
            session =
                session.copy(
                    workspace = history.workspace,
                    workspaceVersion = accepted.workspaceVersion,
                    lastServerSeq = accepted.lastServerSeq,
                )
            val restoredPreferences = SessionPreferences(settings)
            val second = BackendWorkspaceRepository("https://qa.invalid", restoredPreferences, client())
            try {
                val undo = history.undo(remoteRestoration = true)
                val transaction = assertIs<TransactionOperation>(undo.appliedOperation)
                val objectRestore = assertIs<CreateObjectsOperation>(transaction.operations.first())
                val tampered =
                    transaction.copy(
                        operations =
                            listOf(
                                objectRestore.copy(
                                    objects =
                                        listOf(assertIs<TextNode>(objectRestore.objects.single()).copy(text = "Not the deleted snapshot")),
                                ),
                                transaction.operations.last(),
                            ),
                    )
                val sequenceBeforeRejection = restoredPreferences.clientSequence
                assertFailsWith<IllegalStateException> { second.submit(session, tampered) }
                assertFailsWith<IllegalStateException> { second.submit(session.copy(userId = "other-actor"), transaction) }
                assertEquals(sequenceBeforeRejection, restoredPreferences.clientSequence)
                assertEquals(1, sent.size)
                assertIs<SubmitOutcome.Accepted>(second.submit(session, checkNotNull(undo.appliedOperation)))
                assertEquals(listOf("restore_objects", "restore_relations"), sent.last().operations.map { it.kind })
                assertTrue(
                    sent
                        .flatMap { it.operations }
                        .map { it.operationId }
                        .distinct()
                        .size == 3,
                )
            } finally {
                second.close()
            }
        }

    @Test fun missingOrWrongScopeEvidenceFailsBeforeNetworkAndSequenceReservation() =
        runTest {
            val preferences = SessionPreferences(InMemorySettings())
            val session =
                WorkspaceSession(
                    "actor",
                    preferences.clientId,
                    WorkspaceMemberRole.Owner,
                    1,
                    1,
                    WorkspaceHistory(workspace).execute(deletion).history.workspace,
                )
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        error("Must not send unproven restore")
                    },
                )
            val repository = BackendWorkspaceRepository("https://qa.invalid", preferences, client)
            val undo = WorkspaceHistory(workspace).execute(deletion).history.undo(remoteRestoration = true)
            try {
                assertFailsWith<IllegalStateException> { repository.submit(session, checkNotNull(undo.appliedOperation)) }
                assertEquals(0, calls)
                assertEquals(0L, preferences.clientSequence)
            } finally {
                repository.close()
            }
        }
}
