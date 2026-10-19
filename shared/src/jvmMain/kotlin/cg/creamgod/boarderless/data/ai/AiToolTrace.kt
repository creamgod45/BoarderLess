package cg.creamgod.boarderless.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.Executors

private const val TRACE_VALUE_LIMIT = 16 * 1024

/** JSON Lines, 1 MiB current + one backup, owner-only. Symlinks and unsafe paths are refused.
 * Failures are swallowed: tracing must never change approval, HTTP or tool handling. */
internal class DesktopAiToolTrace(
    private val directory: Path,
    private val maxBytes: Long = 1024 * 1024,
    private val submit: (() -> Unit) -> Unit = { task -> executor.execute(task) },
) : AiToolTrace {
    override fun record(
        type: String,
        request: String,
        round: Int,
        fields: Map<String, String>,
    ) {
        val line =
            JsonObject(
                buildMap {
                    put("ts", JsonPrimitive(Instant.now().toString()))
                    put("type", JsonPrimitive(type))
                    put("request", JsonPrimitive(request))
                    put("round", JsonPrimitive(round))
                    fields.forEach { (key, value) ->
                        put(key, JsonPrimitive(if (value.length > TRACE_VALUE_LIMIT) value.take(TRACE_VALUE_LIMIT) + "…(truncated)" else value))
                    }
                },
            ).toString() + "\n"
        submit { runCatching { write(line.toByteArray(Charsets.UTF_8)) } }
    }

    @Synchronized
    internal fun write(bytes: ByteArray) {
        require(directory.isAbsolute)
        val posix = Files.getFileStore(directory.root ?: directory).supportsFileAttributeView("posix")
        val dirAttr = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))) else emptyArray()
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(directory, *dirAttr)
        check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory))
        val current = directory.resolve("ai-trace.jsonl")
        val backup = directory.resolve("ai-trace.jsonl.1")
        for (file in listOf(current, backup)) {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        }
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.size(current) + bytes.size > maxBytes) {
            Files.move(current, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        val attrs = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
        java.nio.channels.FileChannel
            .open(
                current,
                setOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS),
                *attrs,
            ).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
    }

    private companion object {
        val executor =
            Executors.newSingleThreadExecutor { task -> Thread(task, "BoarderLess AI trace").apply { isDaemon = true } }
    }
}

internal actual fun aiToolTrace(): AiToolTrace? {
    val home = System.getProperty("user.home") ?: return null
    val directory = runCatching { Path.of(home, ".boarderless", "logs").takeIf { it.isAbsolute } }.getOrNull() ?: return null
    return DesktopAiToolTrace(directory)
}
