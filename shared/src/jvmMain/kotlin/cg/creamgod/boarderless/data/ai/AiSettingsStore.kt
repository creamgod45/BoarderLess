package cg.creamgod.boarderless.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

internal class ProcessResult(
    val exitCode: Int,
    val output: String,
)

/** Command boundary so vault command shapes are testable without touching a real keychain. */
internal fun interface ProcessRunner {
    fun run(
        command: List<String>,
        stdin: String?,
    ): ProcessResult?
}

internal object SystemProcessRunner : ProcessRunner {
    override fun run(
        command: List<String>,
        stdin: String?,
    ): ProcessResult? =
        try {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            process.outputStream.use { out -> if (stdin != null) out.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
            val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            process.errorStream.readBytes()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                ProcessResult(process.exitValue(), output)
            }
        } catch (_: Exception) {
            null
        }
}

/** Secret storage held by the operating system. Secrets cross process boundaries only on stdin. */
internal interface DesktopSecretVault {
    fun available(): Boolean

    fun read(): String?

    fun write(secret: String): Boolean

    fun delete(): Boolean
}

private const val VAULT_ACCOUNT = "boarderless"
private const val VAULT_SERVICE = "BoarderLess AI token"

/** macOS Keychain through `security -i`: commands arrive on stdin and the secret is hex encoded,
 * so it is neither visible in the process list nor subject to shell/quote parsing. */
internal class MacKeychainVault(
    private val runner: ProcessRunner,
    private val service: String = VAULT_SERVICE,
) : DesktopSecretVault {
    private val tool = "/usr/bin/security"

    override fun available() = java.io.File(tool).canExecute()

    override fun read(): String? {
        val result = runner.run(listOf(tool, "find-generic-password", "-a", VAULT_ACCOUNT, "-s", service, "-w"), null) ?: return null
        return result.output.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    override fun write(secret: String): Boolean {
        val hex = secret.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }
        val command = "add-generic-password -a $VAULT_ACCOUNT -s \"$service\" -U -X $hex\n"
        return runner.run(listOf(tool, "-i"), command)?.exitCode == 0
    }

    override fun delete(): Boolean {
        val result = runner.run(listOf(tool, "delete-generic-password", "-a", VAULT_ACCOUNT, "-s", service), null) ?: return false
        // 44 = item not found: nothing left to delete.
        return result.exitCode == 0 || result.exitCode == 44
    }
}

/** Linux libsecret via `secret-tool`; the secret is read from stdin. Unavailable without the tool. */
internal class LibsecretVault(
    private val runner: ProcessRunner,
) : DesktopSecretVault {
    private val attributes = listOf("service", "boarderless-ai-token")

    override fun available() = runner.run(listOf("secret-tool", "--version"), null)?.exitCode?.let { true } ?: false

    override fun read(): String? {
        val result = runner.run(listOf("secret-tool", "lookup") + attributes, null) ?: return null
        return result.output.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    override fun write(secret: String): Boolean =
        runner.run(listOf("secret-tool", "store", "--label=$VAULT_SERVICE") + attributes, secret)?.exitCode == 0

    override fun delete(): Boolean = runner.run(listOf("secret-tool", "clear") + attributes, null)?.exitCode == 0
}

/** Windows DPAPI (current user) through PowerShell. The ciphertext lives in a private file; the
 * plaintext crosses only stdin. */
internal class WindowsDpapiVault(
    private val runner: ProcessRunner,
    private val file: Path,
) : DesktopSecretVault {
    private val prelude = "Add-Type -AssemblyName System.Security; \$in = [Console]::In.ReadToEnd();"

    override fun available() = runner.run(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", "exit 0"), null)?.exitCode == 0

    override fun read(): String? {
        val cipher = runCatching { Files.readString(file).trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val script = "$prelude [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String(\$in.Trim()), \$null, 'CurrentUser'))"
        val result = runner.run(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script), cipher) ?: return null
        return result.output.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    override fun write(secret: String): Boolean {
        val script = "$prelude [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes(\$in), \$null, 'CurrentUser'))"
        val result = runner.run(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script), secret) ?: return false
        val cipher = result.output.trim()
        if (result.exitCode != 0 || cipher.isEmpty()) return false
        return runCatching { writePrivateFile(file, cipher) }.isSuccess
    }

    override fun delete(): Boolean = runCatching { Files.deleteIfExists(file) }.isSuccess
}

private fun writePrivateFile(
    path: Path,
    text: String,
) {
    Files.createDirectories(path.parent)
    val temp = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
    try {
        runCatching { Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------")) }
        Files.writeString(temp, text)
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temp)
    }
}

internal class DesktopAiSettingsStore(
    private val directory: Path,
    private val vault: DesktopSecretVault?,
) : AiSettingsStore {
    private val file = directory.resolve("ai-settings.json")
    private val json = Json { ignoreUnknownKeys = true }
    override val tokenProtected: Boolean get() = vault != null

    override suspend fun loadSettings(): AiSavedSettings? =
        withContext(Dispatchers.IO) {
            runCatching {
                // Bound the actual read, not only a racy pre-read file size. Settings contain
                // no secrets; still refuse a linked/non-regular source and malformed UTF-8.
                require(!Files.isSymbolicLink(file) && Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                val bytes = Files.newInputStream(file, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(64 * 1024 + 1) }
                require(bytes.size in 1..64 * 1024)
                val raw = bytes.decodeToString(throwOnInvalidSequence = true)
                val document = cg.creamgod.boarderless.data.parseStrictJsonObject(raw)
                json.decodeFromJsonElement(AiSavedSettings.serializer(), document)
            }.getOrNull()
        }

    override suspend fun saveSettings(settings: AiSavedSettings): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { writePrivateFile(file, json.encodeToString(AiSavedSettings.serializer(), settings)) }.isSuccess
        }

    override suspend fun loadToken(): String? = withContext(Dispatchers.IO) { runCatching { vault?.read() }.getOrNull() }

    override suspend fun saveToken(token: String): Boolean =
        withContext(Dispatchers.IO) { vault != null && runCatching { vault.write(token) }.getOrDefault(false) }

    override suspend fun clearToken(): Boolean = withContext(Dispatchers.IO) { vault == null || runCatching { vault.delete() }.getOrDefault(false) }
}

internal fun desktopSecretVault(
    directory: Path,
    runner: ProcessRunner = SystemProcessRunner,
    osName: String = System.getProperty("os.name").orEmpty(),
): DesktopSecretVault? {
    val name = osName.lowercase()
    val vault =
        when {
            "mac" in name -> MacKeychainVault(runner)
            "win" in name -> WindowsDpapiVault(runner, directory.resolve("ai-token.dpapi"))
            else -> LibsecretVault(runner)
        }
    return vault.takeIf { it.available() }
}

internal actual fun aiSettingsStore(): AiSettingsStore? {
    val home = System.getProperty("user.home") ?: return null
    val directory = runCatching { Path.of(home, ".boarderless").takeIf { it.isAbsolute } }.getOrNull() ?: return null
    return DesktopAiSettingsStore(directory, desktopSecretVault(directory))
}
