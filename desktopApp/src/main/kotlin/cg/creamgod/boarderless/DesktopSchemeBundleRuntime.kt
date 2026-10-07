package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.desktopSchemeBundleDestination
import cg.creamgod.boarderless.data.persistence.desktopSchemeBundleReceipts
import cg.creamgod.boarderless.data.persistence.desktopSchemeBundleSource
import cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Window
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun desktopSchemeBundleRuntime(window: Window) = SchemeBundleRuntime(
    chooseSource = { canRead ->
        chooseBundlePath(window, null)?.let { desktopSchemeBundleSource(it, canRead) }
    },
    chooseDestination = { name, canWrite ->
        chooseBundlePath(window, name)?.let { desktopSchemeBundleDestination(it, canWrite) }
    },
    receipts = ::desktopSchemeBundleReceipts,
)

private suspend fun chooseBundlePath(window: Window, suggested: String?): String? = suspendCancellableCoroutine { pending ->
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("BoarderLess scheme with originals", "blscheme")
        isAcceptAllFileFilterUsed = false
        if (suggested != null) selectedFile = java.io.File(suggested)
    }
    pending.invokeOnCancellation { SwingUtilities.invokeLater { chooser.cancelSelection() } }
    SwingUtilities.invokeLater {
        if (!pending.isActive) return@invokeLater
        try {
            val result = if ((if (suggested == null) chooser.showOpenDialog(window) else chooser.showSaveDialog(window)) == JFileChooser.APPROVE_OPTION) {
                val path = chooser.selectedFile.toPath().toAbsolutePath()
                if (suggested == null || path.fileName.toString().endsWith(".blscheme", true)) path.toString()
                else path.resolveSibling(path.fileName.toString() + ".blscheme").toString()
            } else null
            if (pending.isActive) pending.resume(result)
        } catch (error: Exception) {
            if (pending.isActive) pending.resumeWithException(error)
        }
    }
}
