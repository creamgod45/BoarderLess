package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.DraftImportRuntime
import java.awt.Window
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun desktopDraftImportRuntime(window: Window) = DraftImportRuntime { canRead ->
    currentCoroutineContext().ensureActive()
    check(canRead())
    val path = suspendCancellableCoroutine<Path?> { pending ->
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("JSON draft backup", "json")
            isAcceptAllFileFilterUsed = false
            isMultiSelectionEnabled = false
        }
        pending.invokeOnCancellation { SwingUtilities.invokeLater { chooser.cancelSelection() } }
        SwingUtilities.invokeLater {
            if (!pending.isActive) return@invokeLater
            try {
                if (!canRead()) { pending.resume(null); return@invokeLater }
                val selected = if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION)
                    chooser.selectedFile.toPath().toAbsolutePath() else null
                if (pending.isActive) pending.resume(selected)
            } catch (_: Exception) {
                if (pending.isActive) pending.resumeWithException(IllegalArgumentException("Draft selection failed"))
            }
        }
    }
    currentCoroutineContext().ensureActive()
    check(canRead())
    path?.let { readDesktopDraftJson(it, canRead) }
}

/** Regular new selection only. Read-only channel, no symlink following, bounded even if file grows. */
internal suspend fun readDesktopDraftJson(path: Path, canRead: () -> Boolean): String = withContext(Dispatchers.IO) {
    try {
        currentCoroutineContext().ensureActive()
        check(canRead())
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            require(channel.size() in 1..MaximumDraftBytes.toLong())
            val bytes = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                check(canRead())
                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) break
                require(bytes.size() + count <= MaximumDraftBytes)
                bytes.write(buffer.array(), 0, count)
            }
            currentCoroutineContext().ensureActive()
            check(canRead())
            require(bytes.size() > 0)
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix("\uFEFF")
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { throw IllegalArgumentException("Draft file could not be read") }
}

private const val MaximumDraftBytes = 4 * 1024 * 1024
