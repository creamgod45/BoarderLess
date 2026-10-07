package cg.creamgod.boarderless

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID
import kotlin.test.*

/** READ-ONLY opt-in against the retained six-format QA scope. No upload, operation, identity,
 * production preferences or user files. Exercises native loaders, not visual/audio playback QA.
 */
class AndroidMediaNativeLoadLiveTest {
    @Test fun retainedAssetsDownloadDecodePrepareAndRelease() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit read-only Android live QA scope",
            args.getString("boarderlessMediaReadLive") == "true")
        val base = requireNotNull(args.getString("boarderlessMediaBase"))
        val user = requireNotNull(args.getString("boarderlessMediaUser"))
        val workspace = requireNotNull(args.getString("boarderlessMediaWorkspace"))
        require(base == "http://192.168.68.67:3000")
        require(user == "60e37ef6-1e7a-4066-898b-c1878ae84d33")
        require(workspace == "c47bf81e-2a79-4fb0-aea6-2d36d47ffac3")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.context.cacheDir, "qa-native-read-${UUID.randomUUID()}")
        check(root.mkdir())
        val gateway = BackendAssetTransferGateway(baseUrl = base)
        val repository = BackendWorkspaceRepository(base, SessionPreferences(InMemorySettings()).apply {
            userId = user; workspaceId = workspace
        })
        val previews = AndroidAssetPreviewLoader(root)
        try {
            val seed = WorkspaceSession(user, UUID.randomUUID().toString(), WorkspaceMemberRole.Viewer, 0L, 0L,
                Workspace(WorkspaceId(workspace), "QA"))
            val session = repository.refresh(seed) // GET-only; never openOrCreate fallback.
            withTimeout(120000) {
                for ((id, mime) in Images) {
                    val ticket = gateway.authorize(session, id)
                    assertEquals(mime, ticket.asset.mediaType)
                    assertEquals(AssetStatus.Ready, ticket.asset.status)
                    assertEquals(Url(base).host, Url(ticket.downloadUrl).host)
                    assertEquals(9000, Url(ticket.downloadUrl).port)
                    val image = previews.load(gateway, session, id)
                    assertEquals(ticket.asset.width, image.width)
                    assertEquals(ticket.asset.height, image.height)
                    assertTrue(root.listFiles()!!.isEmpty(), "Preview must remove verified temporary download")
                    report(id, "native-image-decoded")
                }
                val gif = loadAndroidGif(gateway, session, Gif, root)
                try {
                    assertTrue(gif.frameCount > 1)
                    var duration = 0L
                    repeat(gif.frameCount) { index ->
                        val frame = gif.frame(index)
                        assertEquals(48, frame.width); assertEquals(32, frame.height)
                        assertTrue(gif.durationMs(index) > 0)
                        duration += gif.durationMs(index)
                    }
                    assertEquals(1000L, duration)
                    assertTrue(root.listFiles()!!.isEmpty(), "GIF keeps decoded data, not its download file")
                    report(Gif, "native-gif-all-frames-decoded")
                } finally { gif.release(); gif.release() }
                for (id in Videos) {
                    val player = loadAndroidVideo(gateway, session, id, root, instrumentation.context)
                    try {
                        val prepared = player.state.value
                        assertEquals(64, prepared.width); assertEquals(48, prepared.height)
                        assertTrue(prepared.durationMs > 0)
                        assertFalse(prepared.failed); assertFalse(prepared.released)
                        assertTrue(prepared.muted); assertFalse(prepared.playing)
                        assertEquals(1, root.listFiles()!!.size, "Player owns one verified session directory")
                        player.setMuted(true)
                        player.setPlaying(false)
                        player.seekTo(prepared.durationMs / 2)
                        assertFalse(player.state.value.failed)
                        // No surface: this verifies MediaPlayer prepare/commands, not video/audio presentation.
                    } finally { player.release(); player.release() }
                    assertTrue(player.state.value.released)
                    assertFalse(player.state.value.playing)
                    assertTrue(root.listFiles()!!.isEmpty(), "Released player must remove its verified file")
                    report(id, "native-video-prepared-seek-command-released")
                }
            }
            val after = repository.refresh(session)
            assertEquals(session.workspaceVersion, after.workspaceVersion)
            assertEquals(session.lastServerSeq, after.lastServerSeq)
            assertEquals(session.workspace, after.workspace)
        } finally {
            previews.clear(); repository.close(); gateway.close()
            // Never recursively remove an existing cache root or conceal failed loader cleanup.
            check(root.listFiles()!!.isEmpty()) { "Native loader left fixture cache files" }
            check(root.delete())
        }
    }

    private fun report(id: String, result: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0,
        Bundle().apply { putString("qaAssetId", id); putString("qaNativeResult", result) })

    private companion object {
        val Images = listOf(
            "92d842e8-d11a-4467-9e59-b36694eb218c" to "image/jpeg",
            "8e374c2c-a25c-4ce2-93e9-3f01b08990c8" to "image/webp",
            "9adbf731-acfe-48db-92d9-6d0aedcd885b" to "image/png", // GIF poster, not prior PNG upload case.
            "b5cbc2d0-0723-4c8e-a801-6cd81452c18c" to "image/jpeg",
            "d26a2024-6c19-4e34-ae30-074f76be6a9d" to "image/jpeg",
        )
        const val Gif = "fa7a6188-fcbf-48ce-b05d-44cff16acaa7"
        val Videos = listOf("0c124f64-fdc3-4d54-a536-faff2a64623f", "20adddf8-e685-432a-9142-f8ab992b2019")
    }
}
