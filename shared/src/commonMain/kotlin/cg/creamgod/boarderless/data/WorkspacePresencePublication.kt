package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Normalized transient state, NOT a backend wire schema or a persisted operation. */
data class WorkspacePresenceUpdate(
    val presenceSeq: Long,
    val cursor: Vec2?,
    val selectedIds: Set<CanvasObjectId>,
)

/** Bound to one authenticated session, subscription, room AND physical connection epoch.
 * The adapter must recheck authority/epoch at the actual transport write, respect cancellation,
 * and apply any lower negotiated server limits. Creation alone must not share local state.
 * Closing relinquishes this handle; server leave/TTL handles removal after authority is lost.
 */
interface WorkspacePresencePublisher {
    suspend fun publish(update: WorkspacePresenceUpdate)

    fun close()
}

internal data class WorkspacePresenceIntent(
    val cursor: Vec2?,
    val selectedIds: Set<CanvasObjectId>,
)

/** Re-evaluate on every read, not only when Compose cancels the previous owner's effect. */
internal fun presencePublicationIsLive(
    opened: WorkspaceSession,
    current: WorkspaceSession?,
    subscriptionId: String,
    currentSubscriptionId: String,
    roomEpoch: String,
    presence: WorkspacePresenceState,
    nowMs: Long,
): Boolean =
    subscriptionId.isNotBlank() && roomEpoch.isNotBlank() &&
        subscriptionId == currentSubscriptionId && presence.belongsTo(opened, subscriptionId) &&
        presence.visibleFor(current, currentSubscriptionId, nowMs).let { it.connected && it.roomEpoch == roomEpoch }

/** Uses authoritative IDs only; optimistic-only creations must wait for their committed state.
 * Screen samples remain local, so panning under a stationary pointer recomputes world position.
 * No viewport, text, URL, actor identity, secrets or draft payload enters an outgoing update.
 */
internal fun workspacePresenceIntent(
    session: WorkspaceSession,
    viewport: Viewport,
    screenCursor: Vec2?,
    selectedIds: Set<CanvasObjectId>,
    sharingEnabled: Boolean,
): WorkspacePresenceIntent {
    if (!sharingEnabled) return WorkspacePresenceIntent(null, emptySet())
    // Double arithmetic avoids Float overflow before Vec2 can validate the result.
    val cursor =
        screenCursor?.let {
            val x = (it.x.toDouble() - viewport.pan.x.toDouble()) / viewport.zoom.toDouble()
            val y = (it.y.toDouble() - viewport.pan.y.toDouble()) / viewport.zoom.toDouble()
            if (x.isFinite() && y.isFinite() && x in -10_000_000.0..10_000_000.0 && y in -10_000_000.0..10_000_000.0) {
                Vec2(x.toFloat(), y.toFloat())
            } else {
                null
            }
        }
    val ids =
        selectedIds
            .asSequence()
            .filter { it in session.workspace.objects && it.value.length <= 128 && it.value.none { ch -> ch.code < 32 } }
            .sortedBy { it.value }
            .take(128)
            .toSet()
    return WorkspacePresenceIntent(cursor, ids)
}

/** Polls the latest intent, never queues pointer events. One publication at a time gives
 * backpressure. Client ceilings: cursor 10 Hz, selection at least 250 ms apart. These are
 * proposal limits, not proof of backend capability. Null means the authenticated scope expired:
 * stop without sending an old-room clear. Disabled sharing is an explicit empty intent instead.
 * No retries: a failed publisher must be replaced by a newly authenticated connection owner.
 */
internal suspend fun publishWorkspacePresence(
    publisher: WorkspacePresencePublisher,
    readLatest: () -> WorkspacePresenceIntent?,
    monotonicTimeMs: () -> Long,
) {
    var failure: Throwable? = null
    try {
        var previous = WorkspacePresenceIntent(null, emptySet())
        var lastPublication: Long? = null
        var lastSelectionPublication: Long? = null
        var lastClock: Long? = null
        var sequence = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val latest = readLatest() ?: return
            currentCoroutineContext().ensureActive()
            val now = monotonicTimeMs()
            require(now >= 0 && (lastClock == null || now >= lastClock)) { "Presence clock must be monotonic" }
            lastClock = now
            val selectionChanged = latest.selectedIds != previous.selectedIds
            val cursorReady = lastPublication == null || now - lastPublication >= 100
            val selectionReady = !selectionChanged || lastSelectionPublication == null || now - lastSelectionPublication >= 250
            if (latest != previous && cursorReady && selectionReady) {
                check(sequence < 9_007_199_254_740_991L) { "Presence sequence exhausted" }
                val update = WorkspacePresenceUpdate(sequence + 1, latest.cursor, latest.selectedIds.toSet())
                currentCoroutineContext().ensureActive()
                publisher.publish(update)
                currentCoroutineContext().ensureActive()
                sequence = update.presenceSeq
                previous = latest.copy(selectedIds = update.selectedIds)
                // Account for time spent suspended in a slow transport, not only sampling time.
                val completedAt = monotonicTimeMs()
                require(completedAt >= now) { "Presence clock must be monotonic" }
                lastClock = completedAt
                lastPublication = completedAt
                if (selectionChanged) lastSelectionPublication = completedAt
            }
            delay(100)
        }
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            publisher.close()
        } catch (closeError: Throwable) {
            if (failure == null) throw closeError
            // Do not replace the original publication failure or cancellation with cleanup failure.
        }
    }
}
