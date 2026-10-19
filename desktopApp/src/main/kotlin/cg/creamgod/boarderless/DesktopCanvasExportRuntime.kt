package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.desktopCanvasExportDestination
import cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Window
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import javax.swing.JOptionPane
import cg.creamgod.boarderless.domain.export.MaxCanvasExportDimension
import cg.creamgod.boarderless.domain.export.MaxCanvasExportPixels
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun desktopCanvasExportRuntime(window: Window) = CanvasExportRuntime(maxDimension = MaxCanvasExportDimension, maxPixels = MaxCanvasExportPixels) { name, format, canWrite ->
    require(name.length in 1..180 && name.none { it == '/' || it == '\\' || it.code < 32 })
    val path: String? = suspendCancellableCoroutine { pending ->
        val chooser = JFileChooser().apply {
            selectedFile = java.io.File("${name.removeSuffix(".${format.extension}")}-${UUID.randomUUID().toString().take(8)}.${format.extension}")
            fileFilter = FileNameExtensionFilter(format.extension.uppercase(), format.extension)
            isAcceptAllFileFilterUsed = false
        }
        pending.invokeOnCancellation { SwingUtilities.invokeLater { chooser.cancelSelection() } }
        SwingUtilities.invokeLater {
            if (!pending.isActive) return@invokeLater
            try {
                var chosen: String? = null
                while (pending.isActive && canWrite()) {
                    if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION || !canWrite()) break
                    val selected = chooser.selectedFile.toPath().toAbsolutePath()
                    val target = if (selected.fileName.toString().endsWith(".${format.extension}", true)) selected
                        else selected.resolveSibling(selected.fileName.toString() + ".${format.extension}")
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        val chinese = java.util.Locale.getDefault().language == "zh"
                        JOptionPane.showMessageDialog(chooser,
                            if (chinese) "檔案已存在，請選擇另一個檔名。" else "This file already exists. Choose another filename.",
                            if (chinese) "匯出 PNG／PDF" else "Export PNG / PDF", JOptionPane.INFORMATION_MESSAGE)
                        continue
                    }
                    chosen = target.toString()
                    break
                }
                if (pending.isActive) pending.resume(chosen)
            } catch (failure: Exception) {
                if (pending.isActive) pending.resumeWithException(failure)
            }
        }
    }
    path?.let { desktopCanvasExportDestination(it, canWrite) }
}
