package cg.creamgod.boarderless

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.mediaNodeFromReadyAsset
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Test-only private content URI. No provider writes or arbitrary filesystem/path traversal. */
class AndroidMediaQaProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri): File {
        require(uri.pathSegments.size == 2 && uri.pathSegments[0] == "source")
        val name = uri.pathSegments[1]
        require(name.matches(Regex("[0-9a-f-]{36}\\.png")))
        return File(requireNotNull(context).cacheDir, name).also { require(it.isFile) }
    }
    override fun getType(uri: Uri): String { file(uri); return "image/png" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
        val source = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map<String, Any?> { when (it) {
            OpenableColumns.DISPLAY_NAME -> source.name
            OpenableColumns.SIZE -> source.length()
            else -> null
        } }.toTypedArray()) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = error("Read-only QA provider")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read-only QA provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read-only QA provider")
}

/** Opt-in Android real engine/content resolver/storage/worker/Node/sink test. Retains ONE new QA
 * asset and Node in the explicitly supplied QA workspace. No production prefs, user file or keys.
 * Ordinary instrumentation skips it. Does not prove native picker UI or process restart.
 */
class AndroidMediaBackendLiveTest {
    @Test fun contentUriUploadNodeReloadAndVerifiedNativeSink() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit Android live QA scope", arguments.getString("boarderlessMediaLive") == "true")
        val base = requireNotNull(arguments.getString("boarderlessMediaBase"))
        val user = requireNotNull(arguments.getString("boarderlessMediaUser"))
        val workspace = requireNotNull(arguments.getString("boarderlessMediaWorkspace"))
        require(base == "http://192.168.68.67:3000") // Explicit local acceptance target, not a production default.
        UUID.fromString(user); UUID.fromString(workspace)
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = Base64.decode(Png)
        assertEquals(442, bytes.size)
        assertEquals(Checksum, hash(bytes))
        val file = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val downloads = File(context.cacheDir, "qa-download-${UUID.randomUUID()}")
        check(downloads.mkdir())
        var source: AndroidUriAssetTransferSource? = null
        val gateway = BackendAssetTransferGateway(baseUrl = base)
        val repository = repository(base, user, workspace)
        try {
            file.writeBytes(bytes) // Fixture-owned file only, independent of the user's documents.
            val uri = Uri.parse("content://${context.packageName}.qa-media/source/${file.name}")
            val selected = AndroidUriAssetTransferSource.fromUri(context, uri)
            source = selected
            assertEquals("image/png", selected.mediaType)
            assertEquals(64, selected.width); assertEquals(48, selected.height)
            assertEquals(Checksum, selected.checksum)
            val seed = WorkspaceSession(user, UUID.randomUUID().toString(), WorkspaceMemberRole.Viewer, 0L, 0L,
                Workspace(WorkspaceId(workspace), "QA"))
            var session = repository.refresh(seed) // GET-only: no open/create fallback or identity writes.
            val stages = mutableListOf<AssetImportStage>()
            val imported = withTimeout(90000) { AssetImportCoordinator(gateway).import(session, selected) { stages += it } }
            assertEquals(AssetStatus.Ready, imported.asset.status)
            assertTrue(stages.any { it == AssetImportStage.Uploading(0, 442) })
            assertIs<AssetImportStage.Ready>(stages.last())
            val ticket = gateway.authorize(session, imported.asset.id)
            assertEquals(Url(base).host, Url(ticket.downloadUrl).host)
            assertEquals(9000, Url(ticket.downloadUrl).port)
            val node = mediaNodeFromReadyAsset(imported.asset, session.workspace.id, CanvasObjectId(UUID.randomUUID().toString()),
                Vec2(0f, 0f), 1f, (session.workspace.objects.values.maxOfOrNull { it.zIndex } ?: 0L) + 1, "Android live QA PNG")
            assertIs<SubmitOutcome.Accepted>(repository.submit(session,
                CreateObjectsOperation("android-qa-${UUID.randomUUID()}", listOf(node))))
            session = repository.refresh(session)
            assertEquals(imported.asset.id, (session.workspace.objectById(node.id) as MediaNode).assetId)
            val reopened = repository(base, user, workspace)
            try {
                val reloaded = reopened.refresh(seed)
                assertEquals(imported.asset.id, (reloaded.workspace.objectById(node.id) as MediaNode).assetId)
                val sink = AndroidFileAssetDownloadSink.create(downloads, "image/png")
                val reference = withTimeout(30000) { AssetDownloadCoordinator(gateway).download(reloaded, imported.asset.id, sink) }
                assertContentEquals(bytes, File(reference.token).readBytes())
                assertEquals(1, downloads.listFiles()!!.size)
                val bitmap = AndroidAssetPreviewLoader.decodeBytes(File(reference.token).readBytes())
                assertEquals(64, bitmap.width); assertEquals(48, bitmap.height)
            } finally { reopened.close() }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("qaAssetId", imported.asset.id); putString("qaNodeId", node.id.value)
                putString("qaStorageOrigin", "http://${Url(ticket.downloadUrl).host}:${Url(ticket.downloadUrl).port}")
            })
        } finally {
            source?.release(); repository.close(); gateway.close()
            check(!file.exists() || file.delete())
            downloads.listFiles()?.forEach { check(it.isFile && it.delete()) }
            check(downloads.delete()) // Only this fixture's flat unique directory, never a cache root.
        }
    }
    private fun repository(base: String, user: String, workspace: String) = BackendWorkspaceRepository(base,
        SessionPreferences(InMemorySettings()).apply { userId = user; workspaceId = workspace })
    private fun hash(bytes: ByteArray) = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object {
        const val Checksum = "sha256:15b8da68f777d7caaab816ede7dc7364cc979cc9bffaab4e93d39e24b624d49d"
        const val Png = "iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAACXBIWXMAAAABAAAAAQBPJcTWAAABbElEQVR4nO2asXLCMAxApTvuYCtjV7Z+ZvsH/Q3+ipGxbDC5TpQWbEuJA0nk+PTOAziJrId15hIHQcAJ/dgecBgfRymO44+wvT5sEnggn43Q34c4yMtgYzsu+DgBdNIPOhkOkQbKPH+EQH7Q1/EamcPlCiyZPZHpkCWwfPZEjsOwgFb2xKDDgIBu9kS/wzPLaFH0CZTw8xPN2iokIwqUkz3R1hGTUqUlNP8f7jOwk1DjDLClVgjpJNQ4A+siFii5foioiqqbgdVhAtqYgDYmoI0JaGMC2piANrGAdPNfDnZHVhiMQMlVxDxWaTrek3ZE9/f5+gZnuLdPF3z17QZ7LkTXEJILwra7BBd8JSG28OPP66Lgd3R9jSW0LkxAGxPQZtM8RqcVKeR/vd0BHB5WX+m5e9+rBrQcCudfAU5tY+O3+yx+md47+OAFhHHDJLK3nadlsn1i0HCYeKcelnWY5V0J6BxmfFsFxm8u/gKajJ/9ym/TPAAAAABJRU5ErkJggg=="
    }
}
