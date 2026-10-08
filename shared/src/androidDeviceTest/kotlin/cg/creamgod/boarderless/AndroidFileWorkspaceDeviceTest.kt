package cg.creamgod.boarderless

import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.persistence.AndroidFileWorkspaceRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import kotlin.test.*

class AndroidFileWorkspaceDeviceTest {
    @Test fun actualNativeRecordReopensCanvasGeometryBackgroundAndSaveReceipts() = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir.canonicalFile, "local-documents-test-${UUID.randomUUID()}")
        try { verifyNativeLocalDocumentReopen { AndroidFileWorkspaceRepository(root) } }
        finally { root.deleteRecursively() }
    }
}
