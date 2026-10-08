package cg.creamgod.boarderless

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Image
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CanvasExportSceneRenderTest {
    @Test fun largeDesktopPreviewPublishesBothFormatsAtOneAndTwoTimes() = kotlinx.coroutines.test.runTest {
        val node = TextNode(CanvasObjectId("large"),
            transform = CanvasTransform(Vec2.Zero, CanvasSize(1450f, 2200f)), text = "Export 驗證")
        val workspace = Workspace(WorkspaceId("large-export"), "Large export", objects = mapOf(node.id to node))
        val runtime = CanvasExportRuntime(maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels)
        val root = Files.createTempDirectory("boarderless-large-export-")
        try {
            for (scale in listOf(1f, 2f)) {
                var raster: CanvasExportRaster? = null
                var measured: CanvasExportMeasuredScene? = null
                val preview = ImageComposeScene(240, 180, Density(1f), LayoutDirection.Ltr, Dispatchers.Unconfined) {
                    BoarderLessTheme(darkTheme = false) {
                        val plan = rememberCanvasExportMeasurement(workspace, scale, runtime).getOrThrow()
                        measured = plan
                        CanvasExportPreview(workspace, plan, emptyMap(), Color.White, Modifier.fillMaxSize()) {
                            raster = it.getOrThrow()
                        }
                    }
                }
                try {
                    repeat(3) { preview.render(it * 16_000_000L).close() }
                    val captured = assertNotNull(raster)
                    val plan = assertNotNull(measured).layout
                    assertEquals(plan.pixelWidth, captured.width)
                    assertEquals(plan.pixelHeight, captured.height)
                    if (scale == 2f) assertTrue(captured.width.toLong() * captured.height > 4_194_304)
                    for (format in CanvasExportFormat.entries) {
                        val target = root.resolve("canvas-$scale.${format.extension}")
                        val destination = cg.creamgod.boarderless.data.persistence.desktopCanvasExportDestination(target.toString()) { true }
                        publishCanvasExport(captured, format, destination) { true }
                        assertEquals(target.toString(), destination.savedLocation)
                        assertNotNull(destination.revealSavedFile)
                        assertTrue(Files.size(target) > 0)
                        if (format == CanvasExportFormat.Png) {
                            val image = ImageIO.read(target.toFile())
                            assertEquals(plan.pixelWidth, image.width)
                            assertEquals(plan.pixelHeight, image.height)
                            assertEquals(0xffffffff.toInt(), image.getRGB(image.width - 1, image.height - 1))
                        } else {
                            val pdf = Files.readString(target, java.nio.charset.StandardCharsets.ISO_8859_1)
                            assertTrue("/MediaBox [0 0 ${plan.pixelWidth} ${plan.pixelHeight}]" in pdf)
                            assertTrue(pdf.endsWith("%%EOF\n"))
                        }
                    }
                } finally { preview.close() }
            }
        } finally {
            Files.list(root).use { it.forEach(Files::delete) }
            Files.delete(root)
        }
    }

    @Test fun miniaturePreviewReadbackMatchesFullAuthoritativeSceneAndKeepsOutsideTextMediaAndSequence() {
        val label = TextNode(CanvasObjectId("label"), transform = CanvasTransform(Vec2(100f, 100f), CanvasSize(120f, 40f), 25f),
            text = "Floating\n標籤", textPlacement = TextPlacement(TextAnchor.Above, -90f, -30f,
                TextAppearance(fontSize = 22f, weight = 700, color = "#b71c1c", font = TextFont.Serif, underline = true)))
        val vector = TextNode(CanvasObjectId("path"), transform = CanvasTransform(Vec2(380f, 130f), CanvasSize(100f, 70f), -20f),
            text = "Pen", vectorPath = VectorPath(CanvasSize(100f, 100f), listOf(VectorPathCommand.Move(Vec2(-20f, 10f)),
                VectorPathCommand.Cubic(Vec2(20f, -40f), Vec2(100f, 130f), Vec2(120f, 80f))),
                VectorPathStyle(null, "#1565c0", 8f)))
        val media = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2(700f, 120f), CanvasSize(160f, 90f), 12f),
            assetId = "fixture", mediaKind = MediaKind.Image, altText = "Fixture")
        val loop = Relation(RelationId("loop"), sourceObjectId = label.id, targetObjectId = label.id,
            label = "Loop outside label", geometry = RelationGeometry(route = RelationRoute.Loop("left", 100f)))
        val edge = Relation(RelationId("manual"), sourceObjectId = label.id, targetObjectId = vector.id,
            label = "Manual route", geometry = RelationGeometry(route = RelationRoute.Manual(listOf(Vec2(250f, -70f))),
                labelPlacement = RelationLabelPlacement.Manual(.5f, normalOffset = -40f)))
        var workspace = Workspace(WorkspaceId("render"), "Export", objects = listOf(label, vector, media).associateBy { it.id },
            relations = listOf(loop, edge).associateBy { it.id })
        val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 0, 0, workspace)
        val draft = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse(
            "sequenceDiagram\nparticipant A as Alice\nparticipant B as Bob\nA->>B: Request\nB-->>A: Done")).draft
        var id = 0
        workspace = WorkspaceHistory(workspace).execute(SequenceCanvasCreation(owner, workspace, Vec2(80f, 320f))
            .operation(draft, "Sequence") { "diagram_${++id}" }).history.workspace
        val sourceImage = Image.makeFromEncoded(CanvasExportRaster(2, 2, intArrayOf(0xffff00ff.toInt(), 0xff00ff00.toInt(),
            0xff0000ff.toInt(), 0xffffff00.toInt())).encode(CanvasExportFormat.Png))
        val frames = mapOf(media.id to sourceImage.toComposeImageBitmap())
        var measured: CanvasExportMeasuredScene? = null
        var raster: CanvasExportRaster? = null
        val preview = ImageComposeScene(240, 180, Density(1f), LayoutDirection.Ltr, Dispatchers.Unconfined) {
            BoarderLessTheme(darkTheme = false) {
                val scene = rememberCanvasExportMeasurement(workspace, 1f).getOrThrow()
                measured = scene
                CanvasExportPreview(workspace, scene, frames, Color.White, Modifier.fillMaxSize()) { raster = it.getOrThrow() }
            }
        }
        try {
            repeat(3) { preview.render(it * 16_000_000L).close() }
            val captured = assertNotNull(raster)
            val plan = assertNotNull(measured)
            assertTrue(captured.width > 240 && captured.height > 180)
            assertTrue(plan.layout.worldBounds.top < -70f)
            assertEquals(2, plan.labels.size)
            // The direct reference uses the snapshot's sRGB surface AND the same
            // physical window metrics: Compose derives shadow lighting from that window.
            val expectedBitmap = ImageBitmap(captured.width, captured.height)
            val referenceWindow = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(240, 180)
                override val containerDpSize = DpSize(240.dp, 180.dp)
            }
            val referenceContext = object : PlatformContext by PlatformContext.Empty() {
                override val windowInfo = referenceWindow
            }
            val recomposer = FrameRecomposer(Dispatchers.Unconfined)
            val expected = CanvasLayersComposeScene(recomposer, density = Density(1f),
                size = IntSize(captured.width, captured.height), platformContext = referenceContext)
            expected.setContent {
                BoarderLessTheme(darkTheme = false) {
                    CanvasExportScene(workspace, plan.layout.viewport, plan.labels, frames, Color.White, Modifier.fillMaxSize())
                }
            }
            try {
                repeat(2) {
                    recomposer.performFrame(it * 16_000_000L)
                    expected.measureAndLayout()
                    expected.draw(Canvas(expectedBitmap))
                }
                val pixels = IntArray(captured.width * captured.height)
                expectedBitmap.readPixels(pixels)
                    val png = captured.encode(CanvasExportFormat.Png)
                    val decoded = ImageIO.read(ByteArrayInputStream(png))
                    val exported = decoded.getRGB(0, 0, captured.width, captured.height, null, 0, captured.width)
                    val firstMismatch = pixels.indices.firstOrNull { pixels[it] != exported[it] }
                    var maxError = 0; var changed = 0
                    for (i in pixels.indices) {
                        if (pixels[i] != exported[i]) changed++
                        for (shift in listOf(24, 16, 8, 0)) maxError = maxOf(maxError,
                            kotlin.math.abs((pixels[i] ushr shift and 255) - (exported[i] ushr shift and 255)))
                    }
                    println("Full scene ${captured.width}x${captured.height}; changedPixels=$changed maxChannelError=$maxError firstMismatch=$firstMismatch")
                    assertNull(firstMismatch, "Pixel mismatch at $firstMismatch; maxError=$maxError changed=$changed expected=${firstMismatch?.let { pixels[it] }} exported=${firstMismatch?.let { exported[it] }}")
                    assertTrue(pixels.any { pixel -> (pixel ushr 16 and 255) > 150 && (pixel ushr 8 and 255) < 100 })
                    System.getenv("BOARDERLESS_EXPORT_FIXTURE_DIR")?.let { directory ->
                        val root = Path.of(directory); require(root.isAbsolute); Files.createDirectories(root)
                        val destination = Files.createTempDirectory(root, "scene-")
                        Files.write(destination.resolve("canvas-scene.png"), png)
                        Files.write(destination.resolve("canvas-scene.pdf"), captured.encode(CanvasExportFormat.Pdf))
                        println("Canvas scene fixture: $destination")
                    }
            } finally { expected.close(); recomposer.close() }
        } finally { preview.close(); sourceImage.close() }
    }
}
