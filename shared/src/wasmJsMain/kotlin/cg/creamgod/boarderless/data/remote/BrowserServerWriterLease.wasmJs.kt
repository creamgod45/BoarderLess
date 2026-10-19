@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless.data.remote

@JsFun("(id, done) => globalThis.boarderlessServerWriterLease.acquire(id, done)")
internal actual external fun browserAcquireServerWriterLease(id: String, done: (Boolean) -> Unit)
@JsFun("id => globalThis.boarderlessServerWriterLease.release(id)")
internal actual external fun browserReleaseServerWriterLease(id: String)
@JsFun("id => globalThis.boarderlessServerWriterLease.isHeld(id)")
internal actual external fun browserServerWriterLeaseHeld(id: String): Boolean
