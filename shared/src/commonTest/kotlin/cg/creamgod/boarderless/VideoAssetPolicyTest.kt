package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.*

class VideoAssetPolicyTest {
    private val session = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Video"))
    private fun ticket() = AssetDownloadTicket(WorkspaceAsset("video", session.workspace.id, "owner", "video/mp4", 100,
        "sha256:" + "a".repeat(64), 100, 100, null, AssetStatus.Ready, "2026-10-02"), "https://storage.invalid/video")
    @Test fun viewerCanAuthorizeBothVideoContainersAtUploadSizeLimit() {
        listOf("video/mp4", "video/webm").forEach { mime ->
            val ticket = ticket()
            VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(mediaType = mime, byteSize = MaxWorkspaceAssetBytes)), session, "video")
        }
    }
    @Test fun foreignWorkspaceWrongReferenceAndNonreadyReject() {
        val ticket = ticket()
        assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket, session, "other") }
        assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(workspaceId = WorkspaceId("other"))), session, "video") }
        AssetStatus.entries.filter { it != AssetStatus.Ready }.forEach { status ->
            assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(status = status)), session, "video") }
        }
    }
    @Test fun invalidSizeHashOrMimeRejectsBeforeNativePlayerOpens() {
        val ticket = ticket()
        listOf(0L, MaxWorkspaceAssetBytes + 1).forEach { size ->
            assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(byteSize = size)), session, "video") }
        }
        listOf("sha256:wrong", "", "md5:" + "a".repeat(64)).forEach { hash ->
            assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(checksum = hash)), session, "video") }
        }
        assertFailsWith<IllegalArgumentException> { VideoAssetPolicy.validate(ticket.copy(asset = ticket.asset.copy(mediaType = "image/png")), session, "video") }
    }
    @Test fun playbackDefaultsAreLocalPausedAndMuted() {
        val state = VideoPlaybackState()
        assertFalse(state.playing); assertTrue(state.muted)
        assertFalse(state.ended); assertFalse(state.failed); assertFalse(state.released)
        assertEquals(0L, state.positionMs); assertEquals(0L, state.durationMs)
    }
}
