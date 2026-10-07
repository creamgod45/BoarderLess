@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless.data

@JsFun("(id, done) => globalThis.boarderlessMedia.pick(id, done)")
internal actual external fun browserMediaPick(requestId: String, done: (String?, String?) -> Unit)
@JsFun("(id, offset, max, done) => globalThis.boarderlessMedia.read(id, offset, max, done)")
internal actual external fun browserMediaRead(fileId: String, offset: Double, maximum: Int, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessMedia.release(id)")
internal actual external fun browserMediaRelease(fileId: String)
@JsFun("(id) => globalThis.boarderlessMedia.cancel(id)")
internal actual external fun browserMediaCancel(requestId: String)
@JsFun("(id, url, headers, progress, done) => globalThis.boarderlessMedia.upload(id, url, headers, progress, done)")
internal actual external fun browserMediaUpload(fileId: String, url: String, headers: String, progress: (Double) -> Unit, done: (String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessMedia.abortUpload(id)")
internal actual external fun browserMediaAbortUpload(fileId: String)
