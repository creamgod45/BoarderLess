package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalSyncRebaseTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null; var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation); value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) { check(value?.generation == generation && !failConfirm) }
    }
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Ours")
    private val beforePeer = node.copy(id = CanvasObjectId("peer-before"), text = "Peer before")
    private val laterPeer = node.copy(id = CanvasObjectId("peer-later"), text = "Peer later")
    private val localLater = node.copy(id = CanvasObjectId("local-later"), text = "Local later")
    private data class Fixture(val records: Records, val repo: AwaitedLocalWorkspaceRepository,
        val snapshot: LocalWorkspaceSyncSnapshot, val verification: PreparedSubmissionVerification)
    private suspend fun fixture(materialized: Boolean = false): Fixture {
        val records = Records(); val repo = AwaitedLocalWorkspaceRepository(records) { "2026-10-10T00:00:00Z" }
        val opened = repo.openOrCreateWorkspace()
        repo.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("create", listOf(node)))
        val owner = repo.refresh(opened)
        val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-user", WorkspaceId("remote-board"))
        val media = MediaNode(CanvasObjectId("media"), transform = node.transform, assetId = "server-original", mediaKind = MediaKind.Image)
        val remote = WorkspaceSession(binding.userId, "remote-client", WorkspaceMemberRole.Owner, owner.workspaceVersion, 1,
            owner.workspace.copy(id = binding.workspaceId, objects = if (materialized) mapOf(node.id to node.copy(text = "Remote old"), media.id to media) else mapOf(node.id to node.copy(text = "Remote old"))))
        val bound = repo.bindSyncDestination(owner, binding, remote)
        val merge = LocalWorkspaceSyncMergePlan(bound, remote)
        val original = merge.proposal("original", merge.rows.associate { it.id to if (materialized && it.id == "object:media") LocalSyncMergeChoice.Remote else LocalSyncMergeChoice.Local })
        var saved = repo.stageSyncProposal(owner, bound.checkpoint!!, null, original)
        val applied = if (materialized) {
            val source = WorkspaceAsset(media.assetId, binding.workspaceId, "creator", "image/png", 3, "sha256:" + "c".repeat(64),
                1, 1, null, AssetStatus.Ready, "2026-10-11")
            val mapping = newLocalAssetMaterialization(saved, source, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11")
            saved = repo.stageSyncMaterialization(saved.local, saved.checkpoint!!, mapping)
            // Controlled verification input for publication tests; not a live byte/auth proof.
            val target = reviewedLocalApplyTarget(saved)
            repo.applyMaterializedSyncProposal(VerifiedMaterializedTarget(saved, target), LocalWorkspaceContentOperation("apply", owner.workspace, target)).snapshot
        } else repo.applySyncProposal(owner, original.id, LocalWorkspaceContentOperation("apply", owner.workspace, original.target)).snapshot
        var id = 0
        val operation = buildWorkspaceContentTransaction(remote.workspace, original.target.copy(id = remote.workspace.id), { "out-${++id}" })!!
        val outgoing = OutgoingLocalSyncTransaction(original.id, remote.workspaceVersion, remote.lastServerSeq, remote.clientId, operation)
        repo.stageSyncOutgoing(applied.local, outgoing)
        val prepared = PreparedWorkspaceSubmission(binding.serverScope, binding.userId, remote.clientId, binding.workspaceId,
            operation.operationId, "wire", remote.workspaceVersion, listOf("wire-op"), listOf(1L), "b".repeat(64))
        repo.bindSyncOutgoingSubmission(applied.local, outgoing, prepared)
        repo.retainDraftAwaited(applied.local, applied.local.workspace, CreateObjectsOperation("local-later", listOf(localLater)))
        val snapshot = repo.readSyncSnapshot(applied.local)
        // A disjoint peer transaction precedes ours; another follows it.
        val withPeer = remote.workspace.copy(version = remote.workspaceVersion + 1, objects = remote.workspace.objects + (beforePeer.id to beforePeer))
        val committed = assertIs<OperationResult.Applied>(operation.applyTo(withPeer)).workspace
        val latest = committed.copy(version = committed.version + 1, objects = committed.objects +
            (beforePeer.id to beforePeer.copy(text = "Peer changed later", version = 2)) + (laterPeer.id to laterPeer))
        val current = remote.copy(workspace = latest, workspaceVersion = latest.version, lastServerSeq = 4)
        return Fixture(records, repo, snapshot, PreparedSubmissionVerification(prepared, PendingReceiptStatus.Committed,
            current, committed.version, 3, committed))
    }
    @Test fun mappedRebasePublishesCanonicalContinuationAndLocalUndoWithSameUnknownAckIdentity() = runTest {
        val f = fixture(materialized = true)
        val context = localSyncRebaseContext(f.snapshot, f.verification)
        val reviewed = LocalSyncRebasePlan(f.snapshot.local, context).proposal("mapped-rebase", syncCheckpointDigest(f.snapshot.checkpoint!!), emptyMap(), emptyMap())
        val saved = f.repo.stageSyncRebaseProposal(f.snapshot.local, null, reviewed, f.verification)
        val localTarget = reviewedLocalApplyTarget(saved)
        val operation = LocalWorkspaceContentOperation("mapped-rebase-apply", saved.local.workspace, localTarget)
        val proof = VerifiedMaterializedTarget(saved, localTarget)
        val beforeGeneration = f.records.value!!.generation
        assertFails { f.repo.applyMaterializedSyncRebaseProposal(VerifiedMaterializedTarget(saved, localTarget.copy(title = "Forged")), operation, f.verification) }
        assertEquals(beforeGeneration, f.records.value!!.generation)
        f.records.failConfirm = true
        assertFails { f.repo.applyMaterializedSyncRebaseProposal(proof, operation, f.verification) }
        val generation = f.records.value!!.generation
        f.records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(f.records) { "2026-10-11" }
        val server = object : CanvasWorkspaceRepository by reopened { override val syncServerScope = saved.checkpoint!!.binding.serverScope }
        val gateway = object : AssetDownloadGateway {
            override val downloadServerScope = server.syncServerScope
            override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket = error("Recovery must not transfer or reauthorize originals")
            override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = error("Recovery must not transfer")
        }
        val recovery = verifyMaterializedRebaseTargetOrRecovery(reopened, saved, operation, server, f.verification.current, gateway, { true })
        val applied = reopened.applyMaterializedSyncRebaseProposal(recovery, operation, f.verification).snapshot
        assertEquals(generation, f.records.value!!.generation)
        val mapping = saved.materializations.single()
        assertEquals(mapping.localAssetId, (applied.local.workspace.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertEquals(mapping.source.id, (applied.proposal!!.target.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertEquals(f.verification.committedWorkspace!!.copy(id = applied.local.workspace.id), applied.checkpoint!!.baseline)
        assertEquals(saved.materializations, applied.rebases.single().materializations)
        assertEquals(saved.materializations, applied.applied!!.materializations)
        assertNull(applied.outgoing); assertNull(applied.rebaseProposal)
        var sequence = 0
        val outgoing = OutgoingLocalSyncTransaction(applied.proposal!!.id, applied.proposal!!.remoteVersion, applied.proposal!!.remoteSeq,
            applied.proposal!!.remoteClientId, buildWorkspaceContentTransaction(applied.proposal!!.remote, applied.proposal!!.target.copy(id = applied.proposal!!.remote.id), { "continuation-op-${++sequence}" })!!)
        outgoing.requireProposal(applied.proposal!!)
        val wireTarget = assertIs<OperationResult.Applied>(outgoing.operation.applyTo(applied.proposal!!.remote)).workspace
        assertEquals(mapping.source.id, (wireTarget.objects[CanvasObjectId("media")] as MediaNode).assetId)
        val undone = restoredLocalSyncHistory(applied).undo(); assertTrue(undone.succeeded)
        assertEquals(mapping.localAssetId, (undone.history.workspace.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertTrue(sameSyncContent(saved.local.workspace, undone.history.workspace))
        val redo = undone.history.redo(); assertTrue(redo.succeeded)
        reopened.retainDraftAwaited(applied.local, applied.local.workspace, undone.appliedOperation!!)
        val fresh = reopened.readSyncSnapshot(applied.local)
        reopened.retainDraftAwaited(fresh.local, fresh.local.workspace, redo.appliedOperation!!)
        assertEquals(mapping.localAssetId, (reopened.readSyncSnapshot(fresh.local).local.workspace.objects[CanvasObjectId("media")] as MediaNode).assetId)
        assertFails { reopened.applyMaterializedSyncRebaseProposal(proof, operation, f.verification) }
    }
    @Test fun twoStagesRetainUnseenPeerAdditionsLaterLocalIntentAndLatestRemoteChanges() = runTest {
        val f = fixture(); val context = localSyncRebaseContext(f.snapshot, f.verification)
        val plan = LocalSyncRebasePlan(f.snapshot.local, context)
        val normalized = plan.intentPlan.resolve(emptyMap())
        assertEquals(beforePeer, normalized.objects[beforePeer.id])
        assertEquals(localLater, normalized.objects[localLater.id])
        val final = plan.latestPlan(emptyMap()).resolve(emptyMap())
        assertEquals("Peer changed later", (final.objects[beforePeer.id] as TextNode).text)
        assertEquals(laterPeer, final.objects[laterPeer.id]); assertEquals(localLater, final.objects[localLater.id])
        assertEquals("Ours", (final.objects[node.id] as TextNode).text)
    }
    @Test fun materializedFourInputsCompareCanonicalIdsWithoutInventingAnAssetConflict() = runTest {
        val f = fixture(); val original = f.snapshot.proposal!!
        val source = MaterializationSourceDescriptor("server-original", original.binding.workspaceId, "creator", "image/png", 3,
            "sha256:" + "c".repeat(64), 1, 1, null, "2026-10-11")
        val mapping = LocalAssetMaterialization(original.binding, f.snapshot.local.userId, f.snapshot.local.clientId,
            f.snapshot.local.workspace.id, original.id, source, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11")
        val canonical = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)),
            assetId = source.id, mediaKind = MediaKind.Image, altText = "Original")
        val localMedia = canonical.copy(assetId = mapping.localAssetId)
        fun Workspace.add(media: MediaNode) = copy(objects = objects + (media.id to media))
        val submitted = original.copy(remote = original.remote.add(canonical), target = original.target.add(canonical))
        val localOperation = f.snapshot.applied!!.operation.copy(after = original.target.add(localMedia))
        val applied = f.snapshot.applied!!.copy(operation = localOperation,
            appliedDigest = syncWorkspaceDigest(localOperation.appliedWorkspace()), materializations = listOf(mapping))
        val snapshot = f.snapshot.copy(proposal = submitted, applied = applied, materializations = listOf(mapping),
            local = f.snapshot.local.copy(workspace = f.snapshot.local.workspace.add(localMedia.copy(altText = "Later local", version = 2))))
        val verified = f.verification.copy(committedWorkspace = f.verification.committedWorkspace!!.add(canonical),
            current = f.verification.current.copy(workspace = f.verification.current.workspace.add(canonical)))
        val context = localSyncRebaseContext(snapshot, verified)
        val plan = LocalSyncRebasePlan(snapshot.local, context)
        assertFalse(plan.intentPlan.rows.any { it.id == "object:media" && it.requiresChoice })
        val target = plan.latestPlan(emptyMap()).resolve(emptyMap())
        assertEquals(canonical.copy(altText = "Later local", version = 2), target.objects[canonical.id])
        assertEquals(localLater, target.objects[localLater.id]); assertEquals(laterPeer, target.objects[laterPeer.id])
        assertEquals("Peer changed later", (target.objects[beforePeer.id] as TextNode).text)
        assertEquals(mapping.localAssetId, (snapshot.local.workspace.objects[canonical.id] as MediaNode).assetId)
        assertEquals(source.id, (context.committed.objects[canonical.id] as MediaNode).assetId)
        val conflictContext = context.copy(remote = context.remote.add(canonical.copy(altText = "Peer later", version = 2)))
        val conflict = LocalSyncRebasePlan(snapshot.local, conflictContext).latestPlan(emptyMap())
        assertTrue(conflict.rows.any { it.id == "object:media" && it.requiresChoice })
        assertFails { conflict.resolve(emptyMap()) }
        assertEquals("Later local", (conflict.resolve(mapOf("object:media" to LocalSyncMergeChoice.Local)).objects[canonical.id] as MediaNode).altText)
        assertEquals("Peer later", (conflict.resolve(mapOf("object:media" to LocalSyncMergeChoice.Remote)).objects[canonical.id] as MediaNode).altText)
    }
    @Test fun durablePlanSurvivesUnknownAckAndCannotOverwriteNewEditsOrChangedHead() = runTest {
        val f = fixture(); val context = localSyncRebaseContext(f.snapshot, f.verification)
        val plan = LocalSyncRebasePlan(f.snapshot.local, context)
        val proposal = plan.proposal("rebase", syncCheckpointDigest(f.snapshot.checkpoint!!), emptyMap(), emptyMap())
        f.records.failConfirm = true
        assertFails { f.repo.stageSyncRebaseProposal(f.snapshot.local, null, proposal, f.verification) }
        val generation = f.records.value!!.generation
        f.records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(f.records) { "2026-10-10T00:00:00Z" }
        val saved = reopened.stageSyncRebaseProposal(f.snapshot.local, null, proposal, f.verification)
        assertEquals(generation, f.records.value!!.generation)
        assertEquals(proposal, saved.rebaseProposal); assertTrue(saved.rebaseIsCurrent)
        assertEquals(f.snapshot.local.workspace, saved.local.workspace); assertEquals(f.snapshot.outgoing, saved.outgoing)
        assertEquals(f.snapshot.checkpoint, saved.checkpoint)
        assertFails { reopened.stageSyncRebaseProposal(saved.local, proposal.id, proposal.copy(target = proposal.target.copy(title = "Forged")), f.verification) }
        assertFails { reopened.stageSyncRebaseProposal(saved.local, proposal.id, proposal.copy(id = "next"),
            f.verification.copy(current = f.verification.current.copy(lastServerSeq = 5))) }
        reopened.retainDraftAwaited(saved.local, saved.local.workspace, CreateObjectsOperation("extra", listOf(node.copy(id = CanvasObjectId("extra")))))
        val changed = reopened.readSyncSnapshot(saved.local)
        assertFalse(changed.rebaseIsCurrent); assertEquals(proposal, changed.rebaseProposal)
        assertFails { reopened.stageSyncRebaseProposal(saved.local, proposal.id, proposal.copy(id = "stale"), f.verification) }
    }
    @Test fun actualConflictsRequireDecisionsRatherThanSilentReplay() = runTest {
        val f = fixture()
        val localNode = (f.snapshot.local.workspace.objects[node.id] as TextNode).copy(text = "Later local")
        val remoteNode = (f.verification.current.workspace.objects[node.id] as TextNode).copy(text = "Later remote")
        val local = f.snapshot.local.copy(workspace = f.snapshot.local.workspace.copy(objects = f.snapshot.local.workspace.objects + (node.id to localNode)))
        val verified = f.verification.copy(current = f.verification.current.copy(workspace = f.verification.current.workspace.copy(
            objects = f.verification.current.workspace.objects + (node.id to remoteNode))))
        val plan = LocalSyncRebasePlan(local, localSyncRebaseContext(f.snapshot, verified))
        val latest = plan.latestPlan(emptyMap())
        assertFails { latest.resolve(emptyMap()) }
        assertEquals("Later local", (latest.resolve(mapOf("object:node" to LocalSyncMergeChoice.Local)).objects[node.id] as TextNode).text)
        assertEquals("Later remote", (latest.resolve(mapOf("object:node" to LocalSyncMergeChoice.Remote)).objects[node.id] as TextNode).text)
    }
    @Test fun atomicApplySettlesHistoricalBaselineKeepsOriginalEvidenceAndRestoresUndo() = runTest {
        val f = fixture(); val context = localSyncRebaseContext(f.snapshot, f.verification)
        val reviewed = LocalSyncRebasePlan(f.snapshot.local, context).proposal("apply-reviewed",
            syncCheckpointDigest(f.snapshot.checkpoint!!), emptyMap(), emptyMap())
        val staged = f.repo.stageSyncRebaseProposal(f.snapshot.local, null, reviewed, f.verification)
        val operation = LocalWorkspaceContentOperation("apply-rebase", staged.local.workspace, reviewed.target)
        f.records.failConfirm = true
        assertFails { f.repo.applySyncRebaseProposal(staged.local, reviewed.id, operation, f.verification) }
        val generation = f.records.value!!.generation
        f.records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(f.records) { "2026-10-10T00:00:00Z" }
        val applied = reopened.applySyncRebaseProposal(staged.local, reviewed.id, operation, f.verification).snapshot
        assertEquals(generation, f.records.value!!.generation)
        assertNull(applied.rebaseProposal); assertNull(applied.outgoing)
        assertEquals(reviewed, applied.rebases.single().reviewed)
        assertEquals(f.verification.committedWorkspace!!.copy(id = applied.local.workspace.id), applied.checkpoint!!.baseline)
        assertEquals(context.committedVersion, applied.checkpoint!!.serverVersion)
        assertEquals(context.remoteVersion, applied.proposal!!.remoteVersion)
        assertTrue(applied.appliedIsCurrent)
        assertTrue(sameSyncContent(reviewed.target, applied.local.workspace))
        val restored = restoredLocalSyncHistory(reopened.readSyncSnapshot(applied.local))
        val undone = restored.undo(); assertTrue(undone.succeeded)
        assertTrue(sameSyncContent(reviewed.localBefore, undone.history.workspace))
        val redone = undone.history.redo(); assertTrue(redone.succeeded)
        assertTrue(sameSyncContent(reviewed.target, redone.history.workspace))
        val server = object : CanvasWorkspaceRepository by reopened {
            override val syncServerScope = context.original.binding.serverScope
            override suspend fun verifyPreparedSubmission(session: WorkspaceSession, prepared: PreparedWorkspaceSubmission,
                operation: WorkspaceOperation, baseline: WorkspaceSession?) = f.verification
        }
        val (publication, _) = verifyLocalSyncRebaseApply(reopened, staged.local, server, context.remoteSession(), reviewed, { true })
        assertEquals(reviewed.localBefore, publication.local.workspace)
        assertEquals(applied.local.workspace, reopened.readSyncSnapshot(applied.local).local.workspace)
        reopened.retainDraftAwaited(applied.local, applied.local.workspace,
            CreateObjectsOperation("post-rebase", listOf(node.copy(id = CanvasObjectId("post-rebase")))))
        assertFails { reopened.applySyncRebaseProposal(staged.local, reviewed.id, operation, f.verification) }
    }
    @Test fun changedRemoteCannotApplyAnApprovedRebase() = runTest {
        val f = fixture(); val context = localSyncRebaseContext(f.snapshot, f.verification)
        val reviewed = LocalSyncRebasePlan(f.snapshot.local, context).proposal("stale-head",
            syncCheckpointDigest(f.snapshot.checkpoint!!), emptyMap(), emptyMap())
        val staged = f.repo.stageSyncRebaseProposal(f.snapshot.local, null, reviewed, f.verification)
        val operation = LocalWorkspaceContentOperation("stale-apply", staged.local.workspace, reviewed.target)
        assertFails { f.repo.applySyncRebaseProposal(staged.local, reviewed.id, operation,
            f.verification.copy(current = f.verification.current.copy(lastServerSeq = f.verification.current.lastServerSeq + 1))) }
        assertEquals(staged.local.workspace, f.repo.readSyncSnapshot(staged.local).local.workspace)
    }

    @Test fun appliedContinuationCanBeReviewedAgainWithoutLosingCommitEvidenceOrUndo() = runTest {
        val f = fixture()
        val context = localSyncRebaseContext(f.snapshot, f.verification)
        val reviewed = LocalSyncRebasePlan(f.snapshot.local, context).proposal("continued-review",
            syncCheckpointDigest(f.snapshot.checkpoint!!), emptyMap(), emptyMap())
        f.repo.stageSyncRebaseProposal(f.snapshot.local, null, reviewed, f.verification)
        val applied = f.repo.applySyncRebaseProposal(f.snapshot.local, reviewed.id,
            LocalWorkspaceContentOperation("continued-apply", reviewed.localBefore, reviewed.target), f.verification).snapshot
        val addition = node.copy(id = CanvasObjectId("new-peer"), text = "New peer")
        val latestWorkspace = context.remote.copy(version = context.remoteVersion + 1, objects = context.remote.objects + (addition.id to addition))
        val latest = context.remoteSession().copy(workspace = latestWorkspace, workspaceVersion = latestWorkspace.version, lastServerSeq = context.remoteSeq + 1)
        val plan = LocalWorkspaceSyncMergePlan(applied, latest)
        val next = plan.proposal("continued-next", emptyMap())
        f.records.failConfirm = true
        assertFails { f.repo.stageSyncProposal(applied.local, applied.checkpoint!!, applied.proposal!!.id, next) }
        val generation = f.records.value!!.generation
        f.records.failConfirm = false
        val reopened = AwaitedLocalWorkspaceRepository(f.records) { "2026-10-10T00:00:00Z" }
        val staged = reopened.stageSyncProposal(applied.local, applied.checkpoint!!, applied.proposal!!.id, next)
        assertEquals(generation, f.records.value!!.generation)
        assertEquals(applied.local.workspace, staged.local.workspace)
        assertEquals(applied.checkpoint, staged.checkpoint)
        assertEquals(applied.rebases, staged.rebases)
        assertEquals(applied.applied, staged.superseded.single().applied)
        assertEquals(addition, staged.proposal!!.target.objects[addition.id])
        assertNull(staged.applied); assertNull(staged.outgoing)
        assertTrue(restoredLocalSyncHistory(staged).undo().succeeded)
        val evidence = LocalSyncEvidenceJson.decodeFromString(LocalSyncEvidenceBackup.serializer(), staged.toEvidenceJson())
        assertEquals(staged.superseded, evidence.superseded)
        assertEquals(staged.rebases, evidence.rebases)
        assertEquals(f.snapshot.outgoing, evidence.rebases.single().reviewed.context.outgoing)
        assertFails { reopened.stageSyncProposal(staged.local, staged.checkpoint!!, next.id,
            next.copy(id = "forged-next", target = next.target.copy(title = "forged"))) }
    }
}
