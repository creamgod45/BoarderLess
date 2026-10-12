package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

/** Non-cooperative adapters must not deliver snapshots after observer cancellation. */
internal suspend fun collectActiveWorkspacePresence(
    source: () -> Flow<WorkspacePresenceSnapshot>?,
    publish: (WorkspacePresenceSnapshot) -> Unit,
) {
    currentCoroutineContext().ensureActive()
    val stream = source() ?: return
    currentCoroutineContext().ensureActive()
    stream.collect { snapshot ->
        currentCoroutineContext().ensureActive()
        publish(snapshot)
    }
}

data class WorkspacePresencePeer(
    val connectionId: String,
    val userId: String,
    val clientId: String,
    val displayName: String,
    val cursor: Vec2? = null,
    val selectedIds: Set<CanvasObjectId> = emptySet(),
)

/** Trusted transport emits full snapshots after authenticating scope/liveness. Not wire JSON. */
data class WorkspacePresenceSnapshot(
    val workspaceId: WorkspaceId,
    val observerUserId: String,
    val observerClientId: String,
    val subscriptionId: String,
    val roomEpoch: String,
    val roomRevision: Long,
    val peers: List<WorkspacePresencePeer>,
    val ttlMs: Long = 45_000,
    // Adapter-normalized, monotonic per room epoch, renewed only on authenticated liveness.
    // It is not a server content sequence or a peer-provided permission grant.
    val leaseSequence: Long = roomRevision,
)

internal data class WorkspacePresenceState(
    val workspaceId: WorkspaceId? = null,
    val observerUserId: String? = null,
    val observerClientId: String? = null,
    val observerRole: WorkspaceMemberRole? = null,
    val subscriptionId: String? = null,
    val roomEpoch: String? = null,
    val roomRevision: Long = -1,
    val leaseSequence: Long = -1,
    val seenRooms: Set<String> = emptySet(),
    val snapshotPeers: List<WorkspacePresencePeer> = emptyList(),
    val peers: List<WorkspacePresencePeer> = emptyList(),
    val expiresAtMs: Long = 0,
    val connected: Boolean = false,
) {
    fun belongsTo(
        session: WorkspaceSession?,
        subscriptionId: String,
    ): Boolean =
        session != null && workspaceId == session.workspace.id && observerUserId == session.userId &&
            observerClientId == session.clientId && observerRole == session.role && this.subscriptionId == subscriptionId

    fun visibleFor(
        session: WorkspaceSession?,
        subscriptionId: String,
        nowMs: Long,
    ): WorkspacePresenceState = if (belongsTo(session, subscriptionId)) expire(nowMs) else WorkspacePresenceState()

    fun expire(nowMs: Long): WorkspacePresenceState =
        if (connected && nowMs >= expiresAtMs) copy(peers = emptyList(), connected = false) else this
}

internal fun acceptWorkspacePresence(
    session: WorkspaceSession,
    subscriptionId: String,
    previous: WorkspacePresenceState,
    snapshot: WorkspacePresenceSnapshot,
    nowMs: Long,
): WorkspacePresenceState? {
    fun plain(value: String, maximumBytes: Int): Boolean =
        value.isNotBlank() && value.length <= maximumBytes && value.none { it.code < 32 || it.code == 127 } &&
            runCatching { value.encodeToByteArray(throwOnInvalidSequence = true).size <= maximumBytes }.getOrDefault(false)
    if (snapshot.workspaceId != session.workspace.id || snapshot.observerUserId != session.userId ||
        snapshot.observerClientId != session.clientId || snapshot.subscriptionId != subscriptionId ||
        !plain(subscriptionId, 256) || !plain(snapshot.roomEpoch, 128) ||
        snapshot.roomRevision !in 0..9_007_199_254_740_991L || snapshot.ttlMs !in 1_000..120_000 ||
        snapshot.leaseSequence !in 0..9_007_199_254_740_991L ||
        nowMs < 0 || nowMs > Long.MAX_VALUE - snapshot.ttlMs || snapshot.peers.size > 256
    ) {
        return null
    }
    val scoped = if (previous.belongsTo(session, subscriptionId)) previous else WorkspacePresenceState()
    if (snapshot.roomEpoch == scoped.roomEpoch &&
        (
            snapshot.roomRevision < scoped.roomRevision || snapshot.leaseSequence <= scoped.leaseSequence ||
                (snapshot.roomRevision == scoped.roomRevision && snapshot.peers != scoped.snapshotPeers)
        )
    ) {
        return null
    }
    if (snapshot.roomEpoch != scoped.roomEpoch && snapshot.roomEpoch in scoped.seenRooms) return null
    val rooms = scoped.seenRooms + snapshot.roomEpoch
    if (rooms.size > 64 || snapshot.peers
            .map { it.connectionId }
            .toSet()
            .size != snapshot.peers.size
    ) {
        return null
    }
    if (snapshot.peers.any { peer ->
            listOf(peer.connectionId, peer.userId, peer.clientId).any { !plain(it, 128) } ||
                peer.displayName.length > 100 || !plain(peer.displayName, 400) ||
                peer.selectedIds.size > 200 ||
                peer.selectedIds.any { !plain(it.value, 128) } ||
                peer.cursor?.let { !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > 10_000_000f || abs(it.y) > 10_000_000f } == true
        }
    ) {
        return null
    }
    val peers = snapshot.peers.map { it.copy(selectedIds = it.selectedIds.toSet()) }
    return WorkspacePresenceState(
        workspaceId = session.workspace.id,
        observerUserId = session.userId,
        observerClientId = session.clientId,
        observerRole = session.role,
        subscriptionId = subscriptionId,
        roomEpoch = snapshot.roomEpoch,
        roomRevision = snapshot.roomRevision,
        leaseSequence = snapshot.leaseSequence,
        seenRooms = rooms,
        snapshotPeers = peers,
        peers = peers.filterNot { it.userId == session.userId && it.clientId == session.clientId },
        expiresAtMs = nowMs + snapshot.ttlMs,
        connected = true,
    )
}
