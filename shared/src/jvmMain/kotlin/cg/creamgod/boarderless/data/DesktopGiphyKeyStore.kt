package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.ai.DesktopSecretVault
import cg.creamgod.boarderless.data.ai.desktopSecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class VaultGiphyKeyStore(
    private val vault: DesktopSecretVault,
) : GiphyKeyStore {
    override suspend fun load(): String? = withContext(Dispatchers.IO) { runCatching { vault.read() }.getOrNull() }

    override suspend fun save(key: String): Boolean = withContext(Dispatchers.IO) { runCatching { vault.write(key) }.getOrDefault(false) }

    override suspend fun clear(): Boolean = withContext(Dispatchers.IO) { runCatching { vault.delete() }.getOrDefault(false) }
}

/** macOS Keychain, Windows DPAPI or Linux libsecret; null when none is available (key stays in memory). */
fun desktopGiphyKeyStore(): GiphyKeyStore? {
    val home = System.getProperty("user.home") ?: return null
    val directory = runCatching { Path.of(home, ".boarderless").takeIf { it.isAbsolute } }.getOrNull() ?: return null
    val vault =
        desktopSecretVault(
            directory,
            service = "BoarderLess GIPHY key",
            linuxAttribute = "boarderless-giphy-key",
            windowsFileName = "giphy-key.dpapi",
        ) ?: return null
    return VaultGiphyKeyStore(vault)
}
