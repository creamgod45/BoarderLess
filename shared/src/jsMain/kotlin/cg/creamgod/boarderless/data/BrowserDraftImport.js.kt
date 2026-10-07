package cg.creamgod.boarderless.data

internal actual fun browserPickDraftJson(
    id: String,
    canRead: () -> Boolean,
    done: (String?, String?) -> Unit,
) {
    js("globalThis.boarderlessDraftImport").pick(id, canRead, done)
}

internal actual fun browserCancelDraftSelection(id: String) {
    js("globalThis.boarderlessDraftImport").cancel(id)
}
