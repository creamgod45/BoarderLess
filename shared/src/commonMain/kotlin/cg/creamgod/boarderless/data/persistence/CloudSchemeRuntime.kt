package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.remote.BackendQuickSchemeGateway

/** Platform-owned durable storage only; accessing this does not connect or migrate data. */
class CloudSchemeRuntime internal constructor(private val records: () -> AwaitedDurableLocalRecordStore) {
    internal fun outbox(scope: CloudSchemeOutboxScope): CloudSchemeOutbox = CloudSchemeOutbox(records(), scope)
}

/** Fixed deployment from the selected repository. No default-server substitution. */
class CloudSchemeServer internal constructor(private val baseUrl: String) {
    internal fun gateway(userId: String): BackendQuickSchemeGateway = BackendQuickSchemeGateway(baseUrl, userId)
}
