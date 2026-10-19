package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class DesktopAiSettingsStoreTest {
    private class Recording(
        val reply: ProcessResult? = ProcessResult(0, ""),
    ) : ProcessRunner {
        val calls = mutableListOf<Pair<List<String>, String?>>()

        override fun run(
            command: List<String>,
            stdin: String?,
        ): ProcessResult? {
            calls += command to stdin
            return reply
        }
    }

    @Test fun macKeychainNeverPutsTheSecretInArguments() {
        val runner = Recording()
        assertTrue(MacKeychainVault(runner, "svc").write("sk-Secret\"\\ value"))
        val (command, stdin) = runner.calls.single()
        assertEquals(listOf("/usr/bin/security", "-i"), command)
        assertFalse(command.any { "Secret" in it })
        assertTrue(stdin!!.startsWith("add-generic-password -a boarderless -s \"svc\" -U -X "))
        assertFalse("Secret" in stdin, "secret must be hex encoded on stdin")
    }

    @Test fun settingsFileNeverContainsTheToken() =
        runBlocking {
            val dir = Files.createTempDirectory("ai-settings")
            val vault =
                object : DesktopSecretVault {
                    var secret: String? = null

                    override fun available() = true

                    override fun read() = secret

                    override fun write(secret: String): Boolean {
                        this.secret = secret
                        return true
                    }

                    override fun delete(): Boolean {
                        secret = null
                        return true
                    }
                }
            val store = DesktopAiSettingsStore(dir, vault)
            assertTrue(store.saveSettings(AiSavedSettings(endpoint = "https://x.test/v1", model = "m")))
            assertTrue(store.saveToken("sk-TOP-SECRET"))
            assertEquals("m", store.loadSettings()?.model)
            assertEquals("sk-TOP-SECRET", store.loadToken())
            assertFalse(Files.readString(dir.resolve("ai-settings.json")).contains("TOP-SECRET"))
            assertTrue(store.clearToken())
            assertNull(store.loadToken())
        }

    @Test fun withoutVaultTokensAreRefusedNotWrittenToDisk() =
        runBlocking {
            val dir = Files.createTempDirectory("ai-settings")
            val store = DesktopAiSettingsStore(dir, null)
            assertFalse(store.tokenProtected)
            assertFalse(store.saveToken("sk-secret"))
            assertNull(store.loadToken())
            assertFalse(Files.list(dir).use { it.anyMatch { path -> Files.readString(path).contains("sk-secret") } })
        }

    @Test fun realMacKeychainRoundTripUsesAnIsolatedService() {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return
        val vault = MacKeychainVault(SystemProcessRunner, "BoarderLess AI token test ${System.nanoTime()}")
        try {
            assertTrue(vault.write("sk-roundtrip\"quoted\\value"))
            assertEquals("sk-roundtrip\"quoted\\value", vault.read())
            assertTrue(vault.write("sk-second"))
            assertEquals("sk-second", vault.read())
        } finally {
            assertTrue(vault.delete())
        }
        assertNull(vault.read())
    }
}
