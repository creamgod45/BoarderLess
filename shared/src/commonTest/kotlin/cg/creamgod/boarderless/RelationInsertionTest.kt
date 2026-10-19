package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class RelationInsertionTest {
    private val a = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f)), text = "A")
    private val b = a.copy(id = CanvasObjectId("b"), transform = a.transform.copy(position = Vec2(600f, 0f)), text = "B")
    private val edge =
        Relation(
            RelationId("edge"),
            version = 3,
            sourceObjectId = a.id,
            targetObjectId = b.id,
            label = "condition",
            intent = "supports",
            colorToken = "custom",
        )
    private val workspace = Workspace(WorkspaceId("w"), "Canvas", 7, mapOf(a.id to a, b.id to b), mapOf(edge.id to edge))

    @Test fun allDirectionsPreserveActualFlowAndOnlyFirstSegmentKeepsCondition() {
        for (direction in RelationDirection.entries) {
            val baseline = workspace.copy(relations = mapOf(edge.id to edge.copy(direction = direction)))
            val plan = RelationInsertionDraft(baseline, edge.id).plan("Step")
            val backward = direction == RelationDirection.Backward
            assertEquals(if (backward) b.id else a.id, plan.first.sourceObjectId)
            assertEquals(if (backward) a.id else b.id, plan.second.targetObjectId)
            assertEquals(plan.node.id, plan.first.targetObjectId)
            assertEquals(plan.node.id, plan.second.sourceObjectId)
            assertEquals(if (backward) RelationDirection.Forward else direction, plan.first.direction)
            assertEquals(plan.first.direction, plan.second.direction)
            assertEquals(edge.label, plan.first.label)
            assertEquals(edge.intent, plan.first.intent)
            assertNull(plan.second.label)
            assertNull(plan.second.intent)
            assertEquals(edge.colorToken, plan.first.colorToken)
            assertEquals(edge.colorToken, plan.second.colorToken)
            assertFalse(edge.id in plan.preview.relations)
            assertEquals(8, plan.preview.version)
            assertEquals(3, plan.preview.objects.size)
            assertEquals(2, plan.preview.relations.size)
            assertEquals(2, workspace.objects.size)
            assertEquals(1, workspace.relations.size)
        }
    }

    @Test fun editsKeepIdentityAndCustomGeometryButSnapshotChangesFailCurrentGate() {
        val draft = RelationInsertionDraft(workspace, edge.id)
        val first = draft.plan("First")
        val second = draft.plan("Edited", "Label", "relates", Vec2(200f, 300f), CanvasSize(200f, 80f))
        assertEquals(first.node.id, second.node.id)
        assertEquals(first.first.id, second.first.id)
        assertEquals(first.second.id, second.second.id)
        assertEquals(first.operation.operationId, second.operation.operationId)
        assertEquals(first.operation.operations.map { it.operationId }, second.operation.operations.map { it.operationId })
        assertEquals(Vec2(200f, 300f), second.node.transform.position)
        assertTrue(draft.isCurrent(workspace))
        assertFalse(draft.isCurrent(workspace.copy(version = 8)))
        assertFalse(draft.isCurrent(workspace.copy(relations = mapOf(edge.id to edge.copy(label = "remote")))))
        assertFalse(draft.isCurrent(workspace.copy(title = "renamed")))
    }

    @Test fun groupParentRetainedOnlyForSharedParentAndLocksCyclesMissingEndpointsReject() {
        val group = GroupFrame(CanvasObjectId("g"), transform = CanvasTransform(Vec2.Zero, CanvasSize(900f, 300f)), title = "Group")
        val grouped =
            workspace.copy(
                objects = mapOf(group.id to group, a.id to a.copy(parentId = group.id), b.id to b.copy(parentId = group.id)),
            )
        assertEquals(group.id, RelationInsertionDraft(grouped, edge.id).plan("Step").node.parentId)
        assertNull(RelationInsertionDraft(grouped.copy(objects = grouped.objects + (b.id to b)), edge.id).plan("Step").node.parentId)
        for (baseline in listOf(
            workspace.copy(objects = workspace.objects + (a.id to a.copy(locked = true))),
            grouped.copy(objects = grouped.objects + (group.id to group.copy(locked = true))),
            grouped.copy(objects = grouped.objects + (group.id to group.copy(parentId = group.id))),
            workspace.copy(objects = mapOf(a.id to a)),
            workspace.copy(relations = mapOf(edge.id to edge.copy(targetObjectId = a.id))),
        )) {
            assertFails { RelationInsertionDraft(baseline, edge.id) }
        }
    }

    @Test fun transactionRejectsChangedOriginalAtomicallyAndRemoteUndoRedoRestoresProvenance() {
        val plan = RelationInsertionDraft(workspace, edge.id).plan("Step")
        val changed = workspace.copy(relations = mapOf(edge.id to edge.copy(version = 4)))
        assertIs<OperationResult.Rejected>(plan.operation.applyTo(changed))
        assertFalse(plan.node.id in changed.objects)
        val applied = WorkspaceHistory(workspace).execute(plan.operation)
        assertTrue(applied.succeeded)
        val undo = applied.history.undo(remoteRestoration = true)
        assertTrue(undo.succeeded)
        assertEquals(setOf(a.id, b.id), undo.history.workspace.objects.keys)
        assertEquals(
            edge.copy(version = 5),
            undo.history.workspace.relations
                .getValue(edge.id),
        )
        val undoneWire = checkNotNull(undo.appliedOperation).toExpandedDtos(20)
        assertEquals(listOf("delete_relations", "restore_relations", "delete_objects"), undoneWire.map { it.kind })
        assertEquals(mapOf(edge.id.value to 4L), undoneWire[1].expectedObjectVersions)
        val redo = undo.history.redo(remoteRestoration = true)
        assertTrue(redo.succeeded)
        val redoneWire = checkNotNull(redo.appliedOperation).toExpandedDtos(30)
        assertEquals(listOf("restore_objects", "delete_relations", "restore_relations"), redoneWire.map { it.kind })
        assertEquals(5L, redoneWire[1].expectedObjectVersions!!.getValue(edge.id.value))
        assertEquals(setOf(plan.first.id, plan.second.id), redo.history.workspace.relations.keys)
    }

    @Test fun finiteGeometryUtf8AndSafeVersionBoundsRejectInvalidPlans() {
        val draft = RelationInsertionDraft(workspace, edge.id)
        for (text in listOf("", "  ", "中".repeat(22000))) assertFails { draft.plan(text) }
        assertFails { draft.plan("Step", position = Vec2(1_000_001f, 0f)) }
        assertFails { draft.plan("Step", size = CanvasSize(0f, 60f)) }
        assertFails { draft.plan("Step", firstIntent = "x".repeat(4097)) }
        assertFails { RelationInsertionDraft(workspace.copy(version = Long.MAX_VALUE), edge.id) }
        assertFails {
            RelationInsertionDraft(
                workspace.copy(objects = workspace.objects + (a.id to a.copy(zIndex = Long.MAX_VALUE))),
                edge.id,
            )
        }
    }

    @Test fun insertionPointFollowsVisibleDetourAroundAnObstacle() {
        val blocker =
            a.copy(
                id = CanvasObjectId("blocker"),
                transform = CanvasTransform(Vec2(250f, -100f), CanvasSize(200f, 300f)),
                text = "Obstacle",
            )
        val draft = RelationInsertionDraft(workspace.copy(objects = workspace.objects + (blocker.id to blocker)), edge.id)
        assertTrue(draft.center.y < -100f || draft.center.y > 200f)
        assertEquals(30f, RelationInsertionDraft(workspace, edge.id).center.y)
    }

    @Test fun callerMutableMapsAreCopiedBeforeAnyPreviewEdits() {
        val objects = workspace.objects.toMutableMap()
        val relations = workspace.relations.toMutableMap()
        val draft = RelationInsertionDraft(workspace.copy(objects = objects, relations = relations), edge.id)
        objects.clear()
        relations.clear()
        assertEquals(
            3,
            draft
                .plan("Step")
                .preview.objects.size,
        )
        assertEquals(
            2,
            draft
                .plan("Step")
                .preview.relations.size,
        )
    }

    @Test fun realRepositoryStagesOneTransactionAndAcknowledgesFourExactWireOperations() =
        runTest {
            val settings = InMemorySettings()
            val preferences = SessionPreferences(settings)
            preferences.userId = "actor"
            val session = WorkspaceSession("actor", preferences.clientId, WorkspaceMemberRole.Owner, 7, 10, workspace)
            val plan = RelationInsertionDraft(workspace, edge.id).plan("Step")
            val sent = mutableListOf<SubmitOperationsRequest>()
            val http =
                HttpClient(
                    MockEngine { request ->
                        val body = Json.decodeFromString<SubmitOperationsRequest>((request.body as TextContent).text)
                        sent += body
                        val records =
                            body.operations.mapIndexed { i, op ->
                                CommittedWorkspaceOperationDto(
                                    11L + i,
                                    op.operationId,
                                    body.transactionId,
                                    "actor",
                                    body.clientId,
                                    op.clientSeq,
                                    body.baseVersion,
                                    body.baseVersion + 1,
                                    op.kind,
                                    op.payload,
                                    1,
                                    "2026-10-06",
                                )
                            }
                        respond(
                            Json.encodeToString(AcceptedOperationsDto("accepted", 8, 11, 14, records)),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val repository = BackendWorkspaceRepository("https://fixture.invalid", preferences, http)
            try {
                repository.retainDraft(session, workspace, plan.operation)
                val accepted = assertIs<SubmitOutcome.Accepted>(repository.submit(session, plan.operation))
                assertEquals(8, accepted.workspaceVersion)
                assertEquals(14, accepted.lastServerSeq)
                assertEquals(1, sent.size)
                assertEquals(
                    listOf("create_object", "delete_relations", "create_relation", "create_relation"),
                    sent.single().operations.map { it.kind },
                )
                assertEquals(mapOf(edge.id.value to 3L), sent.single().operations[1].expectedObjectVersions)
                assertEquals(
                    plan.node.id.value,
                    sent
                        .single()
                        .operations[2]
                        .payload
                        .getValue("targetObjectId")
                        .jsonPrimitive.content,
                )
                assertFalse("intent" in sent.single().operations[3].payload)
                assertFalse("label" in sent.single().operations[3].payload)
                assertNull(repository.pendingChange(session))
                assertNull(repository.pendingDraft(session))
            } finally {
                repository.close()
            }
        }
}
