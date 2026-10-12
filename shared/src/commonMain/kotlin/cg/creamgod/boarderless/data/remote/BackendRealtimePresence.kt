package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.WorkspacePresencePeer
import cg.creamgod.boarderless.data.WorkspacePresenceSnapshot
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.abs

@Serializable private data class PresenceCursorWire(val x: Float, val y: Float)
@Serializable private data class PresencePeerWire(val connectionId: String, val userId: String, val clientId: String,
    val displayName: String, val role: String, val cursor: PresenceCursorWire?, val selectedObjectIds: List<String>, val presenceSeq: Long) {
    fun normalized(maxSelectionIds: Int): WorkspacePresencePeer {
        listOf(connectionId, userId, clientId).forEach(::requireRealtimeUuid)
        require(role in setOf("owner", "editor", "commenter", "viewer") && presenceSeq in 0..RealtimeMaxSequence)
        require(displayName.isNotBlank() && displayName.length <= 100 && displayName.none { it.code < 32 || it.code == 127 })
        require(displayName.encodeToByteArray(throwOnInvalidSequence = true).size <= 400)
        require(selectedObjectIds.size <= maxSelectionIds && selectedObjectIds.distinct().size == selectedObjectIds.size)
        selectedObjectIds.forEach(::requireRealtimeUuid)
        cursor?.let { require(it.x.isFinite() && it.y.isFinite() && abs(it.x) <= 10_000_000f && abs(it.y) <= 10_000_000f) }
        return WorkspacePresencePeer(connectionId, userId, clientId, displayName, cursor?.let { Vec2(it.x, it.y) },
            selectedObjectIds.map { CanvasObjectId(it) }.toSet())
    }
}

/** Ephemeral room state for exactly one validated physical epoch. Room deltas are not
 * workspace operations, permissions or receipts. Missing revisions require reconnect;
 * stale notifications are ignored without extending the lease. */
internal class BackendRealtimePresence(private val workspaceId: String, private val userId: String,
    private val clientId: String, private val subscriptionId: String, private val connectionEpoch: String,
    private val ttlMs: Long, private val maxSelectionIds: Int, private val isLive: () -> Boolean) {
    private val strict = Json
    private var roomEpoch: String? = null
    private var revision = -1L
    private var leaseSequence = 0L
    private var peers = emptyMap<String, WorkspacePresencePeer>()
    private var closed = false
    init {
        listOf(workspaceId, userId, clientId, connectionEpoch).forEach(::requireRealtimeUuid)
        require(subscriptionId.isNotBlank() && subscriptionId.length <= 256 && subscriptionId.none { it.code < 32 || it.code == 127 })
        require(subscriptionId.encodeToByteArray(throwOnInvalidSequence = true).size <= 256)
        require(ttlMs in 1_000..120_000 && maxSelectionIds in 1..128)
    }
    fun close() { closed = true; peers = emptyMap(); roomEpoch = null; revision = -1 }
    private fun peer(raw: JsonElement): WorkspacePresencePeer {
        val value = raw as? JsonObject ?: error("Invalid presence peer")
        require(value.keys == setOf("connectionId", "userId", "clientId", "displayName", "role", "cursor", "selectedObjectIds", "presenceSeq"))
        require((value["presenceSeq"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
        if (value["cursor"] != JsonNull) {
            val cursor = value["cursor"] as? JsonObject ?: error("Invalid presence cursor")
            require(cursor.keys == setOf("x", "y") && cursor.values.all {
                (it as? JsonPrimitive)?.takeUnless { number -> number.isString }?.doubleOrNull?.isFinite() == true
            })
        }
        val normalized = strict.decodeFromJsonElement(PresencePeerWire.serializer(), raw).normalized(maxSelectionIds)
        peers[normalized.connectionId]?.let { old ->
            require(old.userId == normalized.userId && old.clientId == normalized.clientId)
        }
        return normalized
    }
    fun accept(frame: JsonObject): WorkspacePresenceSnapshot? = guarded {
        require(!closed && isLive())
        fun text(key: String) = requireNotNull((frame[key] as? JsonPrimitive)?.takeIf { it.isString }?.content)
        fun number(key: String) = requireNotNull((frame[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
            .also { require(it in 0..RealtimeMaxSequence) }
        require(number("protocolVersion") == 1L && text("workspaceId") == workspaceId && text("connectionEpoch") == connectionEpoch)
        val type = text("type"); val incomingEpoch = text("roomEpoch"); requireRealtimeUuid(incomingEpoch)
        val incomingRevision = number("roomRevision")
        val envelope = setOf("type", "protocolVersion", "workspaceId", "connectionEpoch", "roomEpoch", "roomRevision")
        when (type) {
            "presence_snapshot" -> {
                require(frame.keys == envelope + "peers" && roomEpoch == null)
                val raw = frame["peers"] as? JsonArray ?: error("Invalid presence snapshot")
                require(raw.size <= 256)
                val normalized = raw.map(::peer)
                require(normalized.map { it.connectionId }.distinct().size == normalized.size)
                peers = normalized.associateBy { it.connectionId }; roomEpoch = incomingEpoch; revision = incomingRevision
            }
            "presence_delta" -> {
                require(frame.keys == envelope + setOf("upserts", "removedConnectionIds") && incomingEpoch == roomEpoch)
                val upserts = frame["upserts"] as? JsonArray ?: error("Invalid presence delta")
                val removed = frame["removedConnectionIds"] as? JsonArray ?: error("Invalid presence delta")
                require(upserts.size <= 256 && removed.size <= 256)
                val updates = upserts.map(::peer)
                val removals = removed.map { requireNotNull((it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content).also(::requireRealtimeUuid) }
                require(updates.map { it.connectionId }.distinct().size == updates.size && removals.distinct().size == removals.size)
                require(updates.none { it.connectionId in removals })
                if (incomingRevision <= revision) return@guarded null
                require(incomingRevision == revision + 1) { "Presence revision gap" }
                val next = peers.toMutableMap(); removals.forEach { next.remove(it) }; updates.forEach { next[it.connectionId] = it }
                require(next.size <= 256); peers = next; revision = incomingRevision
            }
            else -> error("Unsupported presence event")
        }
        snapshot()
    }
    /** Only call for a validated in-order heartbeat ACK on this physical connection. */
    fun renew(): WorkspacePresenceSnapshot? = guarded {
        require(!closed && isLive())
        if (roomEpoch == null) null else snapshot()
    }
    private fun snapshot(): WorkspacePresenceSnapshot {
        require(!closed && isLive())
        require(leaseSequence < RealtimeMaxSequence); leaseSequence++
        return WorkspacePresenceSnapshot(WorkspaceId(workspaceId), userId, clientId, subscriptionId, requireNotNull(roomEpoch),
            revision, peers.values.sortedBy { it.connectionId }, ttlMs, leaseSequence)
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() } catch (failure: Throwable) { close(); throw failure }
}
