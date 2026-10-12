package cg.creamgod.boarderless.data.persistence

internal actual fun browserOriginalOperation(requestId: String, action: String, id: String, token: String?, offset: String, size: String, data: String?, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessOriginalStore").operate(requestId, action, id, token, offset, size, data, done)
}
internal actual fun browserCancelOriginalOperation(requestId: String) { js("globalThis.boarderlessOriginalStore").cancel(requestId) }
