package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.feature.canvas.CanvasExportDestination

/** Reuses the force + atomic new-file publication already used for binary bundles. */
fun desktopCanvasExportDestination(path: String, canWrite: () -> Boolean): CanvasExportDestination {
    val file = DesktopSchemeBundleDestination(java.nio.file.Path.of(path), maximumBytes = Long.MAX_VALUE, canWrite = canWrite)
    return object : CanvasExportDestination {
        override suspend fun writeChunk(bytes: ByteArray) = file.writeChunk(bytes)
        override suspend fun commit() = file.commit()
        override val savedLocation: String get() = java.nio.file.Path.of(path).toAbsolutePath().normalize().toString()
        override val revealSavedFile: () -> Unit get() = {
            val desktop = java.awt.Desktop.getDesktop()
            val saved = java.io.File(savedLocation)
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(saved)
            else desktop.open(saved.parentFile)
        }
        override suspend fun abort() = file.abort()
    }
}
