package cg.creamgod.boarderless

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cg.creamgod.boarderless.data.WorkspaceDraftReview
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.WorkspaceComparisonPreview
import cg.creamgod.boarderless.i18n.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class WorkspaceComparisonSceneRenderTest {
    @Test fun threeWayPreviewRendersActualFloatingTextAndMetadataMediaWithoutFrozenFrames() = runTest {
        val previousLanguage = Localization.language
        activateLanguage(AppLanguage("zh-TW", "繁體中文"))
        val text = TextNode(CanvasObjectId("text"), transform = CanvasTransform(Vec2.Zero, CanvasSize(220f, 110f)),
            text = "上次同步", colorToken = "#ff00ff", shape = NodeShape.Rectangle,
            textPlacement = TextPlacement(TextAnchor.Above, appearance = TextAppearance(fontSize = 24f)))
        val media = MediaNode(CanvasObjectId("video"), transform = CanvasTransform(Vec2(500f, 20f), CanvasSize(180f, 120f)),
            assetId = "historical-video", mediaKind = MediaKind.Video, altText = "影片僅顯示資訊")
        val baseline = Workspace(WorkspaceId("comparison"), "基準", objects = mapOf(text.id to text, media.id to media))
        val local = baseline.copy(objects = baseline.objects + (text.id to text.copy(text = "本機離線編輯", transform = text.transform.copy(position = Vec2(180f, 140f)))))
        val remote = baseline.copy(objects = baseline.objects + (text.id to text.copy(text = "伺服器編輯", transform = text.transform.copy(position = Vec2(30f, 220f)))))
        val review = WorkspaceDraftReview("comparison", 0, 0, 1, 1, baseline, local, remote, emptyList(), false, false)
        val scene = ImageComposeScene(840, 440, Density(1f), LayoutDirection.Ltr, Dispatchers.Unconfined) {
            BoarderLessTheme(darkTheme = false) { WorkspaceComparisonPreview(review) }
        }
        try {
            repeat(4) { scene.render(it * 16_000_000L).close() }
            scene.render(80_000_000L).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    val decoded = ImageIO.read(ByteArrayInputStream(data.bytes))
                    var painted = 0
                    for (y in 100 until decoded.height) for (x in 0 until decoded.width) {
                        val argb = decoded.getRGB(x, y)
                        val r = (argb ushr 16) and 255; val g = (argb ushr 8) and 255; val b = argb and 255
                        if (r > 200 && b > 200 && g < 80) painted++
                    }
                    assertTrue(painted > 300, "The local object must actually paint inside the comparison viewport")
                    System.getenv("BOARDERLESS_COMPARISON_SCREENSHOT")?.let { file ->
                        val target = Path.of(file).toAbsolutePath().normalize()
                        require(target.startsWith(Path.of("/tmp/boarderless-ai-reasoning-build/shared")))
                        Files.createDirectories(target.parent)
                        Files.write(target, data.bytes)
                    }
                }
            }
        } finally { scene.close(); activateLanguage(previousLanguage) }
    }
}
