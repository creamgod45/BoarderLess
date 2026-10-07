package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class RelationGeometryIntegrationTest {
    private val a =
        TextNode(
            CanvasObjectId("a"),
            transform = CanvasTransform(Vec2(40f, 60f), CanvasSize(100f, 80f), 30f),
            text = "A",
            shape = NodeShape.Diamond,
        )
    private val b = a.copy(id = CanvasObjectId("b"), transform = a.transform.copy(position = Vec2(440f, 60f)), text = "B")
    private val base = Workspace(WorkspaceId("board"), "Geometry", objects = mapOf(a.id to a, b.id to b))
    private val loop =
        Relation(
            RelationId("loop"),
            sourceObjectId = a.id,
            targetObjectId = a.id,
            label = "自循環🙂",
            geometry = RelationGeometry(route = RelationRoute.Loop()),
        )

    private fun attrs(edge: Relation) = RelationAttributes(edge.direction, edge.intent, edge.label, edge.colorToken, edge.geometry)

    @Test fun loopCreateDeleteCascadeAndHistoryRestoreKeepGeometryAndOneRelationIdentity() {
        assertTrue(CreateRelationsOperation("legacy", listOf(loop.copy(geometry = null))).applyTo(base) is OperationResult.Rejected)
        val created = WorkspaceHistory(base).execute(CreateRelationsOperation("create", listOf(loop))).history
        assertEquals(loop, created.workspace.relations[loop.id])
        val undone = created.undo(remoteRestoration = true)
        assertTrue(undone.succeeded)
        assertTrue(
            undone.history.workspace.relations
                .isEmpty(),
        )
        val redone = undone.history.redo(remoteRestoration = true)
        assertTrue(redone.succeeded)
        val restored = assertNotNull(redone.history.workspace.relations[loop.id])
        assertEquals(3L, restored.version)
        assertEquals(loop.geometry, restored.geometry)
        val deleted = redone.history.execute(DeleteObjectsOperation("delete-node", listOf(a), listOf(restored)))
        assertTrue(deleted.succeeded)
        assertTrue(
            deleted.history.workspace.relations
                .isEmpty(),
        )
        val restoredNode = deleted.history.undo(remoteRestoration = true)
        assertTrue(restoredNode.succeeded)
        assertEquals(1, restoredNode.history.workspace.relations.size)
        assertEquals(
            loop.geometry,
            restoredNode.history.workspace.relations
                .getValue(loop.id)
                .geometry,
        )
    }

    @Test fun loopEndpointCannotCollapseToZeroSize() {
        val initial = base.copy(relations = mapOf(loop.id to loop))
        val collapsed = a.transform.copy(size = CanvasSize(0f, 80f))
        val operation = TransformObjectsOperation("collapse", listOf(TransformChange(a.id, a.version, a.transform, collapsed)))
        assertTrue(operation.applyTo(initial) is OperationResult.Rejected)
        assertTrue(
            CreateRelationsOperation("loop", listOf(loop)).applyTo(
                base.copy(
                    objects =
                        base.objects + (a.id to a.copy(transform = collapsed)),
                ),
            ) is OperationResult.Rejected,
        )
        val translated = a.transform.copy(position = a.transform.position + Vec2(10f, 20f))
        assertTrue(
            TransformObjectsOperation(
                "move",
                listOf(TransformChange(a.id, a.version, a.transform, translated)),
            ).applyTo(initial) is OperationResult.Applied,
        )
    }

    @Test fun routesAndManualLabelsKeepSourceOrderAcrossDirectionsAndNodeMoves() {
        val right = assertNotNull(relationWorldRoute(loop, base.objects))
        assertEquals(4, right.size)
        assertNotEquals(right.first(), right.last())
        for (direction in RelationDirection.entries) assertEquals(right, relationWorldRoute(loop.copy(direction = direction), base.objects))
        assertTrue(distanceToPolyline(polylineMidpoint(right), right) < 0.001f)
        val geometry =
            RelationGeometry(
                route = RelationRoute.Manual(listOf(Vec2(240f, 260f), Vec2(400f, 260f))),
                labelPlacement = RelationLabelPlacement.Manual(0.5f, 3f, 24f),
            )
        val edge = loop.copy(id = RelationId("edge"), targetObjectId = b.id, geometry = geometry)
        val route = assertNotNull(relationWorldRoute(edge, base.objects))
        assertEquals((geometry.route as RelationRoute.Manual).waypoints, route.drop(1).dropLast(1))
        val moved = base.objects + (a.id to a.copy(transform = a.transform.copy(position = Vec2(100f, 100f))))
        val updated = assertNotNull(relationWorldRoute(edge, moved))
        assertNotEquals(route.first(), updated.first())
        assertEquals(route.drop(1), updated.drop(1))
        assertEquals(
            Vec2(53f, 24f),
            manualRelationLabelAnchor(listOf(Vec2(0f, 0f), Vec2(100f, 0f)), geometry.labelPlacement as RelationLabelPlacement.Manual),
        )
        assertNull(manualRelationLabelAnchor(listOf(Vec2.Zero, Vec2.Zero), RelationLabelPlacement.Manual(0.5f)))
    }

    @Test fun geometryUpdateUndoRedoAndRemoteStateGuardKeepFullAttributes() {
        val initial = base.copy(relations = mapOf(loop.id to loop))
        val after =
            loop.geometry!!.copy(
                route = RelationRoute.Loop("top", 144f),
                labelPlacement = RelationLabelPlacement.Manual(0.3f, 2f, 30f),
            )
        val operation =
            UpdateRelationAttributesOperation(
                "update",
                listOf(RelationAttributesChange(loop.id, 1, attrs(loop), attrs(loop).copy(geometry = after))),
            )
        val changed = WorkspaceHistory(initial).execute(operation).history
        assertEquals(
            after,
            changed.workspace.relations
                .getValue(loop.id)
                .geometry,
        )
        val undone = changed.undo().history
        assertEquals(
            loop.geometry,
            undone.workspace.relations
                .getValue(loop.id)
                .geometry,
        )
        val redone = undone.redo().history
        assertEquals(
            after,
            redone.workspace.relations
                .getValue(loop.id)
                .geometry,
        )
        assertEquals(
            4L,
            redone.workspace.relations
                .getValue(loop.id)
                .version,
        )
        val remote =
            redone.workspace.copy(
                relations =
                    mapOf(
                        loop.id to
                            redone.workspace.relations
                                .getValue(loop.id)
                                .copy(geometry = after.copy(route = RelationRoute.Loop("left"))),
                    ),
            )
        assertFalse(redone.copy(workspace = remote).undo().succeeded)
    }

    @Test fun schemaModesBoundsAndEndpointKindsAreStrict() {
        val json =
            Json {
                encodeDefaults = true
                allowStructuredMapKeys = true
            }
        assertEquals(loop, json.decodeFromString<Relation>(json.encodeToString(loop)))
        assertFails { RelationGeometry(schema = "unknown") }
        assertFails { RelationRoute.Loop("diagonal") }
        assertFails { RelationRoute.Loop(extent = 15f) }
        assertFails { RelationRoute.Manual(emptyList()) }
        assertFails { RelationRoute.Manual(List(33) { Vec2.Zero }) }
        assertFails { RelationLabelPlacement.Manual(1.1f) }
        assertFails { RelationLabelPlacement.Manual(0.5f, normalOffset = Float.NaN) }
        assertFails { RelationLabelPlacement.Manual(0.5f, tangentOffset = 4097f) }
        assertFails { loop.copy(targetObjectId = b.id) }
        assertFails { loop.copy(geometry = RelationGeometry(route = RelationRoute.Manual(listOf(Vec2.Zero)))) }
        assertFails { json.decodeFromString<RelationGeometry>("""{"route":{"mode":"auto","waypoints":[]}}""") }
        assertNotNull(geometryFromWaypointText(null, "240,260; 400,260"))
        for (text in listOf("", "1", "NaN,2", "10000001,0", "1,2;")) assertFails { geometryFromWaypointText(null, text) }
    }

    @Test fun selectionV5AndSchemeTransferPreserveLoopAndTranslateManualWaypoints() {
        val copied = ClipboardRelation("a", "a", "Forward", label = loop.label, geometry = loop.geometry)
        val payload =
            ClipboardPayload(
                version = 5,
                nodes = listOf(ClipboardNode("a", 40f, 60f, 100f, 80f, 30f, "A", "paper", "diamond")),
                relations = listOf(copied),
            )
        assertNull(validateClipboardPayload(payload))
        assertNotNull(clipboardGeometryRoutes(payload)[copied])
        assertEquals(ClipboardPayloadIssue.InvalidRelation, validateClipboardPayload(payload.copy(version = 4)))
        val scheme =
            cg.creamgod.boarderless.data.persistence
                .QuickScheme(1, "Loop", ClipboardJson.encodeToString(payload), 5)
        val decoded = assertNotNull(decodeQuickSchemePayload(QuickSchemeTransferCodec.decode(QuickSchemeTransferCodec.encode(scheme))))
        assertEquals(payload, decoded)
        val points = RelationGeometry(route = RelationRoute.Manual(listOf(Vec2(20f, 30f)))).translated(Vec2(100f, -10f))
        assertEquals(listOf(Vec2(120f, 20f)), (points.route as RelationRoute.Manual).waypoints)
        val inserted =
            RelationInsertionDraft(
                base.copy(relations = mapOf(loop.id to loop)),
                loop.id,
            ).plan("Step", null, null, position = Vec2(260f, 180f), size = CanvasSize(60f, 50f))
        assertEquals(a.id, inserted.first.sourceObjectId)
        assertEquals(a.id, inserted.second.targetObjectId)
        assertNull(inserted.first.geometry)
        assertNull(inserted.second.geometry)
        assertTrue(
            WorkspaceHistory(base.copy(relations = mapOf(loop.id to loop)))
                .execute(inserted.operation)
                .history
                .undo()
                .succeeded,
        )
    }

    @Test fun unsupportedBackendDoesNotWriteDraftOrDispatchGeometryIncludingNestedDeletes() =
        runTest {
            val memory = InMemorySettings()
            val preferences = SessionPreferences(memory)
            val opened = WorkspaceSession("user", preferences.clientId, WorkspaceMemberRole.Editor, 0, 0, base)
            val before = memory.keys.associateWith(memory::getStringOrNull)
            var calls = 0
            val repo =
                BackendWorkspaceRepository(
                    "http://fixture",
                    preferences,
                    HttpClient(
                        MockEngine {
                            calls++
                            error("No HTTP")
                        },
                    ),
                )
            try {
                assertFalse(repo.supportsRelationGeometry)
                val operations =
                    listOf(
                        CreateRelationsOperation("create", listOf(loop)),
                        DeleteRelationsOperation("delete", listOf(loop)),
                        DeleteObjectsOperation("cascade", listOf(a), listOf(loop)),
                    )
                for (operation in operations) {
                    assertFails { repo.retainDraft(opened, base, TransactionOperation("batch", listOf(operation))) }
                    assertFails { repo.submit(opened, operation) }
                }
                assertEquals(before, memory.keys.associateWith(memory::getStringOrNull))
                assertEquals(0, calls)
            } finally {
                repo.close()
            }
        }
}
