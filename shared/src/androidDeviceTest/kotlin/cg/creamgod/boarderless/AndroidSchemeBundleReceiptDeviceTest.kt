package cg.creamgod.boarderless

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.SchemeBundleAsset
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import kotlin.test.*

/** Native Linux rename/fsync, private modes and locks; gateway status is a controlled fixture. */
class AndroidSchemeBundleReceiptDeviceTest {
    private fun root()=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir.canonicalFile,"bundle-receipt-test-${UUID.randomUUID()}")
    @Test fun nativeCasReopensWithPrivateModesAndRejectsStaleOrCorruptRecords() {
        val root=root(); val key="a".repeat(64)
        try {
            val first=AndroidAtomicLocalRecordStore(root)
            first.compareAndSet(key,null,"{\"first\":true}".encodeToByteArray())
            val reopened=AndroidAtomicLocalRecordStore(root)
            val read=assertNotNull(reopened.read(key))
            assertEquals(1L,read.generation); assertContentEquals("{\"first\":true}".encodeToByteArray(),read.payload)
            assertEquals(0x1c0,Os.lstat(root.path).st_mode and 0x1ff)
            assertEquals(0x180,Os.lstat(File(root,"$key.receipt").path).st_mode and 0x1ff)
            reopened.confirmDurable(key,1)
            reopened.compareAndSet(key,1,"{\"second\":true}".encodeToByteArray())
            assertFailsWith<IllegalStateException> { first.compareAndSet(key,1,"stale".encodeToByteArray()) }
            assertEquals(2L,first.read(key)?.generation)
            val file=File(root,"$key.receipt")
            file.writeText(file.readText().replace("second","broken"))
            assertFailsWith<IllegalArgumentException> { reopened.read(key) }
            assertFailsWith<IllegalArgumentException> { reopened.compareAndSet(key,null,"{}".encodeToByteArray()) }
            assertFalse(root.listFiles()!!.any { it.name.endsWith(".partial") })
        } finally { root.deleteRecursively() }
    }
    @Test fun pendingIdentitySurvivesReopenAndReadyRecoveryNeverAllocatesAnotherId()=runBlocking {
        val root=root()
        val owner=WorkspaceSession("u","c",WorkspaceMemberRole.Owner,0,0,Workspace(WorkspaceId("w"),"Destination"))
        val asset=SchemeBundleAsset("source","image/png",81,"sha256:"+"b".repeat(64),1,1,null)
        val ready=WorkspaceAsset("destination",owner.workspace.id,"u",asset.mediaType,asset.byteSize,asset.checksum,1,1,null,AssetStatus.Ready,"now")
        try {
            fun receipts()=PersistentSchemeBundleImportReceipts(AndroidAtomicLocalRecordStore(root),owner,"a".repeat(64),listOf(asset))
            receipts().beforeComplete(asset,"destination")
            var current=ready.copy(status=AssetStatus.Pending); var calls=0
            val repository=object : AssetRepository {
                override suspend fun listAssets(session:WorkspaceSession)=error("must query exact id")
                override suspend fun getAsset(session:WorkspaceSession,assetId:String):WorkspaceAsset {
                    assertEquals(owner,session); assertEquals("destination",assetId); calls++; return current
                }
            }
            val reopened=receipts()
            assertFailsWith<IllegalArgumentException> { reopened.recovered(asset,repository) }
            assertFailsWith<IllegalArgumentException> { reopened.beforeComplete(asset,"another") }
            current=ready
            val recovered=assertNotNull(reopened.recovered(asset,repository))
            reopened.ready(asset,recovered)
            receipts().beforeComplete(asset,"destination") // Idempotent force, cannot downgrade ready.
            assertEquals(recovered,receipts().recovered(asset,repository))
            assertFailsWith<IllegalArgumentException> { reopened.ready(asset,recovered.copy(asset=ready.copy(width=2))) }
            current=ready.copy(ownerId="foreign")
            assertFailsWith<IllegalArgumentException> { receipts().recovered(asset,repository) }
            assertEquals(4,calls)
        } finally { root.deleteRecursively() }
    }
    @Test fun danglingReceiptAndForeignPermissionsFailClosed() {
        val root=root(); val key="a".repeat(64)
        try {
            val store=AndroidAtomicLocalRecordStore(root)
            assertNull(store.read(key))
            val file=File(root,"$key.receipt")
            Os.symlink(File(root,"missing").path,file.path)
            assertFailsWith<IllegalStateException> { store.read(key) }
            assertFailsWith<IllegalStateException> { store.compareAndSet(key,null,"{}".encodeToByteArray()) }
            assertTrue(file.delete())
            Os.chmod(root.path,0x1ff)
            assertFailsWith<IllegalStateException> { store.read(key) }
            Os.chmod(root.path,0x1c0)
        } finally { root.deleteRecursively() }
    }
}
