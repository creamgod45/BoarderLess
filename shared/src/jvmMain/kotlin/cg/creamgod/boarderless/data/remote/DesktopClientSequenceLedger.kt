package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class ClientSequenceRecord(
    val generation: Long,
    val highWater: Long,
)

/** Private local POSIX ledger, shared across workspaces for one origin/user/client.
 * Explicit initialization requires a verified legacy floor and exclusion of all old writers.
 * No missing/corrupt/tombstoned reset, deletion, auto retry or runtime default activation.
 */
internal class DesktopClientSequenceLedger(
    private val files: DesktopAtomicDraftStore,
    private val requireScope: (PendingSubmissionScope) -> Unit = {},
) : ClientSequenceAllocator {
    fun key(scope: PendingSubmissionScope): String {
        require(listOf(scope.apiBase, scope.userId, scope.clientId, scope.workspaceId).all(String::isNotBlank))
        val content = Json.encodeToString(listOf("BoarderLess.client-sequence.v1", scope.apiBase, scope.userId, scope.clientId))
        return MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    fun read(scope: PendingSubmissionScope): ClientSequenceRecord? {
        requireScope(scope)
        val record = files.read(key(scope)) ?: return null
        val bytes = requireNotNull(record.payload) { "Sequence ledger tombstone requires recovery" }
        require(bytes.size == 16)
        val buffer = ByteBuffer.wrap(bytes)
        require(ByteArray(8).also(buffer::get).contentEquals(Magic))
        val highWater = buffer.long
        require(highWater in 0..Maximum)
        return ClientSequenceRecord(record.generation, highWater)
    }

    fun initialize(
        scope: PendingSubmissionScope,
        verifiedLegacyFloor: Long,
    ): ClientSequenceRecord {
        require(verifiedLegacyFloor in 0..Maximum)
        require(read(scope) == null) { "Existing ledger cannot be initialized again" }
        return publish(scope, null, verifiedLegacyFloor)
    }

    fun reserve(
        scope: PendingSubmissionScope,
        expectedGeneration: Long,
        count: Int,
    ): LongRange {
        require(count in 1..200)
        val current = requireNotNull(read(scope)) { "Sequence ledger requires explicit initialization" }
        if (current.generation != expectedGeneration) throw AtomicDraftConflictException()
        require(current.highWater <= Maximum - count)
        publish(scope, expectedGeneration, current.highWater + count)
        return (current.highWater + 1)..(current.highWater + count)
    }

    override fun reserve(
        scope: PendingSubmissionScope,
        count: Int,
    ): LongRange {
        val current = requireNotNull(read(scope)) { "Sequence ledger requires explicit initialization" }
        return reserve(scope, current.generation, count) // Conflict/busy/unknown is not retried.
    }

    override fun advance(
        scope: PendingSubmissionScope,
        sequence: Long,
    ) {
        require(sequence in 0..Maximum)
        val current = requireNotNull(read(scope)) { "Sequence ledger requires explicit initialization" }
        if (sequence > current.highWater) publish(scope, current.generation, sequence)
    }

    private fun publish(
        scope: PendingSubmissionScope,
        expected: Long?,
        highWater: Long,
    ): ClientSequenceRecord {
        val bytes = encode(highWater)
        val record = files.compareAndSet(key(scope), expected, bytes)
        return ClientSequenceRecord(record.generation, highWater)
    }

    internal fun encode(highWater: Long): ByteArray {
        require(highWater in 0..Maximum)
        return ByteBuffer
            .allocate(16)
            .put(Magic)
            .putLong(highWater)
            .array()
    }

    private companion object {
        val Magic = "BLCS0001".toByteArray(Charsets.US_ASCII)
        const val Maximum = 9_007_199_254_740_991L
    }
}
