package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.remote.CloudSchemeMutation
import cg.creamgod.boarderless.data.remote.CloudSchemeResource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

@Serializable internal data class CloudSchemeOutboxScope(val serverScope: String, val userId: String, val clientId: String) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")))
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        require(uuid.matches(userId) && uuid.matches(clientId))
    }
}
@Serializable internal enum class CloudSchemeTaskPhase { Prepared, Sending, Unknown, Committed, Rejected }
@Serializable internal data class CloudSchemeRetryWindow(val serverMillis: Long, val ttlSeconds: Long) {
    init {
        require(serverMillis > 0 && ttlSeconds in 61..((Long.MAX_VALUE - serverMillis) / 1000))
    }
    fun requireOpen(now: Long, currentTtlSeconds: Long) {
        // Lower bound precedes the original mutation; one-minute margin covers Date
        // precision and bounded preparation. Never extend it on a retry or TTL increase.
        val ttl = minOf(ttlSeconds, currentTtlSeconds)
        require(ttl > 60 && now >= serverMillis && now < serverMillis + (ttl - 60) * 1000) {
            "Cloud retry window expired or server clock moved backwards; keep result unknown"
        }
    }
}
@Serializable internal data class CloudSchemeTask(
    val mutation: CloudSchemeMutation,
    val phase: CloudSchemeTaskPhase = CloudSchemeTaskPhase.Prepared,
    val accepted: CloudSchemeResource? = null,
    val httpStatus: Int? = null,
    val retryWindow: CloudSchemeRetryWindow? = null,
)

/** One endpoint/user/client-scoped CAS journal. Preparing/recording does not authorize
 * network access or change the local library. Sending on reopen remains uncertain.
 * Terminal identities are retained, never evicted or reused to make a new mutation. */
internal class CloudSchemeOutbox(private val files: AwaitedDurableLocalRecordStore, val scope: CloudSchemeOutboxScope) {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    @Serializable private data class Record(val version: Int = 1, val scope: CloudSchemeOutboxScope, val tasks: List<CloudSchemeTask>)
    private val key = SHA256().digest(("boarderless.cloud-scheme-outbox.v1\n" + json.encodeToString(CloudSchemeOutboxScope.serializer(), scope)).encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun validate(record: Record) {
        require(record.version == 1 && record.scope == scope && record.tasks.size <= 128)
        require(record.tasks.map { it.mutation.idempotencyKey }.distinct().size == record.tasks.size)
        record.tasks.forEach { task ->
            task.mutation.body?.validate()
            task.retryWindow?.let { require(task.phase != CloudSchemeTaskPhase.Prepared) }
            require(task.httpStatus == null || task.httpStatus in 100..599)
            require(task.phase == CloudSchemeTaskPhase.Committed || task.accepted == null)
            if (task.phase == CloudSchemeTaskPhase.Committed) {
                if (task.mutation.kind == "delete") require(task.accepted == null && task.httpStatus == 204)
                else {
                    val accepted = requireNotNull(task.accepted)
                    accepted.validate(scope.userId)
                    require(accepted.id == task.mutation.resourceId)
                    val body = requireNotNull(task.mutation.body)
                    require(accepted.name == body.name && accepted.schemaVersion == body.schemaVersion &&
                        accepted.payload == body.payload && accepted.assetBindings == body.assetBindings)
                    require(task.httpStatus in setOf(200, 201))
                }
            }
            if (task.phase == CloudSchemeTaskPhase.Rejected) require(task.httpStatus?.let { it in 400..499 } == true)
        }
    }
    private suspend fun read(): Pair<DurableLocalRecord?, Record> {
        val stored = files.read(key) ?: return null to Record(scope = scope, tasks = emptyList())
        require(stored.payload.size <= 4 * 1024 * 1024)
        val raw = parseStrictJsonObject(stored.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "scope", "tasks"))
        val record = json.decodeFromJsonElement(Record.serializer(), raw)
        validate(record)
        return stored to record
    }
    private suspend fun write(before: DurableLocalRecord?, record: Record) {
        validate(record)
        val bytes = json.encodeToString(Record.serializer(), record).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size <= 4 * 1024 * 1024) { "Cloud scheme journal is full" }
        files.compareAndSet(key, before?.generation, bytes)
        files.confirmDurable(key, (before?.generation ?: 0L) + 1)
    }
    suspend fun tasks(): List<CloudSchemeTask> = mutex.withLock { read().second.tasks }
    suspend fun stage(mutation: CloudSchemeMutation): CloudSchemeTask = mutex.withLock {
        val (before, record) = read()
        record.tasks.singleOrNull { it.mutation.idempotencyKey == mutation.idempotencyKey }?.let {
            require(it.mutation == mutation) { "Cloud mutation identity cannot change" }
            files.confirmDurable(key, requireNotNull(before).generation)
            return@withLock it
        }
        require(record.tasks.none { it.mutation.resourceId == mutation.resourceId &&
            it.phase in setOf(CloudSchemeTaskPhase.Prepared, CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown) }) {
            "Resolve the existing resource mutation before staging another"
        }
        val task = CloudSchemeTask(mutation)
        write(before, record.copy(tasks = record.tasks + task)); task
    }
    suspend fun beginAttempt(expected: CloudSchemeTask, window: CloudSchemeRetryWindow? = null): CloudSchemeTask = transition(expected) {
        require(it.phase == CloudSchemeTaskPhase.Prepared)
        it.copy(phase = CloudSchemeTaskPhase.Sending, httpStatus = null, retryWindow = window)
    }
    /** Caller obtains fresh server-time evidence and explicit approval for this attempt. */
    suspend fun beginRecovery(expected: CloudSchemeTask, now: Long, ttlSeconds: Long): CloudSchemeTask = transition(expected) {
        require(it.phase in setOf(CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown))
        requireNotNull(it.retryWindow).requireOpen(now, ttlSeconds)
        it.copy(phase = CloudSchemeTaskPhase.Sending, httpStatus = null)
    }
    suspend fun recordUnknown(expected: CloudSchemeTask): CloudSchemeTask = transition(expected) {
        require(it.phase == CloudSchemeTaskPhase.Sending); it.copy(phase = CloudSchemeTaskPhase.Unknown)
    }
    suspend fun recordAccepted(expected: CloudSchemeTask, status: Int, resource: CloudSchemeResource?): CloudSchemeTask = transition(expected) {
        require(it.phase == CloudSchemeTaskPhase.Sending)
        it.copy(phase = CloudSchemeTaskPhase.Committed, accepted = resource, httpStatus = status)
    }
    suspend fun recordRejected(expected: CloudSchemeTask, status: Int): CloudSchemeTask = transition(expected) {
        require(it.phase == CloudSchemeTaskPhase.Sending && status in 400..499)
        it.copy(phase = CloudSchemeTaskPhase.Rejected, httpStatus = status)
    }
    private suspend fun transition(expected: CloudSchemeTask, change: (CloudSchemeTask) -> CloudSchemeTask): CloudSchemeTask = mutex.withLock {
        val (before, record) = read()
        val current = record.tasks.single { it.mutation.idempotencyKey == expected.mutation.idempotencyKey }
        val next = change(expected)
        require(next.mutation == expected.mutation)
        if (current == next) {
            files.confirmDurable(key, requireNotNull(before).generation)
            return@withLock current
        }
        require(current == expected) { "Cloud journal changed; reload before continuing" }
        write(before, record.copy(tasks = record.tasks.map { if (it == current) next else it })); next
    }
}
