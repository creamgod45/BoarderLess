@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless.data.persistence

@JsFun("(id, action, key, expected, next, done) => globalThis.boarderlessDurableRecords.operate(id, action, key, expected, next, done)")
internal actual external fun browserRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessDurableRecords.cancel(id)")
internal actual external fun browserCancelRecordOperation(id: String)

@JsFun("(id, action, key, expected, next, done) => globalThis.boarderlessQuickSchemeRecords.operate(id, action, key, expected, next, done)")
internal actual external fun browserLibraryRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessQuickSchemeRecords.cancel(id)")
internal actual external fun browserCancelLibraryRecordOperation(id: String)

@JsFun("(id, action, key, expected, next, done) => globalThis.boarderlessDocumentRecords.operate(id, action, key, expected, next, done)")
internal actual external fun browserDocumentRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessDocumentRecords.cancel(id)")
internal actual external fun browserCancelDocumentRecordOperation(id: String)

@JsFun("(id, action, key, expected, next, done) => globalThis.boarderlessAssetCatalogRecords.operate(id, action, key, expected, next, done)")
internal actual external fun browserAssetCatalogRecordOperation(id: String, action: String, key: String, expected: String?, next: String?, done: (String?, String?) -> Unit)
@JsFun("(id) => globalThis.boarderlessAssetCatalogRecords.cancel(id)")
internal actual external fun browserCancelAssetCatalogRecordOperation(id: String)
