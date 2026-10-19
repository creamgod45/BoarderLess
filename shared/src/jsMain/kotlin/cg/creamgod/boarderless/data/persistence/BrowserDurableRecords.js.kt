package cg.creamgod.boarderless.data.persistence

internal actual fun browserRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessDurableRecords").operate(id, action, key, expected, next, done)
}
internal actual fun browserCancelRecordOperation(id: String) {
    js("globalThis.boarderlessDurableRecords").cancel(id)
}

internal actual fun browserLibraryRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessQuickSchemeRecords").operate(id, action, key, expected, next, done)
}
internal actual fun browserCancelLibraryRecordOperation(id: String) { js("globalThis.boarderlessQuickSchemeRecords").cancel(id) }

internal actual fun browserDocumentRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessDocumentRecords").operate(id, action, key, expected, next, done)
}
internal actual fun browserCancelDocumentRecordOperation(id: String) { js("globalThis.boarderlessDocumentRecords").cancel(id) }

internal actual fun browserAssetCatalogRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessAssetCatalogRecords").operate(id, action, key, expected, next, done)
}
internal actual fun browserCancelAssetCatalogRecordOperation(id: String) { js("globalThis.boarderlessAssetCatalogRecords").cancel(id) }
