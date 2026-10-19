@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless.data

@JsFun("(id, action, token, argument, offset, maximumBytes, guard, done) => globalThis.boarderlessSchemeBundle.operation(id, action, token, argument, offset, maximumBytes, guard, done)")
internal actual external fun browserBundleOperation(
    id: String, action: String, token: String, argument: String, offset: Double, maximumBytes: Int,
    guard: () -> Boolean, done: (String?, String?) -> Unit,
)
@JsFun("(id) => globalThis.boarderlessSchemeBundle.cancel(id)")
internal actual external fun browserCancelBundleOperation(id: String)
@JsFun("(token) => globalThis.boarderlessSchemeBundle.release(token)")
internal actual external fun browserReleaseBundle(token: String)
