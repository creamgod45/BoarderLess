@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless.data.persistence

@JsFun("(requestId, action, id, token, offset, size, data, done) => globalThis.boarderlessOriginalStore.operate(requestId, action, id, token, offset, size, data, done)")
internal actual external fun browserOriginalOperation(requestId: String, action: String, id: String, token: String?, offset: String, size: String, data: String?, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessOriginalStore.cancel(id)")
internal actual external fun browserCancelOriginalOperation(requestId: String)
