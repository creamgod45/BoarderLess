package cg.creamgod.boarderless.data.ai

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One daemon, bounded queue; file failures never alter approval, HTTP or response handling. */
private object DesktopAiProbeLogs {
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(128),
            { task -> Thread(task, "BoarderLess safe AI diagnostics").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardPolicy(),
        )

    fun submit(task: () -> Unit) {
        executor.execute(task)
    }
}

internal actual fun aiProbeLocalLog(
    dialect: AiRequestDialect,
    authentication: AiProbeAuthentication,
): AiProbeLocalLog? {
    val home = System.getProperty("user.home") ?: return null
    val root = runCatching { Path.of(home, ".boarderless", "logs").takeIf { it.isAbsolute } }.getOrNull() ?: return null
    return desktopAiProbeLocalLog(root, dialect, authentication, DesktopAiProbeLogs::submit)
}

/** Injectable local path/scheduler for fixtures; production always uses the user directory. */
internal fun desktopAiProbeLocalLog(
    root: Path,
    dialect: AiRequestDialect,
    authentication: AiProbeAuthentication,
    submit: (() -> Unit) -> Unit,
): AiProbeLocalLog {
    val writer = DesktopAiProbeLogWriter(root)
    val attempt = UUID.randomUUID()
    val count = AtomicInteger()
    return object : AiProbeLocalLog {
        override fun record(event: AiProbeDiagnostic) {
            // Probe emits phase changes, never a record per text delta. Still bound caller misuse.
            if (count.incrementAndGet() > 24) return
            runCatching { submit { runCatching { writer.write(attempt, dialect, authentication, event) } } }
        }
    }
}

/** Typed-only UTF-8 lines, 256 KiB current + one backup. Best effort diagnostics, not a journal.
 * Refuse symlinks and unsafe existing paths; the shared APP parent may be readable,
 * but only its owner may write it. Logs themselves remain private.
 * Desktop's lifetime storage lease excludes cooperating APP processes; not an old-writer gate.
 */
internal class DesktopAiProbeLogWriter(
    private val root: Path,
    private val maxBytes: Long = 256 * 1024,
) {
    init {
        require(root.isAbsolute && maxBytes in 1024..256 * 1024)
    }

    private fun verify(
        path: Path,
        directory: Boolean,
        posix: Boolean,
    ) {
        check(!Files.isSymbolicLink(path))
        check(if (directory) Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) else Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        if (posix) {
            check(
                Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS) ==
                    PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"),
            )
        }
    }

    @Synchronized fun write(
        attempt: UUID,
        dialect: AiRequestDialect,
        authentication: AiProbeAuthentication,
        event: AiProbeDiagnostic,
    ) {
        require(event.httpStatus == null || event.httpStatus in 100..599)
        val bytes =
            ("${Instant.now()} attempt=$attempt dialect=${dialect.name} auth=${authentication.name} ${event.reportLine()}\n")
                .toByteArray(Charsets.UTF_8)
        check(bytes.size < 1024)
        // Exactly two directories beneath the supplied user/test directory. No arbitrary mkdirs.
        val parent = requireNotNull(root.parent)
        val posix = Files.getFileStore(requireNotNull(parent.parent)).supportsFileAttributeView("posix")
        val dirs = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))) else emptyArray()
        for (directory in listOf(parent, root)) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(directory, *dirs)
                } catch (_: FileAlreadyExistsException) {
                }
            }
            if (directory == parent && posix) {
                check(!Files.isSymbolicLink(parent) && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
                check(Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS) == Files.getOwner(parent.parent, LinkOption.NOFOLLOW_LINKS))
                val permissions = Files.getPosixFilePermissions(parent, LinkOption.NOFOLLOW_LINKS)
                check(
                    java.nio.file.attribute.PosixFilePermission.GROUP_WRITE !in permissions &&
                        java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE !in permissions,
                )
            } else {
                verify(directory, true, posix)
            }
        }
        val current = root.resolve("ai-probe.log")
        val backup = root.resolve("ai-probe.log.1")
        for (file in listOf(current, backup)) if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) verify(file, false, posix)
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.size(current) + bytes.size > maxBytes) {
            Files.move(current, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        val attrs = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
        FileChannel
            .open(
                current,
                setOf<OpenOption>(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    LinkOption.NOFOLLOW_LINKS,
                ),
                *attrs,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
    }
}
