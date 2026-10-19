@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.IosFileWorkspaceRepository
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import kotlin.test.*

class IosFileWorkspaceRepositoryTest {
    @Test fun actualNativeRecordReopensCanvasGeometryBackgroundAndSaveReceipts() = runBlocking {
        val root = checkNotNull(NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path).trimEnd('/') + "/boarderless-documents-${NSUUID().UUIDString}"
        try { verifyNativeLocalDocumentReopen { IosFileWorkspaceRepository(root) } }
        finally { NSFileManager.defaultManager.removeItemAtPath(root, null) }
    }
}
