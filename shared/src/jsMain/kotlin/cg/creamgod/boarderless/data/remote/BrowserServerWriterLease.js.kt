package cg.creamgod.boarderless.data.remote

internal actual fun browserAcquireServerWriterLease(id: String, done: (Boolean) -> Unit) {
    js("globalThis.boarderlessServerWriterLease").acquire(id, done)
}
internal actual fun browserReleaseServerWriterLease(id: String) { js("globalThis.boarderlessServerWriterLease").release(id) }
internal actual fun browserServerWriterLeaseHeld(id: String): Boolean = js("globalThis.boarderlessServerWriterLease").isHeld(id) as Boolean
