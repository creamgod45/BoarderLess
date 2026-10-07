package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.DraftBackupDestination
import cg.creamgod.boarderless.data.DraftBackupRuntime
import java.awt.Window
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun desktopDraftBackupRuntime(window: Window) = DraftBackupRuntime { suggested ->
    val path = suspendCancellableCoroutine<Path?> { pending ->
        val chooser = JFileChooser().apply {
            selectedFile = java.io.File(suggested)
            fileFilter = FileNameExtensionFilter("JSON draft backup", "json")
            isAcceptAllFileFilterUsed = false
        }
        pending.invokeOnCancellation { SwingUtilities.invokeLater { chooser.cancelSelection() } }
        SwingUtilities.invokeLater {
            if (!pending.isActive) return@invokeLater
            try {
                val selected = if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                    val chosen = chooser.selectedFile.toPath().toAbsolutePath()
                    if (chosen.fileName.toString().endsWith(".json", ignoreCase = true)) chosen
                    else chosen.resolveSibling(chosen.fileName.toString() + ".json")
                } else null
                if (pending.isActive) pending.resume(selected)
            } catch (error: Exception) { if (pending.isActive) pending.resumeWithException(error) }
        }
    }
    path?.let(::DesktopDraftBackupDestination)
}

/** No fallback directory or overwrite. Guard is checked on IO, not merely before dispatch. */
internal class DesktopDraftBackupDestination(private val path: Path) : DraftBackupDestination {
    private var used = false
    private var closed = false
    override suspend fun write(json: String, canWrite: () -> Boolean) = withContext(Dispatchers.IO) {
        check(!closed && !used) { "Draft destination already used" }
        used = true
        coroutineContext.ensureActive()
        check(canWrite()) { "Draft backup scope changed" }
        // CREATE_NEW prevents races with an existing file or symlink. Failure to open never deletes it.
        val stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        try {
            stream.use {
                coroutineContext.ensureActive()
                check(canWrite()) { "Draft backup scope changed" }
                it.write(json.toByteArray(StandardCharsets.UTF_8))
                it.flush()
                coroutineContext.ensureActive()
                check(canWrite()) { "Draft backup scope changed" }
            }
        } catch (error: Throwable) {
            try { stream.close() } catch (_: Exception) {}
            try { Files.deleteIfExists(path) } catch (_: Exception) {}
            throw error
        }
    }
    override fun close() { closed = true }
}
