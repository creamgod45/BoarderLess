package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.VaultGiphyKeyStore
import cg.creamgod.boarderless.data.ai.DesktopSecretVault
import cg.creamgod.boarderless.data.ai.LibsecretVault
import cg.creamgod.boarderless.data.ai.MacKeychainVault
import cg.creamgod.boarderless.data.ai.ProcessResult
import cg.creamgod.boarderless.data.ai.ProcessRunner
import cg.creamgod.boarderless.data.ai.desktopSecretVault
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopGiphyKeyStoreTest {
    private class MemoryVault : DesktopSecretVault {
        var value: String? = null
        override fun available() = true
        override fun read() = value
        override fun write(secret: String): Boolean { value = secret; return true }
        override fun delete(): Boolean { value = null; return true }
    }

    @Test fun saveLoadReplaceAndClearUseTheVault() = runTest {
        val store = VaultGiphyKeyStore(MemoryVault())
        assertNull(store.load())
        assertTrue(store.save("first-中文"))
        assertEquals("first-中文", store.load())
        assertTrue(store.save("second"))
        assertEquals("second", store.load())
        assertTrue(store.clear())
        assertNull(store.load())
    }

    @Test fun vaultFailuresNeverEscapeOrReportSuccess() = runTest {
        val broken = object : DesktopSecretVault {
            override fun available() = true
            override fun read(): String = error("locked")
            override fun write(secret: String): Boolean = error("locked")
            override fun delete(): Boolean = error("locked")
        }
        val store = VaultGiphyKeyStore(broken)
        assertNull(store.load())
        assertFalse(store.save("x"))
        assertFalse(store.clear())
    }

    @Test fun giphyKeyAndAiTokenUseDifferentVaultEntries() {
        val seen = mutableListOf<List<String>>()
        val runner = ProcessRunner { command, _ -> seen += command; ProcessResult(0, "") }
        MacKeychainVault(runner, "BoarderLess GIPHY key").read()
        LibsecretVault(runner, "boarderless-giphy-key", "BoarderLess GIPHY key").read()
        LibsecretVault(runner).read()
        assertTrue("BoarderLess GIPHY key" in seen[0])
        assertTrue("boarderless-giphy-key" in seen[1])
        assertTrue("boarderless-ai-token" in seen[2])
        val directory = Files.createTempDirectory("giphy-vault")
        try {
            val windows = desktopSecretVault(directory, runner, "Windows 11", windowsFileName = "giphy-key.dpapi")
            assertTrue(windows == null || windows.javaClass.simpleName.contains("Dpapi"))
        } finally {
            Files.deleteIfExists(directory)
        }
    }
}
