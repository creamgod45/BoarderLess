package cg.creamgod.boarderless.data

internal actual fun browserBundleOperation(
    id: String, action: String, token: String, argument: String, offset: Double, maximumBytes: Int,
    guard: () -> Boolean, done: (String?, String?) -> Unit,
) { js("globalThis.boarderlessSchemeBundle").operation(id, action, token, argument, offset, maximumBytes, guard, done) }
internal actual fun browserCancelBundleOperation(id: String) { js("globalThis.boarderlessSchemeBundle").cancel(id) }
internal actual fun browserReleaseBundle(token: String) { js("globalThis.boarderlessSchemeBundle").release(token) }
