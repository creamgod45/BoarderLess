package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.MediaRecoveryStore
import cg.creamgod.boarderless.data.persistence.flushedMediaRecoverySettings
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MediaRecoveryPreferencesTest {
    @Test fun flushFailurePreservesLegacyEvidenceAndDoesNotReportSaved() {
        val node = Preferences.userRoot().node("boarderless-test-${UUID.randomUUID()}")
        try {
            val legacy = "mediaRecovery.v1:1:u:1:w"
            node.put(legacy, "[\"a\"]")
            node.flush()
            val store =
                MediaRecoveryStore(
                    flushedMediaRecoverySettings(node) {
                        throw BackingStoreException("Fixture flush failure")
                    },
                )
            assertFailsWith<BackingStoreException> { store.record("u", "w", "b") }
            assertEquals("[\"a\"]", node.get(legacy, null))
            // The new value may be in memory despite an unknown flush: never roll it back.
            assertEquals(listOf("a", "b"), store.list("u", "w"))
        } finally {
            node.removeNode()
            node.flush()
        }
    }

    @org.junit.Test(timeout = 30000L)
    fun flushedRecordAndDismissSurviveHaltAndFreshJvm() {
        val name = "boarderless-test-${UUID.randomUUID()}"
        val children = mutableListOf<Process>()
        try {
            for (mode in listOf("record", "read-dismiss", "read-empty")) {
                val child =
                    ProcessBuilder(
                        java.nio.file.Path
                            .of(System.getProperty("java.home"), "bin", "java")
                            .toString(),
                        "-cp",
                        checkNotNull(System.getProperty("boarderless.test.classpath")),
                        MediaRecoveryProcessFixture::class.java.name,
                        name,
                        mode,
                    ).redirectErrorStream(true).start()
                children += child
                assertTrue(child.waitFor(8, TimeUnit.SECONDS), "Recovery fixture JVM timed out")
                assertEquals(0, child.exitValue(), "Recovery fixture JVM failed")
                assertTrue(
                    child.inputStream
                        .bufferedReader()
                        .readText()
                        .lineSequence()
                        .any { it == "PASS:$mode" },
                )
            }
        } finally {
            children.forEach {
                if (it.isAlive) {
                    it.destroyForcibly()
                    it.waitFor(8, TimeUnit.SECONDS)
                }
            }
            // Exact unique test node only; no production preference keys are touched.
            Preferences.userRoot().node(name).let {
                it.sync()
                it.removeNode()
                it.flush()
            }
        }
    }

    @Test fun realDesktopPreferencesAcceptUuidUserAndWorkspaceScope() {
        val node = Preferences.userRoot().node("boarderless-test-${UUID.randomUUID()}")
        try {
            val settings = PreferencesSettings(node)
            val user = "11111111-1111-4111-8111-111111111111"
            val workspace = "22222222-2222-4222-8222-222222222222"
            val asset = "33333333-3333-4333-8333-333333333333"
            MediaRecoveryStore(settings).record(user, workspace, asset)
            assertTrue(node.keys().all { it.length <= Preferences.MAX_KEY_LENGTH })
            assertEquals(listOf(asset), MediaRecoveryStore(settings).list(user, workspace))
            MediaRecoveryStore(settings).dismiss(user, workspace, asset)
            assertTrue(MediaRecoveryStore(settings).list(user, workspace).isEmpty())
        } finally {
            node.removeNode()
        }
    }
}

object MediaRecoveryProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        require(args[0].startsWith("boarderless-test-"))
        UUID.fromString(args[0].removePrefix("boarderless-test-"))
        val store = MediaRecoveryStore(flushedMediaRecoverySettings(Preferences.userRoot().node(args[0])))
        when (args[1]) {
            "record" -> {
                store.record("qa-user", "qa-workspace", "qa-asset")
            }

            "read-dismiss" -> {
                check(store.list("qa-user", "qa-workspace") == listOf("qa-asset"))
                check(store.list("other-user", "qa-workspace").isEmpty())
                store.dismiss("qa-user", "qa-workspace", "qa-asset")
            }

            "read-empty" -> {
                check(store.list("qa-user", "qa-workspace").isEmpty())
            }

            else -> {
                error("Unknown recovery fixture mode")
            }
        }
        println("PASS:${args[1]}")
        System.out.flush()
        // No orderly JVM shutdown hook may provide the persistence guarantee for this test.
        Runtime.getRuntime().halt(0)
    }
}
