package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Hands "open this workspace" launches (a Windows jump list entry starts a new process) to the app
 * that is already running, over a loopback socket. The running app writes its port and a random
 * token to a file only the user can read; a launch that cannot reach it just starts normally.
 */
internal object DesktopInstanceChannel {
    private const val ConnectTimeoutMillis = 1000
    private const val ReadTimeoutMillis = 2000
    private const val ClaimIntervalMillis = 3000L
    private val endpointFile: File = run {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { isWindows && it.isNotBlank() }
        val directory = if (localAppData != null) File(localAppData, "BoarderLess") else File(System.getProperty("user.home"), ".boarderless")
        File(directory, "instance")
    }

    /** True when the running app accepted the request, so this process should exit. */
    fun forward(workspaceId: String): Boolean = runCatching {
        val (port, token) = endpointFile.readText().trim().split(' ').takeIf { it.size == 2 } ?: return false
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port.toInt()), ConnectTimeoutMillis)
            socket.soTimeout = ReadTimeoutMillis
            allowRunningInstanceToComeForward()
            socket.getOutputStream().bufferedWriter().apply { write("$token open $workspaceId\n"); flush() }
            socket.getInputStream().bufferedReader().readLine() == "ok"
        }
    }.getOrDefault(false)

    /**
     * Accepts forwarded requests for as long as the app runs. Several instances can run; the endpoint
     * file names one that is alive, and another instance claims it when that one exits.
     */
    fun listen(onOpen: (String) -> Unit) {
        runCatching {
            val token = UUID.randomUUID().toString()
            val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            val endpoint = "${server.localPort} $token"
            Runtime.getRuntime().addShutdownHook(thread(start = false) {
                if (runCatching { endpointFile.readText().trim() == endpoint }.getOrDefault(false)) endpointFile.delete()
            })
            thread(isDaemon = true, name = "BoarderLess instance channel") {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { socket.use { handle(it, token, onOpen) } }
                }
            }
            thread(isDaemon = true, name = "BoarderLess instance endpoint") {
                while (true) {
                    if (!endpointIsLive()) runCatching { writeEndpoint(endpoint) }
                    Thread.sleep(ClaimIntervalMillis)
                }
            }
        }
    }

    private fun endpointIsLive(): Boolean = runCatching {
        val port = endpointFile.readText().trim().substringBefore(' ').toInt()
        Socket().use { it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), ConnectTimeoutMillis) }
        true
    }.getOrDefault(false)

    private fun handle(socket: Socket, token: String, onOpen: (String) -> Unit) {
        socket.soTimeout = ReadTimeoutMillis
        val parts = socket.getInputStream().bufferedReader().readLine()?.split(' ').orEmpty()
        val accepted = parts.size == 3 && parts[0] == token && parts[1] == "open" && WorkspaceLaunchRequests.isWorkspaceId(parts[2])
        if (accepted) onOpen(parts[2])
        socket.getOutputStream().bufferedWriter().apply { write(if (accepted) "ok\n" else "rejected\n"); flush() }
    }

    /** Owner-only from the start on POSIX; on Windows %LOCALAPPDATA% is already private to the user. */
    private fun writeEndpoint(content: String) {
        endpointFile.parentFile.mkdirs()
        val path = endpointFile.toPath()
        Files.deleteIfExists(path)
        runCatching { Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
        endpointFile.writeText(content)
    }

    /** Windows only lets the foreground process (this launch) pass foreground rights on. */
    private fun allowRunningInstanceToComeForward() {
        if (!isWindows) return
        runCatching {
            com.sun.jna.NativeLibrary.getInstance("user32").getFunction("AllowSetForegroundWindow")
                .invokeInt(arrayOf(-1)) // ASFW_ANY
        }
    }
}

internal val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

internal const val OpenWorkspaceArgument = "--open-workspace="
