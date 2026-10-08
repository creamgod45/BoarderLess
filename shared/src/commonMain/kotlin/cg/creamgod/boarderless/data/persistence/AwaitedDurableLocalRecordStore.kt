package cg.creamgod.boarderless.data.persistence

/** Acknowledgement is awaited: enqueueing a browser transaction is not durable publication. */
internal interface AwaitedDurableLocalRecordStore {
    suspend fun read(key: String): DurableLocalRecord?
    suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray)
    suspend fun confirmDurable(key: String, generation: Long)
}

internal fun DurableLocalRecordStore.awaited(): AwaitedDurableLocalRecordStore = object : AwaitedDurableLocalRecordStore {
    override suspend fun read(key: String) = this@awaited.read(key)
    override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) = this@awaited.compareAndSet(key, generation, payload)
    override suspend fun confirmDurable(key: String, generation: Long) = this@awaited.confirmDurable(key, generation)
}
