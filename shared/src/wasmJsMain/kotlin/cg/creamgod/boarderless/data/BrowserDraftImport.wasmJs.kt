@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless.data

@JsFun("(id, canRead, done) => globalThis.boarderlessDraftImport.pick(id, canRead, done)")
internal actual external fun browserPickDraftJson(id: String, canRead: () -> Boolean, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessDraftImport.cancel(id)")
internal actual external fun browserCancelDraftSelection(id: String)
