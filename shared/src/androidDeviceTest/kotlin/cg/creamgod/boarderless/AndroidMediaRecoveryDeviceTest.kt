package cg.creamgod.boarderless

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.persistence.MediaRecoveryStore
import cg.creamgod.boarderless.data.persistence.committedMediaRecoverySettings
import java.io.File
import java.util.UUID
import kotlin.test.*

/** Real SharedPreferences commit and XML, not an APP process-restart or backend acceptance test. */
class AndroidMediaRecoveryDeviceTest {
    @Test fun checkedCommitPublishesReminderBeforeReturningAndDismissPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "boarderless-recovery-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val xml = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        try {
            val store = MediaRecoveryStore(committedMediaRecoverySettings(preferences))
            store.record("qa-user", "qa-workspace", "qa-asset")
            assertTrue(xml.isFile)
            assertTrue(xml.readText().contains("qa-asset"))
            val reopened = MediaRecoveryStore(committedMediaRecoverySettings(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
            assertEquals(listOf("qa-asset"), reopened.list("qa-user", "qa-workspace"))
            assertTrue(reopened.list("other-user", "qa-workspace").isEmpty())
            reopened.dismiss("qa-user", "qa-workspace", "qa-asset")
            assertFalse(xml.readText().contains("qa-asset"))
            assertTrue(store.list("qa-user", "qa-workspace").isEmpty())
        } finally {
            check(context.deleteSharedPreferences(name))
        }
    }
}
