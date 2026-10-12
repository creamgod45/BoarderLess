package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore

/** Platform-owned awaited storage capability. Construction never reads records, migrates
 * content, opens a socket or substitutes Settings for durable checkpoint publication. */
class RealtimeCheckpointRuntime internal constructor(private val records: () -> AwaitedDurableLocalRecordStore) {
    internal fun checkpoints(scope: RealtimeCheckpointScope, capabilities: BackendCanvasReadCapabilities) =
        RealtimeCheckpointStore(records(), scope, capabilities)
}
