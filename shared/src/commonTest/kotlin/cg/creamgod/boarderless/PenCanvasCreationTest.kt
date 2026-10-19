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
import kotlinx.serialization.json.Json
import kotlin.test.*

class PenCanvasCreationTest {
    private val workspace = Workspace(WorkspaceId("board"), "Board")
    private val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 0, 0, workspace)
    private val capture = PenCanvasCreation(owner, workspace, Vec2(50f, 60f))
    private val path =
        VectorPath(
            CanvasSize(400f, 300f),
            listOf(
                VectorPathCommand.Move(Vec2(-10f, -20f)),
                VectorPathCommand.Cubic(Vec2(20f, -40f), Vec2(50f, 90f), Vec2(100f, 100f)),
                VectorPathCommand.Line(Vec2(100f, -20f)),
                VectorPathCommand.Close,
            ),
        )

    @Test fun editorResultNormalizesFullControlAndStrokeBoundsWithoutChangingCommands() {
        val operation = capture.operation(path, "vector", "insert")
        val node = operation.objects.single() as TextNode
        val saved = assertNotNull(node.vectorPath)
        assertEquals(Vec2(50f, 60f), node.transform.position)
        assertEquals(CanvasSize(112f, 142f), saved.viewBox)
        assertEquals(saved.viewBox, node.transform.size)
        assertEquals(Vec2(1f, 21f), (saved.commands.first() as VectorPathCommand.Move).point)
        assertEquals(path.style, saved.style)
        assertEquals(1, node.zIndex)
        assertEquals(path.commands.size, saved.commands.size)
        assertEquals(node, Json.decodeFromString<TextNode>(Json.encodeToString(node)))
        assertTrue(workspace.objects.isEmpty())
    }

    @Test fun noAreaNoSegmentsOversizedBoundsAndForeignCanvasCannotCreate() {
        val coincident = path.copy(commands = listOf(VectorPathCommand.Move(Vec2.Zero), VectorPathCommand.Line(Vec2.Zero)))
        assertFails { capture.operation(coincident, "vector", "insert") }
        val fillOnly =
            path.copy(
                commands = listOf(VectorPathCommand.Move(Vec2.Zero), VectorPathCommand.Line(Vec2(10f, 0f))),
                style = VectorPathStyle(strokeColorToken = null),
            )
        assertFails { capture.operation(fillOnly, "vector", "insert") }
        val huge = path.copy(commands = listOf(VectorPathCommand.Move(Vec2(-100_000f, 0f)), VectorPathCommand.Line(Vec2(100_000f, 0f))))
        assertFails { capture.operation(huge, "vector", "insert") }
        assertFails { capture.copy(baseline = workspace.copy(id = WorkspaceId("foreign"))) }
    }

    @Test fun ordinaryNodeSerializationDoesNotAcquireANewNullFieldOrChangeExistingReceipts() {
        val node = TextNode(CanvasObjectId("ordinary"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 100f)), text = "Text")
        val attributes = TextNodeAttributes(0, false, "paper")
        val json = Json { encodeDefaults = true }
        assertFalse("vectorPath" in json.encodeToString(node))
        assertFalse("vectorPath" in json.encodeToString(attributes))
        assertNotNull(Json.decodeFromString<TextNode>(json.encodeToString(node)).copy(vectorPath = path).vectorPath)
    }

    @Test fun insertionRequiresCapturedActorPermissionCheckpointAndCompleteCanvas() {
        assertTrue(capture.isCurrent(owner, workspace))
        assertFalse(capture.isCurrent(null, workspace))
        for (current in listOf(
            owner.copy(userId = "other"),
            owner.copy(clientId = "other"),
            owner.copy(role = WorkspaceMemberRole.Viewer),
            owner.copy(workspaceVersion = 1),
            owner.copy(lastServerSeq = 1),
            owner.copy(workspace = workspace.copy(id = WorkspaceId("other"))),
        )) {
            assertFalse(capture.isCurrent(current, workspace))
        }
        assertFalse(capture.isCurrent(owner, workspace.copy(title = "Changed")))
    }

    @Test fun vectorAttributesUndoRedoConversionAndOrdinaryEditsKeepTheExactPath() {
        val created = WorkspaceHistory(workspace).execute(capture.operation(path, "vector", "insert")).history
        val node =
            created.workspace.objects.values
                .single() as TextNode
        val before = TextNodeAttributes(node.zIndex, node.locked, node.colorToken, node.shape, node.vectorPath)
        val convert =
            UpdateTextNodeAttributesOperation(
                "convert",
                listOf(TextNodeAttributesChange(node.id, node.version, before, before.copy(shape = NodeShape.Ellipse, vectorPath = null))),
            )
        val converted = created.execute(convert).history
        assertNull((converted.workspace.objects.getValue(node.id) as TextNode).vectorPath)
        val restored = converted.undo().history
        assertEquals(node.vectorPath, (restored.workspace.objects.getValue(node.id) as TextNode).vectorPath)
        assertNull(
            (
                restored
                    .redo()
                    .history.workspace.objects
                    .getValue(node.id) as TextNode
            ).vectorPath,
        )
        val ordinary = created.execute(EditTextOperation("label", listOf(TextChange(node.id, node.version, "", "向量🙂")))).history
        assertEquals(node.vectorPath, (ordinary.workspace.objects.getValue(node.id) as TextNode).vectorPath)
        val transformed = node.transform.copy(position = Vec2(200f, 300f), size = CanvasSize(224f, 284f), rotationDegrees = 45f)
        val move =
            created.execute(
                TransformObjectsOperation("transform", listOf(TransformChange(node.id, node.version, node.transform, transformed))),
            )
        assertTrue(move.succeeded)
        assertEquals(
            node.vectorPath,
            (
                move.history.workspace.objects
                    .getValue(node.id) as TextNode
            ).vectorPath,
        )
        assertEquals(
            node.transform,
            move.history
                .undo()
                .history.workspace.objects
                .getValue(node.id)
                .transform,
        )
        assertFalse(
            created
                .execute(
                    TransformObjectsOperation(
                        "collapse",
                        listOf(TransformChange(node.id, node.version, node.transform, node.transform.copy(size = CanvasSize(0f, 0f)))),
                    ),
                ).succeeded,
        )
        val zero = node.copy(vectorPath = null, transform = node.transform.copy(size = CanvasSize(0f, 0f)))
        val emptyAttrs = before.copy(vectorPath = null)
        assertIs<OperationResult.Rejected>(
            UpdateTextNodeAttributesOperation(
                "attach",
                listOf(TextNodeAttributesChange(zero.id, zero.version, emptyAttrs, before)),
            ).applyTo(
                created.workspace.copy(
                    objects =
                        mapOf(zero.id to zero),
                ),
            ),
        )
        assertContains(
            draftMergeContractGaps(workspace, TransactionOperation("batch", listOf(capture.operation(path, "new", "new-path")))),
            DraftMergeContractGap.VectorPath,
        )
        assertTrue(
            created
                .undo()
                .history.workspace.objects
                .isEmpty(),
        )
    }

    @Test fun selectionV6AndSchemeFilePreservePathWhileOldSelectionVersionsRejectIt() {
        val node = capture.operation(path, "vector", "insert").objects.single() as TextNode
        val copied =
            ClipboardNode(
                "vector",
                0f,
                0f,
                node.transform.size.width,
                node.transform.size.height,
                0f,
                "",
                "paper",
                vectorPath = node.vectorPath,
            )
        val selection = ClipboardPayload(nodes = listOf(copied))
        assertEquals(6, selection.version)
        assertNull(validateClipboardPayload(selection))
        for (version in 1..5) {
            assertEquals(
                ClipboardPayloadIssue.UnsupportedVersion,
                validateClipboardPayload(selection.copy(version = version)),
            )
        }
        val scheme =
            cg.creamgod.boarderless.data.persistence
                .QuickScheme(0, "Curve", ClipboardJson.encodeToString(selection), 6)
        val transferred = QuickSchemeTransferCodec.decode(QuickSchemeTransferCodec.encode(scheme))
        assertEquals(node.vectorPath, decodeQuickSchemePayload(transferred)!!.nodes.single().vectorPath)
        for (style in listOf(VectorPathStyle(fillColorToken = null), VectorPathStyle(strokeColorToken = null))) {
            val variant = selection.copy(nodes = listOf(copied.copy(vectorPath = node.vectorPath!!.copy(style = style))))
            val variantScheme = scheme.copy(payload = ClipboardJson.encodeToString(variant))
            val roundTrip = QuickSchemeTransferCodec.decode(QuickSchemeTransferCodec.encode(variantScheme))
            assertEquals(
                style,
                decodeQuickSchemePayload(roundTrip)!!
                    .nodes
                    .single()
                    .vectorPath!!
                    .style,
            )
        }
    }

    @Test fun unnegotiatedBackendRejectsNativeVectorBeforeDraftOrHttp() =
        runTest {
            var calls = 0
            val preferences = SessionPreferences(InMemorySettings().apply { putString("backend.clientId", "client") })
            val repository =
                BackendWorkspaceRepository(
                    "http://fixture",
                    preferences,
                    HttpClient(
                        MockEngine {
                            calls++
                            error("Unexpected HTTP")
                        },
                    ),
                )
            val op = capture.operation(path, "vector", "insert")
            try {
                assertFalse(repository.supportsVectorPaths)
                assertFails { repository.retainDraft(owner, workspace, TransactionOperation("batch", listOf(op))) }
                assertFails { repository.submit(owner, op) }
                assertEquals(0, calls)
                assertEquals(0, preferences.clientSequence)
                assertNull(repository.pendingChange(owner))
            } finally {
                repository.close()
            }
        }

    @Test fun customContourHitAndRelationPortsUseTheCurveInsteadOfFallbackRectangle() {
        val triangle =
            VectorPath(
                CanvasSize(100f, 100f),
                listOf(
                    VectorPathCommand.Move(Vec2.Zero),
                    VectorPathCommand.Line(Vec2(100f, 0f)),
                    VectorPathCommand.Line(Vec2(0f, 100f)),
                    VectorPathCommand.Close,
                ),
            )
        val node =
            TextNode(
                CanvasObjectId("triangle"),
                transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 100f)),
                text = "",
                vectorPath = triangle,
            )
        assertTrue(node.transform.containsVectorWorldPoint(triangle, Vec2(10f, 10f)))
        assertFalse(node.transform.containsVectorWorldPoint(triangle, Vec2(90f, 90f)))
        val boundary = node.connectionBoundary(node.transform, Vec2(150f, 50f))
        assertEquals(50f, boundary.x, 0.1f)
        assertEquals(50f, boundary.y, 0.1f)
        val moved = node.copy(transform = node.transform.copy(position = Vec2(100f, 50f), rotationDegrees = 90f))
        assertTrue(moved.transform.containsVectorWorldPoint(triangle, Vec2(190f, 60f)))
        val target = TextNode(CanvasObjectId("target"), transform = CanvasTransform(Vec2(200f, 0f), CanvasSize(100f, 100f)), text = "")
        val route =
            assertNotNull(
                relationWorldRoute(
                    Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = target.id),
                    mapOf(node.id to node, target.id to target),
                ),
            )
        assertEquals(boundary, route.first())
    }
}
