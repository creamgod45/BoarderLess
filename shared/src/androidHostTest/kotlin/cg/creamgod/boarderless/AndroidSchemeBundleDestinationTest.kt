package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.runBlocking
import org.kotlincrypto.hash.sha2.SHA256
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.*

class AndroidSchemeBundleDestinationTest {
    @Test fun originalsLargerThanCanvasBudgetStreamToDiskAndReviewAsPortableBundle() = runBlocking {
        val cache = Files.createTempDirectory("android-bundle-stage-").toFile()
        val target = Files.createTempFile("android-bundle-document-", ".blscheme").toFile()
        try {
            // Deliberately opaque original bytes: this tests the bundle transport, not an image decoder.
            val chunk = ByteArray(1024 * 1024) { (it % 251).toByte() }
            val sha = SHA256().also { digest -> repeat(33) { digest.update(chunk) } }.digest()
            val checksum = "sha256:" + sha.joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
            val owner = WorkspaceSession("owner","client",WorkspaceMemberRole.Owner,0,0,Workspace(WorkspaceId("source"),"Source"))
            val asset = WorkspaceAsset("original",owner.workspace.id,"owner","image/png",33L*chunk.size,checksum,1,1,null,AssetStatus.Ready,"now")
            val payload = ClipboardPayload(nodes=emptyList(), media=listOf(ClipboardMedia("m",0f,0f,10f,10f,0f,asset.id,"image")))
            val scheme = QuickScheme(3,"跨裝置🙂",ClipboardJson.encodeToString(ClipboardPayload.serializer(),payload),6,"source")
            var opened = false; var discarded = false
            val document = object : AndroidCanvasExportDocument {
                override fun isBlank() = target.length() == 0L
                override fun openOutput() = target.outputStream().also { opened=true }
                override suspend fun discardBlank() { check(isBlank()); discarded=true; assertTrue(target.delete()) }
            }
            val stage = StagedAndroidCanvasExportDestination(cache,{true},document,QuickSchemeBundleCodec.MaximumBundleBytes)
            val destination = androidSchemeBundleDestination(stage)
            val gateway = object : AssetDownloadGateway {
                override suspend fun authorize(session:WorkspaceSession,assetId:String):AssetDownloadTicket {
                    assertEquals(owner,session); assertEquals(asset.id,assetId)
                    return AssetDownloadTicket(asset,"https://private.invalid/signed")
                }
                override suspend fun download(ticket:AssetDownloadTicket,onChunk:suspend (ByteArray)->Unit) {
                    repeat(33) { onChunk(chunk); assertFalse(opened) }
                }
            }
            QuickSchemeBundleCodec.export(scheme,owner,gateway,destination,{true},{scheme})
            assertTrue(opened); assertFalse(discarded); assertTrue(target.length()>33L*chunk.size)
            assertEquals(0,cache.listFiles()!!.size)
            val source=object : SchemeBundleSource {
                override val byteSize=target.length()
                override suspend fun readChunk(offset:Long,maximumBytes:Int):ByteArray = RandomAccessFile(target,"r").use { file ->
                    file.seek(offset)
                    ByteArray(minOf(maximumBytes.toLong(),byteSize-offset).toInt()).also(file::readFully)
                }
                override suspend fun release() = Unit
            }
            val review=QuickSchemeBundleCodec.review(source) { true }
            assertEquals(scheme.copy(id=0),review.scheme)
            assertEquals(listOf(asset.id),review.assets.map { it.sourceId })
            assertEquals(checksum,review.assets.single().checksum)
            destination.abort(); assertTrue(target.exists()); assertFalse(discarded)
            review.release()
        } finally { cache.deleteRecursively(); target.delete() }
    }

    @Test fun bundleCapacityIsSeparateFromCanvasBudgetAndScopeFailurePreventsPublication() = runBlocking {
        val cache=Files.createTempDirectory("android-bundle-stage-").toFile()
        try {
            var current=true; var opened=false; var discarded=false
            val doc=object : AndroidCanvasExportDocument {
                override fun isBlank()=true
                override fun openOutput():java.io.OutputStream { opened=true; error("must not publish") }
                override suspend fun discardBlank() { discarded=true }
            }
            val destination=androidSchemeBundleDestination(StagedAndroidCanvasExportDestination(cache,{current},doc,QuickSchemeBundleCodec.MaximumBundleBytes))
            repeat(33) { destination.writeChunk(ByteArray(1024*1024)) }
            current=false
            assertFailsWith<IllegalStateException> { destination.commit() }
            destination.abort()
            assertFalse(opened); assertTrue(discarded); assertEquals(0,cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }
}
