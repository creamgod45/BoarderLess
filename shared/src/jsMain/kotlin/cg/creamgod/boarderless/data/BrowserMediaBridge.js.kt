package cg.creamgod.boarderless.data

internal actual fun browserMediaPick(requestId: String, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessMedia").pick(requestId, done)
}
internal actual fun browserMediaRead(fileId: String, offset: Double, maximum: Int, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessMedia").read(fileId, offset, maximum, done)
}
internal actual fun browserMediaRelease(fileId: String) {
    js("globalThis.boarderlessMedia").release(fileId)
}
internal actual fun browserMediaCancel(requestId: String) {
    js("globalThis.boarderlessMedia").cancel(requestId)
}
internal actual fun browserMediaUpload(fileId: String, url: String, headers: String, progress: (Double) -> Unit, done: (String?) -> Unit) {
    js("globalThis.boarderlessMedia").upload(fileId, url, headers, progress, done)
}
internal actual fun browserMediaAbortUpload(fileId: String) {
    js("globalThis.boarderlessMedia").abortUpload(fileId)
}
