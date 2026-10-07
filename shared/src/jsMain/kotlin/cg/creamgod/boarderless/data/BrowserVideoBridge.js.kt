package cg.creamgod.boarderless.data

internal actual fun browserVideoCreate(
    id: String,
    mime: String,
) {
    js("globalThis.boarderlessVideo").create(id, mime)
}

internal actual fun browserVideoAppend(
    id: String,
    bytes: String,
) {
    js("globalThis.boarderlessVideo").append(id, bytes)
}

internal actual fun browserVideoOpen(
    id: String,
    state: (String) -> Unit,
    done: (String?) -> Unit,
) {
    js("globalThis.boarderlessVideo").open(id, state, done)
}

internal actual fun browserVideoCommand(
    id: String,
    action: String,
    value: Double,
    done: (String?) -> Unit,
) {
    js("globalThis.boarderlessVideo").command(id, action, value, done)
}

internal actual fun browserVideoFrame(
    id: String,
    request: String,
    done: (String?, String?) -> Unit,
) {
    js("globalThis.boarderlessVideo").frame(id, request, done)
}

internal actual fun browserVideoCancelFrame(
    id: String,
    request: String,
) {
    js("globalThis.boarderlessVideo").cancelFrame(id, request)
}

internal actual fun browserVideoAttached(
    id: String,
    attached: Boolean,
) {
    js("globalThis.boarderlessVideo").attached(id, attached)
}

internal actual fun browserVideoRelease(id: String) {
    js("globalThis.boarderlessVideo").release(id)
}
