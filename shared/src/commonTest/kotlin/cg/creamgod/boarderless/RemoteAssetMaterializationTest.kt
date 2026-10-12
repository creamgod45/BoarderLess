package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

internal class MaterializationTestServer(private val local: CanvasWorkspaceRepository,
    var current: WorkspaceSession, var asset: WorkspaceAsset, var bytes: ByteArray) : CanvasWorkspaceRepository by local, AssetDownloadGateway {
    override val syncServerScope = "a".repeat(64)
    override val downloadServerScope = syncServerScope
    var authorizations = 0; var downloads = 0; var refreshes = 0
    var denyAt = Int.MAX_VALUE; var failAfterChunks = Int.MAX_VALUE
    var chunkBytes = 73 * 1024
    var afterChunk: suspend () -> Unit = {}
    override suspend fun refresh(session: WorkspaceSession): WorkspaceSession { refreshes++; return current }
    override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
        authorizations++; check(authorizations < denyAt); require(assetId == asset.id)
        return AssetDownloadTicket(asset, "https://example.invalid/original")
    }
    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
        downloads++; var offset = 0; var chunks = 0
        while (offset < bytes.size) {
            check(chunks < failAfterChunks) { "Controlled transport failure" }
            val end = minOf(offset + chunkBytes, bytes.size)
            onChunk(bytes.copyOfRange(offset, end)); offset = end; chunks++; afterChunk()
        }
    }
}

internal suspend fun materializationTestSetup(local: CanvasWorkspaceRepository, bytes: ByteArray): Pair<LocalWorkspaceSyncSnapshot, MaterializationTestServer> {
    val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
    val checksum = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    val asset = WorkspaceAsset("remote-original", binding.workspaceId, "another-creator", "image/png", bytes.size.toLong(), checksum,
        1, 1, null, AssetStatus.Ready, "2026-10-11T00:00:00Z")
    val reviewed = savedRemoteMediaReview(local, binding, asset)
    val mapping = newLocalAssetMaterialization(reviewed, asset, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
    val saved = local.localSyncStore!!.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping)
    return saved to MaterializationTestServer(local, reviewed.proposal!!.remoteSession(), asset, bytes)
}

class RemoteAssetMaterializationTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null; var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    private class Originals : PinnedLocalAssetGateway, AssetDownloadGateway {
        var asset: WorkspaceAsset? = null; var bytes = ByteArray(0)
        var prepares = 0; var uploads = 0; var confirms = 0; var maxRead = 0; var loseConfirmAck = false
        override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket = error("Must use saved identity")
        override suspend fun preparePinned(session: WorkspaceSession, pending: WorkspaceAsset): AssetUploadTicket {
            prepares++; if (asset == null) asset = pending
            require(asset == pending || asset == pending.copy(status = AssetStatus.Ready))
            return AssetUploadTicket(asset!!, "local-fixture")
        }
        override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) {
            uploads++; val chunks = mutableListOf<ByteArray>(); var offset = 0L
            while (offset < source.byteSize) {
                val part = source.readChunk(offset, minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt())
                maxRead = maxOf(maxRead, part.size); chunks.add(part); offset += part.size; onProgress(offset)
            }
            bytes = chunks.fold(ByteArray(0)) { total, part -> total + part } // Fixture only, not production buffering.
        }
        override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset {
            confirms++; asset = ticket.asset.copy(status = AssetStatus.Ready)
            if (loseConfirmAck) { loseConfirmAck = false; error("Controlled unknown confirmation") }
            return asset!!
        }
        override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit) = asset!!
        override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? = null
        override suspend fun abandon(session: WorkspaceSession, assetId: String) = error("Must retain materialization identity")
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(asset!!, "local-fixture")
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            var offset = 0
            while (offset < bytes.size) { val end = minOf(offset + DefaultAssetUploadChunkBytes, bytes.size); onChunk(bytes.copyOfRange(offset, end)); offset = end }
        }
    }
    private suspend fun fixture(): Triple<CanvasWorkspaceRepository, LocalWorkspaceSyncSnapshot, Pair<Originals, MaterializationTestServer>> {
        val documents = AwaitedLocalWorkspaceRepository(Records()) { "2026-10-11" }; val originals = Originals()
        val local = object : CanvasWorkspaceRepository by documents { override val localAssetGateway = originals }
        val (saved, server) = materializationTestSetup(local, ByteArray(2 * DefaultAssetUploadChunkBytes + 7) { (it % 251).toByte() })
        return Triple(local, saved, originals to server)
    }
    @Test fun verifiedTargetPublicationReopensExactLocalUndoAndRetriesUnknownAckWithoutNewGeneration() = runTest {
        val records = Records(); val documents = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }; val originals = Originals()
        val local = object : CanvasWorkspaceRepository by documents { override val localAssetGateway = originals }
        val (saved, server) = materializationTestSetup(local, byteArrayOf(1, 2, 3))
        val mapping = saved.materializations.single()
        materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true })
        val verified = verifyMaterializedTarget(local, saved, server, server.current, server, { true })
        val operation = cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation("apply-mapped", saved.local.workspace, verified.localTarget)
        records.failConfirm = true
        assertFails { documents.applyMaterializedSyncProposal(verified, operation) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(records) { "2026-10-11" }
        val authorizations = server.authorizations
        val recovery = verifyMaterializedApplyOrRecovery(local, saved, operation, server, server.current, server, { true })
        assertEquals(authorizations, server.authorizations) // Confirm already-published result, not another transfer/auth.
        val applied = reopened.applyMaterializedSyncProposal(recovery, operation)
        assertEquals(generation, records.value!!.generation)
        assertEquals(saved.proposal, applied.snapshot.proposal); assertEquals(saved.checkpoint, applied.snapshot.checkpoint)
        assertEquals(mapping.localAssetId, (applied.snapshot.local.workspace.objects.values.single() as MediaNode).assetId)
        assertEquals(saved.materializations, applied.snapshot.applied!!.materializations)
        assertTrue(applied.snapshot.appliedIsCurrent)
        assertEquals(LocalWorkspaceSyncComparison.Unchanged, compareLocalWorkspaceSync(applied.snapshot, server.current))
        val settled = reopened.settleReadOnlySyncProposal(applied.snapshot.local, saved.proposal!!.id, server.current)
        assertFalse(settled.hasPendingContent)
        assertEquals(saved.proposal, settled.proposal)
        assertTrue(settled.proposalIsSettled)
        val history = restoredLocalSyncHistory(settled)
        assertTrue(history.canUndo)
        val undo = history.undo(); assertTrue(undo.succeeded); assertTrue(undo.history.workspace.objects.isEmpty())
        reopened.retainDraftAwaited(settled.local, settled.local.workspace, undo.appliedOperation!!)
        val undone = reopened.readSyncSnapshot(applied.snapshot.local)
        assertTrue(undone.local.workspace.objects.isEmpty()); assertEquals(saved.materializations, undone.materializations)
        val redo = undo.history.redo(); assertTrue(redo.succeeded)
        reopened.retainDraftAwaited(undone.local, undone.local.workspace, redo.appliedOperation!!)
        val redone = reopened.readSyncSnapshot(undone.local)
        assertEquals(mapping.localAssetId, (redone.local.workspace.objects.values.single() as MediaNode).assetId)
        assertFails { reopened.applyMaterializedSyncProposal(verified, operation) } // Later history must not be overwritten.
        val plan = LocalWorkspaceSyncMergePlan(redone, server.current)
        val renewed = plan.proposal("next-review", plan.rows.associate { it.id to LocalSyncMergeChoice.Remote })
        val reviewedAgain = reopened.stageSyncProposal(redone.local, redone.checkpoint!!, saved.proposal!!.id, renewed)
        assertEquals(1, reviewedAgain.superseded.size)
        assertEquals(saved.materializations, reviewedAgain.superseded.single().applied.materializations)
        assertEquals(saved.materializations, reopened.readSyncSnapshot(reviewedAgain.local).materializations)
    }
    @Test fun materializedPublicationRejectsChangedTargetOrStaleLocalStateWithoutWrites() = runTest {
        val (local, saved, services) = fixture(); val (_, server) = services
        materializeRemoteAsset(local, saved.local, server, server.current, server, saved.materializations.single(), { true })
        val verified = verifyMaterializedTarget(local, saved, server, server.current, server, { true })
        val operation = cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation("apply-mapped", saved.local.workspace, verified.localTarget)
        assertFails { local.localSyncStore!!.applyMaterializedSyncProposal(verified, operation.copy(after = verified.localTarget.copy(title = "Forged"))) }
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
        local.retainDraftAwaited(saved.local, saved.local.workspace,
            cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation("later-edit", saved.local.workspace, saved.local.workspace.copy(title = "Later")))
        val later = local.localSyncStore!!.readSyncSnapshot(saved.local)
        assertFails { verifyMaterializedApplyOrRecovery(local, saved, operation, server, server.current, server, { true }) }
        assertFails { local.localSyncStore!!.applyMaterializedSyncProposal(verified, operation) }
        assertEquals(later, local.localSyncStore!!.readSyncSnapshot(saved.local))
    }
    @Test fun boundedTransferRehashesLocalOriginalAndFreshlyAuthorizesEvenCacheReuse() = runTest {
        val (local, saved, services) = fixture(); val (originals, server) = services; val mapping = saved.materializations.single()
        val ready = materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true })
        assertEquals(mapping.localAssetId, ready.id); assertEquals(AssetStatus.Ready, ready.status)
        assertContentEquals(server.bytes, originals.bytes); assertTrue(originals.maxRead <= DefaultAssetUploadChunkBytes)
        assertEquals(2, server.authorizations); assertEquals(1, server.downloads)
        assertEquals(ready, materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }))
        assertEquals(4, server.authorizations); assertEquals(1, server.downloads); assertEquals(1, originals.uploads)
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
        originals.bytes[0] = (originals.bytes[0].toInt() xor 1).toByte()
        assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }) }
    }
    @Test fun targetVerificationUsesReadyOriginalWithoutRemoteTransferOrCanvasWrites() = runTest {
        val (local, saved, services) = fixture(); val (originals, server) = services
        val mapping = saved.materializations.single()
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { true }) }
        assertEquals(0, originals.prepares); assertEquals(0, server.downloads)
        materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true })
        val result = verifyMaterializedTarget(local, saved, server, server.current, server, { true })
        assertEquals(mapping.localAssetId, (result.localTarget.objects.values.single() as MediaNode).assetId)
        assertEquals(saved.proposal!!.target, MaterializedAssetProjection(saved.local, saved.checkpoint!!.binding, saved.materializations).toCanonical(result.localTarget))
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
        assertEquals(1, originals.prepares); assertEquals(1, originals.uploads); assertEquals(1, originals.confirms); assertEquals(1, server.downloads)
        originals.bytes[0] = (originals.bytes[0].toInt() xor 1).toByte()
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { true }) }
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
    }
    @Test fun targetVerificationRejectsRevokedMetadataScopeAndStaleReview() = runTest {
        val (local, saved, services) = fixture(); val (_, server) = services
        materializeRemoteAsset(local, saved.local, server, server.current, server, saved.materializations.single(), { true })
        server.denyAt = server.authorizations + 2 // Initial auth succeeds, final reauthorization fails.
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { true }) }
        server.denyAt = Int.MAX_VALUE
        val original = server.asset; server.asset = original.copy(width = 2)
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { true }) }
        server.asset = original
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { false }) }
        val current = server.current
        server.current = current.copy(lastServerSeq = current.lastServerSeq + 1)
        assertFails { verifyMaterializedTarget(local, saved, server, server.current, server, { true }) }
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local)); assertEquals(1, server.downloads)
    }
    @Test fun metadataAndAuthorizationRejectionNeverPrepareOrPublishAndScopeStopsFinalResult() = runTest {
        val (local, saved, services) = fixture(); val (originals, server) = services; val mapping = saved.materializations.single()
        val original = server.asset; server.asset = original.copy(width = 2)
        assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }) }
        assertEquals(0, originals.prepares)
        server.asset = original; server.denyAt = server.authorizations + 1
        assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }) }
        assertEquals(0, originals.prepares)
        server.denyAt = Int.MAX_VALUE; var active = true; var readyEvents = 0
        assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { active }) {
            if (it is MaterializationStage.Downloading) active = false
            if (it is MaterializationStage.Ready) readyEvents++
        } }
        assertEquals(0, readyEvents); assertEquals(0, originals.confirms)
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
    }
    @Test fun corruptTruncatedAndTrailingBytesDoNotConfirmOrReplaceIdentity() = runTest {
        for (kind in 0..2) {
            val (local, saved, services) = fixture(); val (originals, server) = services
            server.bytes = when (kind) {
                0 -> server.bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
                1 -> server.bytes.copyOf(server.bytes.size - 1)
                else -> server.bytes + byteArrayOf(1)
            }
            assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, saved.materializations.single(), { true }) }
            assertEquals(0, originals.confirms); assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
        }
    }
    @Test fun lostLocalConfirmationAckRecoversSameVerifiedOriginalWithoutSecondDownload() = runTest {
        val (local, saved, services) = fixture(); val (originals, server) = services; val mapping = saved.materializations.single()
        originals.loseConfirmAck = true
        assertFails { materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true }) }
        val recovered = materializeRemoteAsset(local, saved.local, server, server.current, server, mapping, { true })
        assertEquals(mapping.localAssetId, recovered.id); assertEquals(1, server.downloads); assertEquals(1, originals.confirms)
        assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
    }
    @Test fun lateRevocationOrRemoteHeadChangeNeverEmitsReadyAndKeepsVerifiedCache() = runTest {
        for (changeHead in listOf(false, true)) {
            val (local, saved, services) = fixture(); val (originals, server) = services
            var readyEvents = 0
            if (!changeHead) server.denyAt = 2
            else server.afterChunk = { server.current = server.current.copy(lastServerSeq = 1) }
            assertFails { materializeRemoteAsset(local, saved.local, server, saved.proposal!!.remoteSession(), server,
                saved.materializations.single(), { true }) { if (it is MaterializationStage.Ready) readyEvents++ } }
            assertEquals(0, readyEvents)
            assertEquals(AssetStatus.Ready, originals.asset!!.status) // Correct private bytes are not silently deleted.
            assertEquals(saved, local.localSyncStore!!.readSyncSnapshot(saved.local))
        }
    }
}
