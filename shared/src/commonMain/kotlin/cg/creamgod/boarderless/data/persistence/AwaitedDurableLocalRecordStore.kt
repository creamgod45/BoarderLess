package cg.creamgod.boarderless.data.persistence

/** Acknowledgement is awaited: enqueueing a browser transaction is not durable publication. */
internal interface AwaitedDurableLocalRecordStore {
    suspend fun read(key: String): DurableLocalRecord?
    suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray)
    suspend fun confirmDurable(key: String, generation: Long)
}

internal fun AwaitedDurableLocalRecordStore.withWriterOperations(
    operations: cg.creamgod.boarderless.data.remote.ServerWriterOperationLease,
): AwaitedDurableLocalRecordStore = object : AwaitedDurableLocalRecordStore {
    override suspend fun read(key: String) = operations.runAwaited { this@withWriterOperations.read(key) }
    override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) =
        operations.runAwaited { this@withWriterOperations.compareAndSet(key, generation, payload) }
    override suspend fun confirmDurable(key: String, generation: Long) =
        operations.runAwaited { this@withWriterOperations.confirmDurable(key, generation) }
}

internal fun DurableLocalRecordStore.awaited(): AwaitedDurableLocalRecordStore = object : AwaitedDurableLocalRecordStore {
    override suspend fun read(key: String) = this@awaited.read(key)
    override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) = this@awaited.compareAndSet(key, generation, payload)
    override suspend fun confirmDurable(key: String, generation: Long) = this@awaited.confirmDurable(key, generation)
}

internal fun DurableLocalRecordStore.backgroundAwaited(): AwaitedDurableLocalRecordStore = object : AwaitedDurableLocalRecordStore {
    override suspend fun read(key: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { this@backgroundAwaited.read(key) }
    override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { this@backgroundAwaited.compareAndSet(key, generation, payload) }
    override suspend fun confirmDurable(key: String, generation: Long) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { this@backgroundAwaited.confirmDurable(key, generation) }
}
